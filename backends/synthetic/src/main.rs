use std::{
    env, io,
    net::{IpAddr, Ipv4Addr, SocketAddr},
};

use axum::{
    Router,
    body::Bytes,
    extract::State,
    http::{HeaderValue, StatusCode, Uri, header::CONTENT_TYPE},
    response::{IntoResponse, Response},
    routing::get,
};
use tokio::{
    net::TcpListener,
    signal::unix::{SignalKind, signal},
};

const DEFAULT_SIZE: usize = 1024;
const DEFAULT_MAX: usize = 65_536;
const HARD_MAX: usize = 16 * 1024 * 1024;
const USAGE: &str =
    "Usage: mt-synthetic-backend [--bind IP] [--port PORT] [--max-size BYTES] [--instance-id ID]";

struct Config {
    address: SocketAddr,
    max_size: usize,
    instance_id: Option<HeaderValue>,
}

impl Config {
    fn parse(mut args: impl Iterator<Item = String>) -> io::Result<Self> {
        let mut bind = IpAddr::V4(Ipv4Addr::LOCALHOST);
        let mut port = 8080;
        let mut max_size = DEFAULT_MAX;
        let mut instance_id = None;
        while let Some(option) = args.next() {
            let value = args
                .next()
                .ok_or_else(|| invalid(format!("missing value for {option}")))?;
            match option.as_str() {
                "--bind" => {
                    bind = value
                        .parse()
                        .map_err(|_| invalid("expected a numeric IP"))?
                }
                "--port" => {
                    port = value
                        .parse()
                        .map_err(|_| invalid("expected a port from 0 to 65535"))?
                }
                "--max-size" => {
                    max_size = parse_size(&value)
                        .filter(|size| (1..=HARD_MAX).contains(size))
                        .ok_or_else(|| {
                            invalid(format!("expected --max-size from 1 to {HARD_MAX}"))
                        })?;
                }
                "--instance-id" => {
                    if value.is_empty()
                        || value.len() > 64
                        || !value
                            .bytes()
                            .all(|byte| byte.is_ascii_alphanumeric() || b"._-".contains(&byte))
                    {
                        return Err(invalid(
                            "expected a 1–64 character instance ID using A–Z, a–z, 0–9, dot, underscore or hyphen",
                        ));
                    }
                    instance_id = Some(HeaderValue::from_str(&value).map_err(io::Error::other)?);
                }
                _ => return Err(invalid(format!("unknown option {option}"))),
            }
        }
        Ok(Self {
            address: SocketAddr::new(bind, port),
            max_size,
            instance_id,
        })
    }
}

fn invalid(message: impl Into<String>) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, message.into())
}

#[derive(Clone)]
struct Backend {
    payload: Bytes,
    instance_id: Option<HeaderValue>,
}

impl Backend {
    fn respond(
        &self,
        status: StatusCode,
        content_type: &'static str,
        body: impl IntoResponse,
    ) -> Response {
        let mut response = (status, [(CONTENT_TYPE, content_type)], body).into_response();
        if let Some(id) = &self.instance_id {
            response
                .headers_mut()
                .insert("x-backend-instance", id.clone());
        }
        response
    }
}

async fn health(State(backend): State<Backend>) -> Response {
    backend.respond(StatusCode::OK, "text/plain; charset=utf-8", "ok\n")
}

async fn ready(State(backend): State<Backend>) -> Response {
    backend.respond(StatusCode::OK, "text/plain; charset=utf-8", "backend-ok\n")
}

fn parse_size(value: &str) -> Option<usize> {
    if !value.bytes().all(|byte| byte.is_ascii_digit()) {
        return None;
    }
    value.parse().ok()
}

async fn echo(State(backend): State<Backend>, uri: Uri) -> Response {
    let max = backend.payload.len();
    let size = match uri.query() {
        None | Some("") => Some(DEFAULT_SIZE.min(max)),
        Some(query) => query.strip_prefix("size=").and_then(parse_size),
    };
    match size.filter(|size| *size <= max) {
        Some(size) => backend.respond(
            StatusCode::OK,
            "application/octet-stream",
            backend.payload.slice(..size),
        ),
        None => backend.respond(
            StatusCode::BAD_REQUEST,
            "text/plain; charset=utf-8",
            "invalid size\n",
        ),
    }
}

#[tokio::main]
async fn main() -> io::Result<()> {
    let mut args = env::args().skip(1).peekable();
    if matches!(args.peek().map(String::as_str), Some("-h" | "--help")) {
        println!("{USAGE}");
        return Ok(());
    }

    let config = Config::parse(args)?;
    let mut interrupt = signal(SignalKind::interrupt())?;
    let mut terminate = signal(SignalKind::terminate())?;
    let backend = Backend {
        payload: Bytes::from(
            (0..config.max_size)
                .map(|index| index as u8)
                .collect::<Vec<_>>(),
        ),
        instance_id: config.instance_id,
    };
    let app = Router::new()
        .route("/health", get(health))
        .route("/ready", get(ready))
        .route("/echo", get(echo))
        .with_state(backend);
    let listener = TcpListener::bind(config.address).await?;
    println!(
        "listening on {} max-size={}",
        listener.local_addr()?,
        config.max_size
    );

    axum::serve(listener, app)
        .with_graceful_shutdown(async move {
            tokio::select! {
                _ = interrupt.recv() => {}
                _ = terminate.recv() => {}
            }
        })
        .await
}
