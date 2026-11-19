use std::{
    io::{BufRead, BufReader, Read, Write},
    net::{SocketAddr, TcpStream},
    process::{Child, Command, Stdio},
    sync::{Arc, Barrier, mpsc},
    thread,
    time::{Duration, Instant},
};

const TIMEOUT: Duration = Duration::from_secs(5);

struct Server {
    child: Child,
    address: SocketAddr,
}

impl Server {
    fn start(max: usize, id: &str) -> Self {
        let mut server = Self {
            child: Command::new(env!("CARGO_BIN_EXE_mt-synthetic-backend"))
                .args([
                    "--port",
                    "0",
                    "--max-size",
                    &max.to_string(),
                    "--instance-id",
                    id,
                ])
                .stdout(Stdio::piped())
                .spawn()
                .unwrap(),
            address: "127.0.0.1:0".parse().unwrap(),
        };
        let stdout = server.child.stdout.take().unwrap();
        let (send, receive) = mpsc::channel();
        thread::spawn(move || {
            let mut line = String::new();
            BufReader::new(stdout).read_line(&mut line).unwrap();
            let _ = send.send(line);
        });
        let startup = receive.recv_timeout(TIMEOUT).unwrap();
        server.address = startup
            .strip_prefix("listening on ")
            .unwrap()
            .split_ascii_whitespace()
            .next()
            .unwrap()
            .parse()
            .unwrap();
        assert!(server.address.ip().is_loopback());
        server
    }

    fn stop(&mut self, signal: &str) {
        assert!(
            Command::new("/bin/kill")
                .args([signal, &self.child.id().to_string()])
                .status()
                .unwrap()
                .success()
        );
        let deadline = Instant::now() + TIMEOUT;
        loop {
            if let Some(status) = self.child.try_wait().unwrap() {
                assert!(status.success(), "{status}");
                return;
            }
            assert!(Instant::now() < deadline, "server failed to shut down");
            thread::sleep(Duration::from_millis(10));
        }
    }
}

impl Drop for Server {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

fn get(address: SocketAddr, target: &str, status: u16, id: &str) -> Vec<u8> {
    let mut stream = TcpStream::connect_timeout(&address, TIMEOUT).unwrap();
    stream.set_read_timeout(Some(TIMEOUT)).unwrap();
    stream.set_write_timeout(Some(TIMEOUT)).unwrap();
    write!(
        stream,
        "GET {target} HTTP/1.1\r\nHost: {address}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
    )
    .unwrap();
    let mut wire = Vec::new();
    stream.read_to_end(&mut wire).unwrap();
    let end = wire
        .windows(4)
        .position(|part| part == b"\r\n\r\n")
        .unwrap();
    let (head, body) = (std::str::from_utf8(&wire[..end]).unwrap(), &wire[end + 4..]);
    let mut lines = head.split("\r\n");
    assert!(
        lines
            .next()
            .unwrap()
            .starts_with(&format!("HTTP/1.1 {status} ")),
        "{head}"
    );
    let headers: Vec<_> = lines.map(|line| line.split_once(':').unwrap()).collect();
    let values = |name: &str| {
        headers
            .iter()
            .filter(|(key, _)| key.eq_ignore_ascii_case(name))
            .map(|(_, value)| value.trim())
            .collect::<Vec<_>>()
    };
    assert_eq!(values("content-length"), [body.len().to_string()]);
    assert!(values("transfer-encoding").is_empty());
    assert_eq!(values("x-backend-instance"), [id]);
    if target.starts_with("/echo") && status == 200 {
        assert_eq!(values("content-type"), ["application/octet-stream"]);
    }
    body.to_vec()
}

#[test]
fn health_and_firewall_readiness() {
    let mut server = Server::start(16_384, "probe");
    assert_eq!(get(server.address, "/health", 200, "probe"), b"ok\n");
    assert_eq!(get(server.address, "/ready", 200, "probe"), b"backend-ok\n");
    server.stop("-TERM");
}

#[test]
fn echo_sizes_and_binary_pattern_are_exact() {
    let server = Server::start(16_384, "echo");
    for size in [0, 128, 1024, 4096, 16_384] {
        let target = format!("/echo?size={size}");
        let first = get(server.address, &target, 200, "echo");
        assert_eq!(
            first,
            (0..size)
                .map(|index| (index % 256) as u8)
                .collect::<Vec<_>>()
        );
        assert_eq!(first, get(server.address, &target, 200, "echo"));
    }
    assert_eq!(get(server.address, "/echo", 200, "echo").len(), 1024);
}

#[test]
fn invalid_queries_and_maximum() {
    let server = Server::start(128, "limited");
    assert_eq!(get(server.address, "/echo", 200, "limited").len(), 128);
    assert_eq!(
        get(server.address, "/echo?size=128", 200, "limited").len(),
        128
    );
    for query in [
        "size=129",
        "size=",
        "size=-1",
        "size=+1",
        "size=%31",
        "size=1.0",
        "size=1&size=2",
        "size=1&other=2",
        "other=1",
        "size=999999999999999999999999999",
    ] {
        assert_eq!(
            get(server.address, &format!("/echo?{query}"), 400, "limited"),
            b"invalid size\n",
            "{query}"
        );
    }
}

#[test]
fn simultaneous_instances_and_concurrent_requests() {
    let mut first = Server::start(4096, "first");
    let mut second = Server::start(8192, "second");
    assert_ne!(first.address, second.address);
    let starting = Arc::new(Barrier::new(16));
    let clients: Vec<_> = (0..16)
        .map(|index| {
            let (address, id) = if index % 2 == 0 {
                (first.address, "first")
            } else {
                (second.address, "second")
            };
            let starting = Arc::clone(&starting);
            thread::spawn(move || {
                starting.wait();
                get(address, "/echo?size=4096", 200, id)
            })
        })
        .collect();

    let expected: Vec<_> = (0..4096).map(|index| (index % 256) as u8).collect();
    for client in clients {
        assert_eq!(client.join().unwrap(), expected);
    }
    first.stop("-INT");
    second.stop("-TERM");

    let restarted = Server::start(4096, "first");
    assert_eq!(
        get(restarted.address, "/echo?size=4096", 200, "first"),
        expected
    );
}
