//! First functional firewall: shared blocking sockets, bounded sequential requests.
mod http;
use std::{env, io::{self, Read, Write}, net::{SocketAddr, TcpListener, TcpStream},
          process::ExitCode, time::{Duration, Instant}};

struct Config {
    listen: SocketAddr, backend: SocketAddr, timeout: Duration, accept_timeout: Duration,
    max_head: usize, max_headers: usize, max_body: usize, max_connections: usize, rules: Vec<http::Rule>,
}

fn invalid(message: &str) -> io::Error { io::Error::new(io::ErrorKind::InvalidInput, message) }
fn config() -> io::Result<Config> {
    let args: Vec<String> = env::args().skip(1).collect();
    if args.len() < 8 || args.len() > 1032 { return Err(invalid("usage: firewall LISTEN BACKEND IO_MS ACCEPT_MS MAX_HEAD MAX_HEADERS MAX_BODY MAX_CONNECTIONS [allow|deny:METHOD|*:PATH|* ...]")); }
    let address = |s: &str| s.parse::<SocketAddr>().map_err(|_| invalid("expected numeric IP:port"));
    let number = |s: &str, max| s.parse::<usize>().ok().filter(|n| *n > 0 && *n <= max).ok_or_else(|| invalid("invalid numeric limit"));
    Ok(Config {
        listen: address(&args[0])?, backend: address(&args[1])?,
        timeout: Duration::from_millis(number(&args[2], 60000)? as u64),
        accept_timeout: Duration::from_millis(number(&args[3], 60000)? as u64),
        max_head: number(&args[4], 65536)?, max_headers: number(&args[5], 256)?,
        max_body: number(&args[6], 1048576)?, max_connections: args[7].parse().map_err(|_| invalid("invalid connection count"))?,
        rules: args[8..].iter().map(|rule| http::Rule::parse(rule).map_err(|_| invalid("invalid policy rule"))).collect::<io::Result<_>>()?,
    })
}

fn remaining(deadline: Instant) -> io::Result<Duration> {
    deadline.checked_duration_since(Instant::now()).filter(|d| !d.is_zero())
        .ok_or_else(|| io::Error::new(io::ErrorKind::TimedOut, "exchange deadline exceeded"))
}

fn read_head(stream: &mut TcpStream, limit: usize, deadline: Instant) -> io::Result<(Vec<u8>, Vec<u8>)> {
    let mut data = Vec::new();
    loop {
        if let Some(end) = data.windows(4).position(|part| part == b"\r\n\r\n").map(|i| i + 4) {
            let tail = data.split_off(end); return Ok((data, tail));
        }
        if data.len() >= limit { return Err(invalid("HTTP head limit exceeded")); }
        stream.set_read_timeout(Some(remaining(deadline)?))?;
        let mut buffer = [0u8; 2048];
        let amount = (limit - data.len()).min(buffer.len());
        match stream.read(&mut buffer[..amount]) {
            Ok(0) => return Err(io::Error::new(io::ErrorKind::UnexpectedEof, "incomplete HTTP head")),
            Ok(n) => data.extend_from_slice(&buffer[..n]),
            Err(e) if e.kind() == io::ErrorKind::Interrupted => (),
            Err(e) => return Err(e),
        }
    }
}

fn write_all(stream: &mut TcpStream, mut bytes: &[u8], deadline: Instant) -> io::Result<()> {
    while !bytes.is_empty() {
        stream.set_write_timeout(Some(remaining(deadline)?))?;
        match stream.write(bytes) {
            Ok(0) => return Err(io::Error::new(io::ErrorKind::WriteZero, "connection closed while writing")),
            Ok(n) => bytes = &bytes[n..],
            Err(e) if e.kind() == io::ErrorKind::Interrupted => (), Err(e) => return Err(e),
        }
    }
    Ok(())
}

