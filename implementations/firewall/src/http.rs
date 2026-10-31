//! Restricted HTTP/1.1 contract, shared without target-specific branches.
use std::net::{IpAddr, SocketAddr};

pub type Headers = Vec<(String, Vec<u8>)>;

#[derive(Debug, PartialEq)]
pub enum Error { Invalid, TooLarge }

#[derive(Debug)]
pub struct Rule { allow: bool, method: String, path: String }

impl Rule {
    pub fn parse(input: &str) -> Result<Self, Error> {
        let parts: Vec<_> = input.splitn(3, ':').collect();
        if parts.len() != 3 || !matches!(parts[0], "allow" | "deny")
            || !(parts[1] == "*" || token(parts[1]))
            || !(parts[2] == "*" || (target(parts[2]) && !parts[2].contains('?'))) {
            return Err(Error::Invalid);
        }
        Ok(Self { allow: parts[0] == "allow", method: parts[1].into(), path: parts[2].into() })
    }
}

pub struct Request { pub method: String, pub target: String, headers: Headers, remove: Vec<String> }
pub struct Response { pub length: usize, headers: Headers, remove: Vec<String> }

fn token(value: &str) -> bool {
    !value.is_empty() && value.bytes().all(|b| b.is_ascii_alphanumeric() || b"!#$%&'*+-.^_`|~".contains(&b))
}

fn target(value: &str) -> bool {
    value.starts_with('/') && value.is_ascii() && !value.contains('#')
        && value.bytes().all(|b| b > 32 && b < 127)
}

fn strict_lines(head: &[u8]) -> bool {
    head.ends_with(b"\r\n\r\n") && head.iter().enumerate().all(|(i, b)| match b {
        b'\r' => head.get(i + 1) == Some(&b'\n'),
        b'\n' => i > 0 && head[i - 1] == b'\r',
        _ => true,
    })
}

fn values<'a>(headers: &'a Headers, name: &'a str) -> impl Iterator<Item = &'a [u8]> {
    headers.iter().filter(move |(key, _)| key.eq_ignore_ascii_case(name)).map(|(_, value)| value.as_slice())
}

fn text(value: &[u8]) -> Result<&str, Error> {
    if !value.iter().all(|b| *b == b'\t' || (32..127).contains(b)) { return Err(Error::Invalid); }
    Ok(std::str::from_utf8(value).map_err(|_| Error::Invalid)?.trim_matches([' ', '\t']))
}

