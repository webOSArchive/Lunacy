import Foundation
import WebKit

/// `/__lunacy/net?u=<url>` on the app's origin: an `http://` image or media file, fetched by
/// the host and handed to the page (compat.js, "Local network", sends them here when the host
/// says `localNetworkBlocked()`; AndroidLuna's LocalNet.kt is the Android half).
///
/// iOS's reason is not Android's. WebKit plays media through AVFoundation, which believes the
/// type a server declares and the URL's extension; the TouchPad's media server, like Chromium,
/// looked at the bytes. Pandora's CDN serves raw ADTS AAC as `….mp4`, `application/octet-stream`,
/// which AVFoundation opens as an MP4 and fails (AVFoundationErrorDomain -11800, measured
/// 2026-10-29). So media comes through here and is declared as what it is.
///
/// Byte ranges pass through to the server as asked, so seeking and streaming work as before.
final class NetRoute: NSObject, URLSessionDataDelegate {
    private struct Pending { let task: WKURLSchemeTask; let finished: () -> Void }

    private let userAgent: String
    private var types: [String: String] = [:]           // url -> sniffed type
    private var pending: [Int: Pending] = [:]           // session task id -> scheme task
    private var upstream: [ObjectIdentifier: URLSessionTask] = [:]
    private lazy var session = URLSession(configuration: .default, delegate: self, delegateQueue: .main)

    init(userAgent: String) { self.userAgent = userAgent }

    /// Main thread.
    func start(_ task: WKURLSchemeTask, target: String, isStopped: @escaping () -> Bool, finished: @escaping () -> Void) {
        guard let url = URL(string: target), url.scheme == "http" || url.scheme == "https" else {
            finished(); return fail(task, status: 400)
        }
        if types[target] != nil { return fetch(task, url: url, finished: finished) }
        // The type comes from the start of the file, whatever range the player asks for first.
        var probe = URLRequest(url: url)
        probe.setValue("bytes=0-15", forHTTPHeaderField: "Range")
        probe.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        URLSession.shared.dataTask(with: probe) { [weak self] data, response, _ in
            DispatchQueue.main.async {
                guard let self, !isStopped() else { return }
                let declared = (response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Content-Type")
                self.types[target] = Self.sniff(data ?? Data()) ?? declared ?? "application/octet-stream"
                self.fetch(task, url: url, finished: finished)
            }
        }.resume()
    }

    func stop(_ task: WKURLSchemeTask) {
        guard let t = upstream.removeValue(forKey: ObjectIdentifier(task)) else { return }
        pending[t.taskIdentifier] = nil
        t.cancel()
    }

    private func fetch(_ task: WKURLSchemeTask, url: URL, finished: @escaping () -> Void) {
        var r = URLRequest(url: url)
        r.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        if let range = task.request.value(forHTTPHeaderField: "Range") { r.setValue(range, forHTTPHeaderField: "Range") }
        let t = session.dataTask(with: r)
        pending[t.taskIdentifier] = Pending(task: task, finished: finished)
        upstream[ObjectIdentifier(task)] = t
        t.resume()
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        guard let p = pending[dataTask.taskIdentifier], let http = response as? HTTPURLResponse else {
            return completionHandler(.cancel)
        }
        var headers = ["Access-Control-Allow-Origin": "*", "Accept-Ranges": "bytes"]
        for name in ["Content-Length", "Content-Range", "Last-Modified", "ETag", "Cache-Control"] {
            if let v = http.value(forHTTPHeaderField: name) { headers[name] = v }
        }
        let target = dataTask.originalRequest?.url?.absoluteString ?? ""
        headers["Content-Type"] = types[target] ?? http.value(forHTTPHeaderField: "Content-Type") ?? "application/octet-stream"
        p.task.didReceive(HTTPURLResponse(url: p.task.request.url!, statusCode: http.statusCode, httpVersion: "HTTP/1.1", headerFields: headers)!)
        completionHandler(.allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        pending[dataTask.taskIdentifier]?.task.didReceive(data)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let p = pending.removeValue(forKey: task.taskIdentifier) else { return }
        upstream[ObjectIdentifier(p.task)] = nil
        p.finished()
        if let error {
            lunaLog("[LunaKit] net route %@ failed: %@", task.originalRequest?.url?.host ?? "", "\(error)")
            p.task.didFailWithError(error)
        } else {
            p.task.didFinish()
        }
    }

    private func fail(_ task: WKURLSchemeTask, status: Int) {
        task.didReceive(HTTPURLResponse(url: task.request.url!, statusCode: status, httpVersion: "HTTP/1.1", headerFields: [:])!)
        task.didFinish()
    }

    /// What a file is, from its first bytes; nil if they don't say.
    static func sniff(_ d: Data) -> String? {
        let b = [UInt8](d.prefix(16))
        func ascii(_ s: String, at i: Int = 0) -> Bool {
            let u = Array(s.utf8); return b.count >= i + u.count && Array(b[i..<(i + u.count)]) == u
        }
        if ascii("ID3") { return "audio/mpeg" }
        if ascii("ftyp", at: 4) { return "audio/mp4" }
        if ascii("OggS") { return "audio/ogg" }
        if ascii("fLaC") { return "audio/flac" }
        if ascii("RIFF") && ascii("WAVE", at: 8) { return "audio/wav" }
        if ascii("#EXTM3U") { return "application/vnd.apple.mpegurl" }
        if b.count >= 4 && b[0] == 0x89 && ascii("PNG", at: 1) { return "image/png" }
        if b.count >= 3 && b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF { return "image/jpeg" }
        if ascii("GIF8") { return "image/gif" }
        // MPEG audio frame sync: 11 bits set. Layer bits 00 there mean ADTS (AAC), otherwise MP3.
        if b.count >= 2 && b[0] == 0xFF && (b[1] & 0xE0) == 0xE0 {
            return (b[1] & 0x06) == 0 ? "audio/aac" : "audio/mpeg"
        }
        return nil
    }
}
