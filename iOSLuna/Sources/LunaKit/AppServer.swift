import Foundation
import WebKit

/// Serves a ported app the way AndroidLuna's AppServer.kt serves every app: on its own origin,
/// `webos://<appid>.media.cryptofs.apps`, at the path it had on the device
/// (`/media/cryptofs/apps/usr/palm/applications/<appid>/`), with the modernized frameworks at
/// `/usr/palm/frameworks/` and Lunacy's own scripts at `/__lunacy/`. Every file goes through
/// Lunacy's global serve-time transforms; the app's own files are never changed on disk.
///
/// WKWebView can't intercept http or https, so the origin's scheme is `webos:` where Android's
/// is `https:`. The host is the same, and that is what apps and Lunacy's scripts look at.
final class AppServer: NSObject, WKURLSchemeHandler {
    static let scheme = "webos"
    static let hostSuffix = ".media.cryptofs.apps"
    static let appsPath = "media/cryptofs/apps/usr/palm/applications/"
    /// A file read through palmGetResource comes back as it is on disk.
    static let resourceParam = "__lunacy_res"
    /// The longest a page waits for its fonts before its own scripts run anyway.
    static let fontWait: TimeInterval = 1.0

    private let appId: String
    private let package: URL
    private let assets: URL
    private let io = DispatchQueue(label: "LunaKit.appserver", attributes: .concurrent)
    /// Tasks started and not yet answered or stopped; answering a stopped one raises an exception.
    private var active = Set<ObjectIdentifier>()
    private lazy var route = NetRoute(userAgent: userAgent)
    private let userAgent: String
    /// Pages holding their scripts until their fonts are in, by token.
    private var fontWaits: [String: (WKURLSchemeTask) -> Void] = [:]
    private var fontTasks: [String: WKURLSchemeTask] = [:]
    private var fontLoaded = Set<String>()
    private var tokens = 0
    private let tokenLock = NSLock()

    init(appId: String, package: URL, assets: URL, userAgent: String) {
        self.appId = appId; self.package = package; self.assets = assets; self.userAgent = userAgent
    }

    static func url(appId: String, path: String) -> URL {
        URL(string: "\(scheme)://\(appId)\(hostSuffix)/\(appsPath)\(appId)/\(path)")!
    }

    // MARK: WKURLSchemeHandler (main thread)

    func webView(_ webView: WKWebView, start task: WKURLSchemeTask) {
        active.insert(ObjectIdentifier(task))
        guard let url = task.request.url, let host = url.host, host.hasSuffix(Self.hostSuffix) else {
            return respond(task, status: 404, mime: "text/plain", body: Data())
        }
        // A run of slashes is one, as the TouchPad's filesystem read a file:// path.
        let path = url.path.replacingOccurrences(of: "/{2,}", with: "/", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let query = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let resource = query.contains { $0.name == Self.resourceParam }

        switch path {
        case "__lunacy/net":
            let id = ObjectIdentifier(task)
            return route.start(task, target: query.first(where: { $0.name == "u" })?.value ?? "",
                               isStopped: { [weak self] in self?.active.contains(id) != true },
                               finished: { [weak self] in self?.active.remove(id) })
        case "__lunacy/fonts-loaded":
            if let t = query.first(where: { $0.name == "t" })?.value { fontsLoaded(t) }
            return respond(task, status: 204, mime: "text/plain", body: Data())
        case "__lunacy/fonts-ready.js":
            return fontsReady(task, token: query.first(where: { $0.name == "t" })?.value ?? "")
        // Enyo's Tellurium hooks read this while starting; the TouchPad answers 200 and nothing.
        case "usr/palm/frameworks/tellurium/tellurium_config.json":
            return respond(task, status: 200, mime: "application/json", body: Data())
        default: break
        }

        var file: URL?
        if path.hasPrefix("__lunacy/fonts/") {
            file = assets.appendingPathComponent("fonts/" + String(path.dropFirst("__lunacy/fonts/".count)))
        } else if path.hasPrefix("__lunacy/") {
            file = assets.appendingPathComponent("lunacy/" + String(path.dropFirst("__lunacy/".count)))
        } else if let r = path.range(of: "(?:^|/)usr/palm/frameworks/enyo/[^/]+/", options: .regularExpression) {
            file = assets.appendingPathComponent("fw/enyo/1.0/" + String(path[r.upperBound...]))
        } else if path.hasPrefix(Self.appsPath + appId + "/") {
            file = package.appendingPathComponent(String(path.dropFirst((Self.appsPath + appId + "/").count)))
        }
        guard let file, contained(file) else {
            lunaLog("[LunaKit] 404 %@", url.absoluteString)
            return respond(task, status: 404, mime: "text/plain", body: Data())
        }
        let name = path
        io.async { [weak self] in
            guard let self else { return }
            guard let data = try? Data(contentsOf: file) else {
                lunaLog("[LunaKit] 404 %@", url.absoluteString)
                return DispatchQueue.main.async { self.respond(task, status: 404, mime: "text/plain", body: Data()) }
            }
            let mime = Self.mime(name)
            let body = self.transform(data, mime: mime, name: name, resource: resource)
            DispatchQueue.main.async { self.respond(task, status: 200, mime: mime, body: body) }
        }
    }

    func webView(_ webView: WKWebView, stop task: WKURLSchemeTask) {
        active.remove(ObjectIdentifier(task))
        route.stop(task)
        if let t = fontTasks.first(where: { $0.value === task })?.key { fontTasks[t] = nil; fontWaits[t] = nil }
    }

    /// Only files inside the folders this server serves, whatever `..` a path carries.
    private func contained(_ f: URL) -> Bool {
        let p = f.standardizedFileURL.path
        return [package, assets].contains { p.hasPrefix($0.standardizedFileURL.path + "/") }
    }

    private func respond(_ task: WKURLSchemeTask, status: Int, mime: String, body: Data) {
        guard active.remove(ObjectIdentifier(task)) != nil else { return }
        var headers = ["Content-Type": mime.hasPrefix("text/") || mime.hasSuffix("javascript") || mime.hasSuffix("json") ? mime + "; charset=utf-8" : mime,
                       "Content-Length": String(body.count),
                       "Access-Control-Allow-Origin": "*"]
        if mime.hasPrefix("font/") { headers["Cache-Control"] = "max-age=31536000, immutable" }
        let r = HTTPURLResponse(url: task.request.url!, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers)!
        task.didReceive(r)
        if !body.isEmpty { task.didReceive(body) }
        task.didFinish()
    }

    // MARK: transforms

    private func transform(_ data: Data, mime: String, name: String, resource: Bool) -> Data {
        switch mime {
        case "text/html":
            guard let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) else { return data }
            let html = HtmlTransforms.selfClosingTags(text)
            return Data((resource ? html : inject(JsTransforms.sloppyMode(html))).utf8)
        case "text/css":
            guard var css = String(data: data, encoding: .utf8) else { return data }
            // Lunacy's fonts.css names the one origin Android serves every page's fonts from;
            // here each app's origin serves them itself.
            if name == "__lunacy/fonts.css" { css = css.replacingOccurrences(of: "https://lunacy-fonts.media.cryptofs.apps/__lunacy/fonts/", with: "/__lunacy/fonts/") }
            return Data(CssTransforms.apply(css).utf8)
        case "application/javascript":
            guard !resource, let js = String(data: data, encoding: .utf8) else { return data }
            return Data(JsTransforms.sloppyMode(js).utf8)
        default:
            return data
        }
    }