fn headers(parsed: &[httparse::Header<'_>]) -> Result<Headers, Error> {
    parsed.iter().map(|header| {
        text(header.value)?;
        Ok((header.name.to_owned(), header.value.to_vec()))
    }).collect()
}

fn connection_tokens(headers: &Headers) -> Result<Vec<String>, Error> {
    let mut result = Vec::new();
    for value in values(headers, "connection") {
        for name in text(value)?.split(',').map(str::trim) {
            if !token(name) || ["host", "content-length", "transfer-encoding"].iter().any(|key| name.eq_ignore_ascii_case(key)) {
                return Err(Error::Invalid);
            }
            result.push(name.to_ascii_lowercase());
        }
    }
    Ok(result)
}

fn content_length(headers: &Headers) -> Result<Option<usize>, Error> {
    let mut found = values(headers, "content-length");
    let Some(value) = found.next() else { return Ok(None); };
    if found.next().is_some() { return Err(Error::Invalid); }
    let value = text(value)?;
    if value.is_empty() || !value.bytes().all(|b| b.is_ascii_digit()) { return Err(Error::Invalid); }
    value.parse().map(Some).map_err(|_| Error::Invalid)
}

fn authority(value: &str) -> bool {
    if value.is_empty() || !value.is_ascii() { return false; }
    if value.starts_with('[') {
        let Some(end) = value.find(']') else { return false; };
        return value[1..end].parse::<IpAddr>().is_ok_and(|ip| ip.is_ipv6())
            && port(&value[end + 1..]);
    }
    let (host, suffix) = match value.find(':') { Some(i) => (&value[..i], &value[i..]), None => (value, "") };
    let host = host.strip_suffix('.').unwrap_or(host);
    !host.is_empty() && host.len() <= 253 && host.split('.').all(|label| {
        !label.is_empty() && label.len() <= 63 && label.as_bytes()[0].is_ascii_alphanumeric()
            && label.as_bytes()[label.len() - 1].is_ascii_alphanumeric()
            && label.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-')
    }) && port(suffix)
}

fn port(suffix: &str) -> bool {
    suffix.is_empty() || suffix.strip_prefix(':').is_some_and(|value| {
        !value.is_empty() && value.bytes().all(|b| b.is_ascii_digit())
            && value.parse::<u16>().is_ok_and(|p| p != 0)
    })
}

fn unsupported(headers: &Headers) -> bool {
    ["transfer-encoding", "upgrade", "expect", "trailer"].iter().any(|key| values(headers, key).next().is_some())
}

pub fn request(head: &[u8], max_headers: usize) -> Result<Request, Error> {
    if !strict_lines(head) { return Err(Error::Invalid); }
    let mut slots = vec![httparse::EMPTY_HEADER; max_headers];
    let mut parsed = httparse::Request::new(&mut slots);
    match parsed.parse(head) {
        Ok(httparse::Status::Complete(n)) if n == head.len() => (),
        Err(httparse::Error::TooManyHeaders) => return Err(Error::TooLarge),
        _ => return Err(Error::Invalid),
    }
    let method = parsed.method.ok_or(Error::Invalid)?;
    let path = parsed.path.ok_or(Error::Invalid)?;
    if parsed.version != Some(1) || !target(path) || matches!(method, "CONNECT" | "HEAD")
        || !head.starts_with(format!("{method} {path} HTTP/1.1\r\n").as_bytes()) { return Err(Error::Invalid); }
    let headers = headers(parsed.headers)?;
    let mut hosts = values(&headers, "host");
    if !hosts.next().is_some_and(|host| text(host).is_ok_and(authority)) || hosts.next().is_some() {
        return Err(Error::Invalid);
    }
    drop(hosts);
    if unsupported(&headers) || content_length(&headers)?.unwrap_or(0) != 0 { return Err(Error::Invalid); }
    let remove = connection_tokens(&headers)?;
    Ok(Request { method: method.into(), target: path.into(), headers, remove })
}

pub fn allowed(request: &Request, rules: &[Rule]) -> bool {
    let path = request.target.split('?').next().unwrap();
    rules.iter().find(|rule| (rule.method == "*" || rule.method == request.method)
        && (rule.path == "*" || rule.path == path)).is_some_and(|rule| rule.allow)
}

fn retained(name: &str, remove: &[String]) -> bool {
    !["host", "content-length", "connection", "keep-alive", "proxy-connection", "proxy-authenticate",
      "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade"].iter().any(|key| name.eq_ignore_ascii_case(key))
        && !remove.iter().any(|key| name.eq_ignore_ascii_case(key))
}

fn append_headers(output: &mut Vec<u8>, headers: &Headers, remove: &[String]) {
    for (name, value) in headers.iter().filter(|(name, _)| retained(name, remove)) {
        output.extend_from_slice(name.as_bytes()); output.extend_from_slice(b": ");
        output.extend_from_slice(value); output.extend_from_slice(b"\r\n");
    }
}

pub fn upstream(request: &Request, backend: SocketAddr) -> Vec<u8> {
    let mut output = format!("{} {} HTTP/1.1\r\nHost: {backend}\r\n", request.method, request.target).into_bytes();
    append_headers(&mut output, &request.headers, &request.remove);
    output.extend_from_slice(b"Content-Length: 0\r\nConnection: close\r\n\r\n"); output
}

pub fn response(head: &[u8], max_headers: usize, max_body: usize) -> Result<Response, Error> {
    if !strict_lines(head) { return Err(Error::Invalid); }
    let mut slots = vec![httparse::EMPTY_HEADER; max_headers];
    let mut parsed = httparse::Response::new(&mut slots);
    if !matches!(parsed.parse(head), Ok(httparse::Status::Complete(n)) if n == head.len())
        || parsed.version != Some(1) || parsed.code != Some(200) { return Err(Error::Invalid); }
    let headers = headers(parsed.headers)?;
    if unsupported(&headers) { return Err(Error::Invalid); }
    let length = content_length(&headers)?.ok_or(Error::Invalid)?;
    if length > max_body { return Err(Error::TooLarge); }
    let remove = connection_tokens(&headers)?;
    Ok(Response { length, headers, remove })
}

pub fn downstream(response: &Response, body: &[u8]) -> Vec<u8> {
    let mut output = b"HTTP/1.1 200 OK\r\n".to_vec();
    append_headers(&mut output, &response.headers, &response.remove);
    output.extend_from_slice(format!("Content-Length: {}\r\nConnection: close\r\n\r\n", body.len()).as_bytes());
    output.extend_from_slice(body); output
}

#[cfg(test)]
mod tests {
    use super::*;
    fn req(path: &str) -> Request { request(format!("GET {path} HTTP/1.1\r\nHost: test.invalid\r\n\r\n").as_bytes(), 64).unwrap() }

    #[test]
    fn first_match_and_raw_path() {
        let rules = [Rule::parse("deny:GET:/blocked").unwrap(), Rule::parse("allow:GET:/blocked").unwrap(),
                     Rule::parse("allow:GET:/allowed").unwrap()];
        assert!(!allowed(&req("/blocked"), &rules)); assert!(allowed(&req("/allowed?x=1"), &rules));
        for path in ["/Allowed", "/%61llowed", "/unknown"] { assert!(!allowed(&req(path), &rules)); }
        assert!(!allowed(&req("/allowed"), &[]));
    }

    #[test]
    fn wildcard_rules_and_invalid_configuration() {
        assert!(allowed(&req("/allowed"), &[Rule::parse("allow:*:/allowed").unwrap()]));
        assert!(!allowed(&req("/other"), &[Rule::parse("allow:*:/allowed").unwrap()]));
        assert!(allowed(&req("/other"), &[Rule::parse("allow:GET:*").unwrap()]));
        for rule in ["allow::/allowed", "other:GET:/allowed", "allow:GET:/allowed?x=1", "allow:GET:relative"] {
            assert!(Rule::parse(rule).is_err(), "{rule}");
        }
    }

    #[test]
    fn rejects_ambiguous_and_unsupported_requests() {
        for input in ["GET / HTTP/1.1\r\n\r\n", "GET / HTTP/1.0\r\nHost: x\r\n\r\n",
            "GET  / HTTP/1.1\r\nHost: x\r\n\r\n", "GET / HTTP/1.1\nHost: x\n\n",
            "GET / HTTP/1.1\r\nHost: x\r\nHost: y\r\n\r\n", "GET / HTTP/1.1\r\nHost: x\r\nContent-Length: 0\r\nContent-Length: 0\r\n\r\n",
            "GET / HTTP/1.1\r\nHost: x\r\nContent-Length: 1\r\n\r\n", "GET / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n",
            "GET / HTTP/1.1\r\nHost: x\r\nConnection: content-length\r\n\r\n", "GET / HTTP/1.1\r\nHost: bad host\r\n\r\n"] {
            assert!(request(input.as_bytes(), 64).is_err(), "{input:?}");
        }
    }

    #[test]
    fn strips_connection_named_headers_and_rewrites_host() {
        let input = b"GET /allowed?x=1 HTTP/1.1\r\nHost: client.invalid\r\nConnection: x-secret, keep-alive\r\nX-Secret: hidden\r\nX-Keep: yes\r\nContent-Length: 0\r\n\r\n";
        let output = String::from_utf8(upstream(&request(input, 64).unwrap(), "127.0.0.1:9000".parse().unwrap())).unwrap();
        assert!(output.contains("Host: 127.0.0.1:9000\r\n")); assert!(output.contains("X-Keep: yes\r\n"));
        assert!(!output.contains("hidden")); assert!(!output.contains("client.invalid"));
        assert_eq!(output.matches("Content-Length:").count(), 1);
    }

    #[test]
    fn response_framing_is_bounded_and_unambiguous() {
        assert!(response(b"HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\n", 64, 4).is_ok());
        for head in ["HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\n", "HTTP/1.1 200 OK\r\n\r\n",
            "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nContent-Length: 0\r\n\r\n",
            "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nTransfer-Encoding: chunked\r\n\r\n"] {
            assert!(response(head.as_bytes(), 64, 4).is_err());
        }
    }
}
