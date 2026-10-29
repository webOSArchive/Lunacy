import Foundation

/// The simulated Luna bus, as AndroidLuna's Bus.kt has it: `palm://service/method` to a
/// handler, subscriptions that keep delivering until cancelled, and webOS's own errors for
/// anything that isn't here. It never fakes a success (Lunacy's rule 2). Main thread only.
public final class Bus {
    public final class Call {
        public let appId: String
        public let service: String
        public let method: String
        public let params: [String: Any]
        private let send: (String) -> Void
        public private(set) var cancelled = false
        private var onCancel: [() -> Void] = []

        init(appId: String, service: String, method: String, params: [String: Any], send: @escaping (String) -> Void) {
            self.appId = appId; self.service = service; self.method = method; self.params = params; self.send = send
        }

        /// "subscribe": true, or a db8 watch.
        public var subscribe: Bool {
            params["subscribe"] as? Bool == true || params["subscribe"] as? String == "true" ||
                params["watch"] as? Bool == true || method == "watch"
        }
        public func reply(_ json: String) { if !cancelled { send(json) } }
        public func reply(_ o: [String: Any]) { reply(jsonText(o)) }
        /// Runs when the subscription ends.
        public func onCancel(_ f: @escaping () -> Void) { if cancelled { f() } else { onCancel.append(f) } }
        func cancel() {
            guard !cancelled else { return }
            cancelled = true
            let fs = onCancel; onCancel = []
            fs.forEach { $0() }
        }
    }

    public typealias Handler = (Call) -> Void
    private var handlers: [String: Handler] = [:]

    public init() {
        register("com.palm.bus", "signal/registerServerStatus") { [unowned self] in self.serverStatus($0) }
    }

    public func register(_ service: String, _ method: String, _ h: @escaping Handler) { handlers["\(service)/\(method)"] = h }

    public func has(_ service: String) -> Bool { handlers.keys.contains { $0.hasPrefix(service + "/") } }

    private static let uri = try! NSRegularExpression(pattern: "^(?:palm|luna)://([^/]+)/(.*?)/?$")

    @discardableResult
    func call(appId: String, url: String, params: String, reply: @escaping (String) -> Void) -> Call {
        var service = "", method = ""
        let ns = url as NSString
        if let m = Self.uri.firstMatch(in: url, range: NSRange(location: 0, length: ns.length)) {
            service = ns.substring(with: m.range(at: 1)); method = ns.substring(with: m.range(at: 2))
        }
        let p = (try? JSONSerialization.jsonObject(with: Data((params.isEmpty ? "{}" : params).utf8))) as? [String: Any] ?? [:]
        let c = Call(appId: appId, service: service, method: method, params: p, send: reply)
        let h = handlers["\(service)/\(method)"]
        lunaLog("[LunaKit] bus [%@] %@/%@ %@", appId, service, method, h == nil ? "UNHANDLED" : "")
        if let h { h(c) } else {
            // webOS names the category the method sits in (measured on the reference TouchPad).
            let leaf = method.split(separator: "/").last.map(String.init) ?? method
            let category = "/" + (method.contains("/") ? String(method[..<method.lastIndex(of: "/")!]) : "")
            c.reply(Bus.error(has(service) ? "Unknown method \"\(leaf)\" for category \"\(category)\"" : "Service does not exist: \(service)."))
        }
        return c
    }

    /// ls-hubd's own signal: is a service on the bus? {"serviceName", "connected"}, no returnValue.
    private func serverStatus(_ c: Call) {
        guard let name = c.params["serviceName"] as? String, !name.isEmpty else { return c.reply(Bus.error("Invalid payload.")) }
        c.reply(["serviceName": name, "connected": has(name)])
    }

    public static func error(_ text: String, code: Int = -1) -> String {
        orderedJSON([("returnValue", false), ("errorCode", code), ("errorText", text)])
    }
    public static func ok(_ extra: [String: Any] = [:]) -> String {
        var o = extra; o["returnValue"] = true; return jsonText(o)
    }
}