    /// Lunacy's scripts run first in every page, in AndroidLuna's order.
    private func inject(_ html: String) -> String {
        tokenLock.lock(); tokens += 1; let token = "\(tokens)"; tokenLock.unlock()
        let tag = "<link rel=\"stylesheet\" href=\"/__lunacy/fonts.css\">" + Self.fontLoad(token) +
            "<script src=\"/__lunacy/compat.js\"></script><script src=\"/__lunacy/bridge.js\"></script>" +
            "<script src=\"/__lunacy/crypt.js\"></script>" +
            "<script src=\"/__lunacy/textedit.js\"></script>" +
            "<script src=\"/__lunacy/net.js\"></script><script src=\"/__lunacy/websql.js\"></script>" +
            "<script src=\"/__lunacy/fonts-ready.js?t=\(token)\"></script>"
        if let r = html.range(of: "<head[^>]*>", options: [.regularExpression, .caseInsensitive]) {
            return String(html[..<r.upperBound]) + tag + String(html[r.upperBound...])
        }
        return tag + html
    }

    // MARK: the fonts, in hand before the page's own scripts run (AppServer.kt, "the fonts")

    /// The page asks for Prelude's default faces and reports when they are ready.
    private static func fontLoad(_ t: String) -> String {
        "<script>(function(){var t=\"\(t)\";" +
        "function done(){var x=new XMLHttpRequest();x.open(\"GET\",\"/__lunacy/fonts-loaded?t=\"+t,true);x.send();}" +
        "try{var s=document.fonts,w=[];if(!s||!s.forEach){done();return;}" +
        "s.forEach(function(f){if(f.family.replace(/[\"']/g,\"\")===\"Prelude\"&&f.style===\"normal\")w.push(f);});" +
        "var n=w.length;if(!n){done();return;}" +
        "function one(){if(--n===0)done();}" +
        "for(var i=0;i<w.length;i++)w[i].load().then(one,one);" +
        "}catch(e){done();}})();</script>"
    }

    private func fontsLoaded(_ t: String) {
        if let task = fontTasks.removeValue(forKey: t) { fontWaits.removeValue(forKey: t)?(task) }
        else { fontLoaded.insert(t) }
    }

    /// Held until the page's fonts are in, or for a second at most: the parser waits on it.
    private func fontsReady(_ task: WKURLSchemeTask, token: String) {
        let answer: (WKURLSchemeTask) -> Void = { [weak self] task in
            self?.respond(task, status: 200, mime: "application/javascript", body: Data("/* Lunacy: this page's fonts are in. */".utf8))
        }
        if fontLoaded.remove(token) != nil { return answer(task) }
        fontTasks[token] = task
        fontWaits[token] = answer
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.fontWait) { [weak self] in
            guard let self, let task = self.fontTasks.removeValue(forKey: token) else { return }
            lunaLog("[LunaKit] fonts not ready after %.0f ms", Self.fontWait * 1000)
            self.fontWaits.removeValue(forKey: token)?(task)
        }
    }

    static func mime(_ p: String) -> String {
        switch (p as NSString).pathExtension.lowercased() {
        case "html", "htm": return "text/html"
        case "js": return "application/javascript"
        case "css": return "text/css"
        case "json": return "application/json"
        case "png": return "image/png"
        case "jpg", "jpeg": return "image/jpeg"
        case "gif": return "image/gif"
        case "svg": return "image/svg+xml"
        case "ico": return "image/x-icon"
        case "mp3": return "audio/mpeg"
        case "wav": return "audio/wav"
        case "ttf": return "font/ttf"
        case "woff": return "font/woff"
        case "mp4", "m4a": return "audio/mp4"
        case "aac": return "audio/aac"
        case "ogg": return "audio/ogg"
        default: return "application/octet-stream"
        }
    }
}