fn reply(client: &mut TcpStream, code: u16, reason: &str, config: &Config) -> io::Result<()> {
    let body = format!("{reason}\n");
    let response = format!("HTTP/1.1 {code} {reason}\r\nContent-Type: text/plain\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}", body.len());
    // Bounded separate budget permits an error reply after the backend deadline expires.
    write_all(client, response.as_bytes(), Instant::now() + config.timeout.min(Duration::from_millis(250)))
}

fn forward(request: &http::Request, config: &Config, deadline: Instant) -> io::Result<Vec<u8>> {
    let mut backend = TcpStream::connect_timeout(&config.backend, remaining(deadline)?)?;
    if let Some(error) = backend.take_error()? { return Err(error); }
    backend.peer_addr()?;
    write_all(&mut backend, &http::upstream(request, config.backend), deadline)?;
    let (head, mut body) = read_head(&mut backend, config.max_head, deadline)?;
    let response = http::response(&head, config.max_headers, config.max_body).map_err(|_| invalid("unsupported backend response"))?;
    body.truncate(response.length);
    while body.len() < response.length {
        backend.set_read_timeout(Some(remaining(deadline)?))?;
        let mut buffer = [0u8; 4096];
        let amount = (response.length - body.len()).min(buffer.len());
        match backend.read(&mut buffer[..amount]) {
            Ok(0) => return Err(io::Error::new(io::ErrorKind::UnexpectedEof, "incomplete backend response")),
            Ok(n) => body.extend_from_slice(&buffer[..n]),
            Err(e) if e.kind() == io::ErrorKind::Interrupted => (), Err(e) => return Err(e),
        }
    }
    Ok(http::downstream(&response, &body))
}

fn handle(mut client: TcpStream, config: &Config) -> io::Result<()> {
    let deadline = Instant::now() + config.timeout;
    let head = match read_head(&mut client, config.max_head, deadline) {
        Ok((head, _)) => head, // One request per connection: trailing bytes are never forwarded.
        Err(e) if matches!(e.kind(), io::ErrorKind::TimedOut | io::ErrorKind::WouldBlock) => return Err(e),
        Err(e) if e.kind() == io::ErrorKind::InvalidInput => return reply(&mut client, 431, "Request Header Fields Too Large", config),
        Err(_) => return reply(&mut client, 400, "Bad Request", config),
    };
    let request = match http::request(&head, config.max_headers) {
        Ok(request) => request,
        Err(http::Error::TooLarge) => return reply(&mut client, 431, "Request Header Fields Too Large", config),
        Err(_) => return reply(&mut client, 400, "Bad Request", config),
    };
    if !http::allowed(&request, &config.rules) { return reply(&mut client, 403, "Forbidden", config); }
    match forward(&request, config, deadline) {
        Ok(response) => write_all(&mut client, &response, deadline),
        Err(error) => { eprintln!("event=backend_error kind={:?}", error.kind()); reply(&mut client, 502, "Bad Gateway", config) },
    }
}

fn run(config: Config) -> io::Result<()> {
    let listener = TcpListener::bind(config.listen)?;
    listener.set_nonblocking(true)?;
    eprintln!("event=listening model=sequential");
    let mut count = 0;
    loop {
        let deadline = Instant::now() + config.accept_timeout;
        let client = loop {
            match listener.accept() {
                Ok((client, _)) => break client,
                Err(e) if e.kind() == io::ErrorKind::WouldBlock => { remaining(deadline)?; std::thread::sleep(Duration::from_millis(5)); },
                Err(e) if e.kind() == io::ErrorKind::Interrupted => (), Err(e) => return Err(e),
            }
        };
        if let Err(error) = handle(client, &config) { eprintln!("event=client_error kind={:?}", error.kind()); }
        count += 1;
        if config.max_connections != 0 && count >= config.max_connections { return Ok(()); }
    }
}

fn main() -> ExitCode {
    match config().and_then(run) {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => { eprintln!("event=exit status=error detail={error}"); ExitCode::FAILURE },
    }
}
