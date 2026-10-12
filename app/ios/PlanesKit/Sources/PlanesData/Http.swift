import Foundation

public struct HttpResponse: Sendable {
  public var code: Int
  public var body: Data
  public var headers: [String: String]

  public init(code: Int, body: Data, headers: [String: String] = [:]) {
    self.code = code
    self.body = body
    self.headers = headers
  }

  public func header(_ name: String) -> String? {
    headers.first { $0.key.caseInsensitiveCompare(name) == .orderedSame }?.value
  }
}

public enum HttpError: Error { case offline }

/// Minimal HTTP seam so connectors can be tested with canned responses. Throws `HttpError.offline`.
public protocol HttpTransport: Sendable {
  func get(_ url: String, headers: [String: String]) async throws -> HttpResponse
  func postForm(_ url: String, form: [String: String]) async throws -> HttpResponse
}

public struct URLSessionTransport: HttpTransport {
  let userAgent: String
  let session: URLSession

  public init(userAgent: String, timeout: TimeInterval = 15) {
    self.userAgent = userAgent
    let config = URLSessionConfiguration.ephemeral
    config.timeoutIntervalForRequest = timeout
    session = URLSession(configuration: config)
  }

  public func get(_ url: String, headers: [String: String]) async throws -> HttpResponse {
    var req = URLRequest(url: URL(string: url)!)
    headers.forEach { req.setValue($0.value, forHTTPHeaderField: $0.key) }
    return try await send(req)
  }

  public func postForm(_ url: String, form: [String: String]) async throws -> HttpResponse {
    var req = URLRequest(url: URL(string: url)!)
    req.httpMethod = "POST"
    req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
    var allowed = CharacterSet.alphanumerics
    allowed.insert(charactersIn: "-._~")
    req.httpBody = form.map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: allowed) ?? "")" }
      .joined(separator: "&").data(using: .utf8)
    return try await send(req)
  }

  private func send(_ request: URLRequest) async throws -> HttpResponse {
    var req = request
    req.setValue(userAgent, forHTTPHeaderField: "User-Agent")
    req.setValue("application/json", forHTTPHeaderField: "Accept")
    do {
      let (data, response) = try await session.data(for: req)
      let http = response as? HTTPURLResponse
      var headers: [String: String] = [:]
      http?.allHeaderFields.forEach { if let k = $0.key as? String { headers[k] = "\($0.value)" } }
      return HttpResponse(code: http?.statusCode ?? 0, body: data, headers: headers)
    } catch {
      throw HttpError.offline
    }
  }
}
