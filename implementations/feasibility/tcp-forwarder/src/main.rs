//! Serial, bounded TCP feasibility probe. Not an HTTP firewall or a benchmark.
use std::env;
use std::io::{self, Read, Write};
use std::net::{SocketAddr, TcpListener, TcpStream};
use std::process::ExitCode;
use std::time::{Duration, Instant};

struct Config {
    listen: SocketAddr,
    backend: SocketAddr,
    timeout: Duration,
    accept_timeout: Duration,
    max_bytes: usize,
}

fn invalid(message: &str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, message)
}

fn config() -> io::Result<Config> {
    let args: Vec<String> = env::args().skip(1).collect();
    if args.len() != 5 {
        return Err(invalid("usage: probe LISTEN_IP:PORT BACKEND_IP:PORT IO_TIMEOUT_MS ACCEPT_TIMEOUT_MS MAX_BYTES"));
    }
    let address = |s: &str| s.parse::<SocketAddr>().map_err(|_| invalid("expected numeric IP:port"));
    let number = |s: &str| s.parse::<u64>().ok().filter(|n| *n > 0).ok_or_else(|| invalid("expected positive integer"));
    let max_bytes = number(&args[4])?;
    if max_bytes > 1_048_576 {
        return Err(invalid("MAX_BYTES exceeds probe limit of 1 MiB"));
    }
    Ok(Config {
        listen: address(&args[0])?,
        backend: address(&args[1])?,
        timeout: Duration::from_millis(number(&args[2])?),
        accept_timeout: Duration::from_millis(number(&args[3])?),
        max_bytes: max_bytes as usize,
    })
}

fn remaining(deadline: Instant) -> io::Result<Duration> {
    deadline.checked_duration_since(Instant::now())
        .filter(|d| !d.is_zero())
        .ok_or_else(|| io::Error::new(io::ErrorKind::TimedOut, "exchange deadline exceeded"))
}

fn read_exact(stream: &mut TcpStream, mut buffer: &mut [u8], deadline: Instant) -> io::Result<()> {
    while !buffer.is_empty() {
        stream.set_read_timeout(Some(remaining(deadline)?))?;
        match stream.read(buffer) {
            Ok(0) => return Err(io::Error::new(io::ErrorKind::UnexpectedEof, "incomplete frame")),
            Ok(n) => buffer = &mut buffer[n..],
            Err(e) if e.kind() == io::ErrorKind::Interrupted => continue,
            Err(e) => return Err(e),
        }
    }
    Ok(())
}

fn read_frame(stream: &mut TcpStream, limit: usize, deadline: Instant) -> io::Result<Vec<u8>> {
    let mut header = [0; 4];
    read_exact(stream, &mut header, deadline)?;
    let length = u32::from_be_bytes(header) as usize;
    if length > limit {
        return Err(invalid("frame exceeds MAX_BYTES"));
    }
    let mut payload = vec![0; length];
    read_exact(stream, &mut payload, deadline)?;
    Ok(payload)
}

fn write_frame(stream: &mut TcpStream, payload: &[u8], deadline: Instant) -> io::Result<()> {
    let mut frame = Vec::with_capacity(4 + payload.len());
    frame.extend_from_slice(&(payload.len() as u32).to_be_bytes());
    frame.extend_from_slice(payload);
    let mut buffer = frame.as_slice();
    while !buffer.is_empty() {
        stream.set_write_timeout(Some(remaining(deadline)?))?;
        match stream.write(buffer) {
            Ok(0) => return Err(io::Error::new(io::ErrorKind::WriteZero, "closed while writing")),
            Ok(n) => buffer = &buffer[n..],
            Err(e) if e.kind() == io::ErrorKind::Interrupted => continue,
            Err(e) => return Err(e),
        }
    }
    Ok(())
}

fn phase<T>(name: &str, result: io::Result<T>) -> io::Result<T> {
    result.inspect_err(|e| {
        let kind = match e.kind() {
            io::ErrorKind::TimedOut | io::ErrorKind::WouldBlock => "timeout",
            io::ErrorKind::UnexpectedEof => "incomplete_frame",
            io::ErrorKind::InvalidInput => "invalid_input",
            io::ErrorKind::ConnectionRefused => "connection_refused",
            _ => "io_error",
        };
        eprintln!("event=error phase={name} kind={kind} detail={e}");
    })
}

fn run(config: Config) -> io::Result<()> {
    let listener = phase("bind", TcpListener::bind(config.listen))?;
    phase("configure_listener", listener.set_nonblocking(true))?;
    eprintln!("event=listening");
    let accept_deadline = Instant::now() + config.accept_timeout;
    let mut client = loop {
        match listener.accept() {
            Ok((client, _)) => break client,
            Err(e) if e.kind() == io::ErrorKind::WouldBlock => {
                phase("accept", remaining(accept_deadline))?;
                std::thread::sleep(Duration::from_millis(5));
            }
            Err(e) if e.kind() == io::ErrorKind::Interrupted => continue,
            Err(e) => return phase("accept", Err(e)),
        }
    };
    let deadline = Instant::now() + config.timeout;
    let request = phase("read_request", read_frame(&mut client, config.max_bytes, deadline))?;
    let timeout = phase("connect_backend", remaining(deadline))?;
    let mut backend = phase("connect_backend", TcpStream::connect_timeout(&config.backend, timeout))?;
    // Read the pending socket error even if connect_timeout returned a stream.
    // This check is identical on native and WASIp2; it does not remap raw errno.
    if let Some(error) = phase("connect_backend", backend.take_error())? {
        return phase("connect_backend", Err(error));
    }
    phase("connect_backend", backend.peer_addr())?;
    phase("write_backend", write_frame(&mut backend, &request, deadline))?;
    let response = phase("read_backend", read_frame(&mut backend, config.max_bytes, deadline))?;
    phase("write_response", write_frame(&mut client, &response, deadline))?;
    // Drop closes both sockets and the listener, then the process exits normally.
    eprintln!("event=forwarded request_bytes={} response_bytes={}", request.len(), response.len());
    Ok(())
}

fn main() -> ExitCode {
    match config().and_then(run) {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("event=exit status=error detail={error}");
            ExitCode::FAILURE
        }
    }
}
