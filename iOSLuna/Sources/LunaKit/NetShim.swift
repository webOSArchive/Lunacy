import Foundation

/// The native half of Lunacy's network shim (lunacy/net.js), as AndroidLuna's NetShim.kt and
/// Http.kt send it. webOS apps ran from file:// with no CORS check; a ported page lives on its
/// own origin, so its cross-origin XHRs come here and go out as the reference TouchPad sent
/// them (Lunacy's Workbench/probe 0.0.7): its headers, its Origin, its redirect rules.
///
/// Cookies are URLSession's shared store, not the WebView's, which an iOS host can't read
/// synchronously; native requests share them with each other, not with page-loaded images.
final class NetShim: NSObject, URLSessionTaskDelegate {
    private let appId: String
    private let profile: DeviceProfile
    /// Called on the main thread with a request's id and its result JSON.
    private let deliver: (Int, String) -> Void
    private var tasks: [Int: URLSessionTask] = [:]
    private var aborted: Set<Int> = []
    private let lock = NSLock()
    /// Which page's requests these are: a new page numbers its requests from 1 again.
    private var page = 0
    private lazy var session: URLSession = {
        let cfg = URLSessionConfiguration.default
        cfg.httpShouldSetCookies = true
        cfg.httpCookieStorage = .shared
        cfg.requestCachePolicy = .reloadIgnoringLocalCacheData
        cfg.urlCache = nil
        cfg.httpMaximumConnectionsPerHost = 6
        return URLSession(configuration: cfg, delegate: self, delegateQueue: nil)
    }()

    /// Delta: the TouchPad gave up on a server that never answered after about 9 s. URLSession
    /// has only an idle timeout, not a connect timeout, so such a request waits out the
    /// 300 s read timeout instead (or the page's own xhr.timeout).
    private static let maxRedirects = 20

    init(appId: String, profile: DeviceProfile, deliver: @escaping (Int, String) -> Void) {
        self.appId = appId; self.profile = profile; self.deliver = deliver
    }

    func send(id: Int, request: String) {
        let from = locked { page }
        run(id: id, request: request) { [weak self] r in
            DispatchQueue.main.async {
                guard let self else { return }
                let drop = self.locked { from != self.page || self.aborted.remove(id) != nil }
                if !drop { self.deliver(id, r) }
            }
        }
    }

    /// A synchronous XHR: blocks its page, as it did on webOS. Never on the main thread's work
    /// queue: the session answers on its own.
    func sendSync(request: String) -> String {
        let done = DispatchSemaphore(value: 0)
        var out = NetShim.error("no answer", "error")
        run(id: -1, request: request) { r in out = r; done.signal() }
        done.wait()
        return out
    }

    func abort(id: Int) {
        let t = locked { () -> URLSessionTask? in aborted.insert(id); return tasks.removeValue(forKey: id) }
        t?.cancel()
    }

    /// A new page in the window: earlier pages' requests are dropped, answered or not.
    func reset() {
        let all = locked { () -> [URLSessionTask] in page += 1; let a = Array(tasks.values); tasks = [:]; aborted = []; return a }
        all.forEach { $0.cancel() }
    }

    private func locked<T>(_ f: () -> T) -> T { lock.lock(); defer { lock.unlock() }; return f() }

    // MARK: one request

    private struct Req {
        var method: String
        var url: URL
        var headers: [(String, String)]
        var body: Data?
        var timeout: Int
        var binary: Bool
        var charset: String
    }

    private func run(id: Int, request: String, done: @escaping (String) -> Void) {
        guard let j = (try? JSONSerialization.jsonObject(with: Data(request.utf8))) as? [String: Any],
              let urlString = j["url"] as? String, let url = URL(string: urlString) else {
            return done(NetShim.error("bad request", "error"))
        }
        let method = (j["method"] as? String ?? "GET").uppercased()
        var headers: [(String, String)] = (j["headers"] as? [[Any]] ?? []).compactMap { h in
            guard h.count == 2, let k = h[0] as? String, let v = h[1] as? String else { return nil }
            return (k, v)
        }
        let given = Set(headers.map { $0.0.lowercased() })
        func byDefault(_ k: String, _ v: String) { if !given.contains(k.lowercased()) { headers.append((k, v)) } }
        byDefault("User-Agent", profile.userAgent)
        byDefault("X-Palm-Carrier", profile.carrierCode)
        byDefault("Accept", "*/*")
        byDefault("Accept-Language", "en-us,en;q=0.5")
        byDefault("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.3")
        // The origin of a file:// page on the TouchPad, sent with everything but GET and HEAD.
        if method != "GET" && method != "HEAD" { byDefault("Origin", "file://.media.cryptofs.apps.usr.palm.applications.\(appId)") }
        if let user = j["user"] as? String {
            let auth = Data((user + ":" + (j["password"] as? String ?? "")).utf8).base64EncodedString()
            byDefault("Authorization", "Basic " + auth)
        }
        var body: Data?
        if let b = j["body"] as? String {
            body = (j["bodyBase64"] as? Bool == true) ? Data(base64Encoded: b) : Data(b.utf8)
        }
        let req = Req(method: method, url: url, headers: headers, body: body, timeout: j["timeout"] as? Int ?? 0,
                      binary: j["binary"] as? Bool == true, charset: j["charset"] as? String ?? "")
        hop(id: id, req: req, referer: nil, hops: [], count: 0, done: done)
    }

    /// One hop, following redirects as the TouchPad's WebKit did: 301/302/303 turn a POST into
    /// a GET without its body (headers stay), and each hop sends the previous URL as Referer.
    private func hop(id: Int, req: Req, referer: String?, hops: [[(String, String)]], count: Int, done: @escaping (String) -> Void) {
        guard req.url.scheme == "http" || req.url.scheme == "https" else {
            return done(NetShim.error("can't fetch \(req.url.scheme ?? "") URLs", "error"))
        }
        var r = URLRequest(url: req.url, timeoutInterval: req.timeout > 0 ? Double(req.timeout) / 1000 : 300)
        r.httpMethod = req.method
        for (k, v) in req.headers { r.addValue(v, forHTTPHeaderField: k) }
        if let referer, r.value(forHTTPHeaderField: "Referer") == nil { r.setValue(referer, forHTTPHeaderField: "Referer") }
        r.httpBody = req.body
        let started = Date()
        let task = session.dataTask(with: r) { [weak self] data, response, error in
            guard let self else { return }
            if let error {
                if !self.locked({ self.aborted.contains(id) }) { lunaLog("[LunaKit] net [%@] %@ %@ failed: %@", self.appId, req.method, req.url.absoluteString, "\(error)") }
                // "timeout" only for the page's own xhr.timeout; a server that never answers is an "error".
                let timedOut = (error as? URLError)?.code == .timedOut && req.timeout > 0
                return done(NetShim.error("\(error)", timedOut ? "timeout" : "error"))
            }
            guard let http = response as? HTTPURLResponse else { return done(NetShim.error("not HTTP", "error")) }
            let headers = Self.headers(http)
            if (300...399).contains(http.statusCode), http.statusCode != 304,
               let loc = http.value(forHTTPHeaderField: "Location"), let next = URL(string: loc, relativeTo: req.url)?.absoluteURL {
                guard count < Self.maxRedirects else { return done(NetShim.error("too many redirects", "error")) }
                var n = req
                n.url = next
                if (301...303).contains(http.statusCode) && n.method != "GET" && n.method != "HEAD" { n.method = "GET"; n.body = nil }
                return self.hop(id: id, req: n, referer: req.url.absoluteString, hops: hops + [headers], count: count + 1, done: done)
            }
            let bytes = data ?? Data()
            lunaLog("[LunaKit] net [%@] %@ %@ %d %db %.0fms", self.appId, req.method, req.url.absoluteString, http.statusCode, bytes.count, Date().timeIntervalSince(started) * 1000)
            var out: [(String, Any)] = [
                ("status", http.statusCode), ("url", req.url.absoluteString),
                ("headers", Self.merged(hops + [headers]).map { [$0.0, $0.1] }),
            ]
            if req.binary { out.append(("base64", bytes.base64EncodedString())) }
            else { out.append(("text", Self.decode(bytes, override: req.charset, contentType: http.value(forHTTPHeaderField: "Content-Type")))) }
            done(orderedJSON(out))
        }
        locked { tasks[id] = task }
        task.resume()
    }

    // Redirects are followed by hand, above, so each hop's headers can be kept.
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }

    private static func headers(_ r: HTTPURLResponse) -> [(String, String)] {
        r.allHeaderFields.compactMap { k, v in (k as? String).map { ($0, "\(v)") } }
    }

    /// Every hop's headers merged by name, a later hop's value replacing an earlier one.
    private static func merged(_ hops: [[(String, String)]]) -> [(String, String)] {
        var order: [String] = []
        var byName: [String: [(String, String)]] = [:]
        for hop in hops {
            for name in Set(hop.map { $0.0.lowercased() }) { byName[name] = nil; order.removeAll { $0 == name } }
            for h in hop {
                let n = h.0.lowercased()
                if byName[n] == nil { order.append(n); byName[n] = [] }
                byName[n]!.append(h)
            }
        }
        return order.flatMap { byName[$0] ?? [] }
    }

    private static let charsetRe = try! NSRegularExpression(pattern: "charset\\s*=\\s*\"?([\\w.:-]+)", options: .caseInsensitive)
    private static let xmlDecl = try! NSRegularExpression(pattern: "^\\s*<\\?xml[^>]*encoding\\s*=\\s*[\"']([\\w.:-]+)")

    /// The page's override, then the response's charset, then an XML declaration, then UTF-8.
    static func decode(_ bytes: Data, override: String, contentType: String?) -> String {
        func first(_ re: NSRegularExpression, _ s: String) -> String? {
            let ns = s as NSString
            return re.firstMatch(in: s, range: NSRange(location: 0, length: ns.length)).map { ns.substring(with: $0.range(at: 1)) }
        }
        let head = String(decoding: bytes.prefix(200), as: UTF8.self)
        let name = override.isEmpty ? (contentType.flatMap { first(charsetRe, $0) } ?? first(xmlDecl, head)) : override
        var enc = String.Encoding.utf8
        if let name {
            let cf = CFStringConvertIANACharSetNameToEncoding(name as CFString)
            if cf != kCFStringEncodingInvalidId { enc = String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(cf)) }
        }
        return String(data: bytes, encoding: enc) ?? String(decoding: bytes, as: UTF8.self)
    }

    static func error(_ text: String, _ kind: String) -> String { orderedJSON([("error", kind), ("errorText", text)]) }
}
