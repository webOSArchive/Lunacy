import AVFoundation
import UIKit
import WebKit

/// A webOS app bundled into an iOS app, exactly as it was packaged.
public struct LunaApp {
    public let appId: String
    /// The app's folder: the one holding its appinfo.json.
    public let package: URL
    /// The folder assemble-assets.sh filled: lunacy/, fonts/, fw/.
    public let assets: URL
    public let profile: DeviceProfile

    public init(appId: String, package: URL, assets: URL, profile: DeviceProfile = .forThisDevice) {
        self.appId = appId; self.package = package; self.assets = assets; self.profile = profile
    }

    /// The app's own `main` page, from its appinfo.json.
    var main: String {
        let info = (try? Data(contentsOf: package.appendingPathComponent("appinfo.json")))
            .flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
        return info?["main"] as? String ?? "index.html"
    }
}

/// The app's card: its root window, filling the screen. Owns the bus and the services, which
/// every window of the app shares, and each window's native half (AppWindow).
open class LunaCardViewController: UIViewController {
    public let app: LunaApp
    let bus = Bus()
    private let server: AppServer
    private let connection = ConnectionManager()
    let keys = Keys()
    private let display = DisplayService()
    private let properties: SystemProperties
    private let applications: ApplicationManager
    private var windows: [ObjectIdentifier: AppWindow] = [:]
    private(set) var root: AppWindow!
    private var fullScreen = false
    private var routeObserver: NSObjectProtocol?

    public init(app: LunaApp) {
        self.app = app
        server = AppServer(appId: app.appId, package: app.package, assets: app.assets, userAgent: app.profile.userAgent)
        properties = SystemProperties(profile: app.profile)
        applications = ApplicationManager(appId: app.appId)
        super.init(nibName: nil, bundle: nil)
        properties.register(bus)
        connection.register(bus)
        keys.register(bus)
        display.register(bus)
        applications.register(bus)
    }

    public required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    open override var prefersStatusBarHidden: Bool { fullScreen }
    /// Luna's status bar was light text on black, and the card sits on black below it.
    open override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }

    open override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        // Media plays as music: on with the ringer switch off, and on in the background.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        routeObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] n in
            self?.routeChanged(n)
        }

        let config = WKWebViewConfiguration()
        config.setURLSchemeHandler(server, forURLScheme: AppServer.scheme)
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        config.preferences.javaScriptCanOpenWindowsAutomatically = true
        config.userContentController.add(WeakMessageHandler(self), name: "lunacy")
        config.userContentController.addUserScript(WKUserScript(
            source: nativeScript(), injectionTime: .atDocumentStart, forMainFrameOnly: false))

        root = makeWindow(config: config, attributes: [:])
        let wv = root.webView
        wv.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(wv)
        // The card is the screen inside iOS's safe area: below the status bar, as a webOS card
        // sat below Luna's (unless the app asks for full screen), and above the home indicator,
        // which stands where a phone's gesture area was, below its screen.
        let safe = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            wv.leadingAnchor.constraint(equalTo: safe.leadingAnchor),
            wv.trailingAnchor.constraint(equalTo: safe.trailingAnchor),
            wv.bottomAnchor.constraint(equalTo: safe.bottomAnchor),
            wv.topAnchor.constraint(equalTo: safe.topAnchor),
        ])
        wv.load(URLRequest(url: AppServer.url(appId: app.appId, path: app.main)))
    }

    /// ios-native.js with the values that don't change while a page lives.
    private func nativeScript() -> String {
        let url = Bundle.module.url(forResource: "ios-native", withExtension: "js", subdirectory: "JS")!
        let z = app.profile.pointsPerPixel
        let screen = CGSize(width: UIScreen.main.bounds.width / z, height: UIScreen.main.bounds.height / z)
        let locale = Locale.current
        let lang = locale.language.languageCode?.identifier.lowercased() ?? "en"
        let region = locale.region?.identifier.lowercased() ?? "us"
        let h24 = DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: locale)?.contains("a") == false
        let localeInfo = orderedJSON([("locale", "\(lang)_\(region)"), ("localeRegion", region), ("phoneRegion", region),
                                      ("timeFormat", h24 ? "HH24" : "HH12")])
        let statics = orderedJSON([("deviceInfo", app.profile.deviceInfo(screen: screen)), ("localeInfo", localeInfo),
                                   ("processId", Int(ProcessInfo.processInfo.processIdentifier))])
        return try! String(contentsOf: url, encoding: .utf8).replacingOccurrences(of: "__LUNACY_STATIC__", with: statics)
    }

    func makeWindow(config: WKWebViewConfiguration, attributes: [String: Any]) -> AppWindow {
        let wv = WKWebView(frame: view.bounds, configuration: config)
        wv.customUserAgent = app.profile.userAgent
        if #available(iOS 16.4, *) { wv.isInspectable = true }
        // Enyo scrolls its own content; the page itself never scrolls, bounces or zooms.
        wv.scrollView.isScrollEnabled = false
        wv.scrollView.bounces = false
        wv.scrollView.contentInsetAdjustmentBehavior = .never
        wv.isOpaque = false
        wv.backgroundColor = .black
        let w = AppWindow(card: self, webView: wv, attributes: attributes)
        wv.uiDelegate = w
        wv.navigationDelegate = w
        windows[ObjectIdentifier(wv)] = w
        return w
    }

    func closed(_ w: AppWindow) { windows[ObjectIdentifier(w.webView)] = nil }

    // MARK: what a window asks of the card

    /// A car or a Bluetooth headset connected: the page tells what's playing again
    /// (ios-native.js, __lunacyIOS.reannounce), once at once and once when a slow head unit
    /// has finished connecting.
    private func routeChanged(_ n: Notification) {
        let reason = (n.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt).flatMap(AVAudioSession.RouteChangeReason.init)
        guard reason == .newDeviceAvailable else { return }
        let remote: Set<AVAudioSession.Port> = [.bluetoothA2DP, .bluetoothHFP, .bluetoothLE, .carAudio, .airPlay]
        let outputs = AVAudioSession.sharedInstance().currentRoute.outputs.map(\.portType)
        guard outputs.contains(where: remote.contains) else { return }
        lunaLog("[LunaKit] [%@] audio now goes to %@; announcing what's playing", app.appId, outputs.map(\.rawValue).joined(separator: ", "))
        for delay in [1.0, 4.0] {
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
                self?.root?.evaluate("window.__lunacyIOS&&__lunacyIOS.reannounce&&__lunacyIOS.reannounce()")
            }
        }
    }

    func setFullScreen(_ on: Bool) {
        guard on != fullScreen else { return }
        fullScreen = on
        UIView.animate(withDuration: 0.25) { self.setNeedsStatusBarAppearanceUpdate() }
    }

    fileprivate func received(_ message: WKScriptMessage) {
        guard let wv = message.webView, let w = windows[ObjectIdentifier(wv)],
              let body = message.body as? [String: Any], let m = body["m"] as? String else { return }
        w.handle(m, body["a"] as? [Any] ?? [])
    }
}

/// WKUserContentController keeps its handlers; this keeps it from keeping the card.
private final class WeakMessageHandler: NSObject, WKScriptMessageHandler {
    weak var card: LunaCardViewController?
    init(_ card: LunaCardViewController) { self.card = card }
    func userContentController(_ c: WKUserContentController, didReceive message: WKScriptMessage) { card?.received(message) }
}

/// One webOS window: a WKWebView on the app's origin, and the native half of its
/// window.LunacyNative (ios-native.js). AndroidLuna's AppWindow.kt is the reference.
final class AppWindow: NSObject, WKUIDelegate, WKNavigationDelegate {
    unowned let card: LunaCardViewController
    let webView: WKWebView
    let attributes: [String: Any]
    private var calls: [Int: Bus.Call] = [:]
    private var children: [AppWindow] = []
    private var nextAttributes: [String: Any] = [:]
    private lazy var net = NetShim(appId: card.app.appId, profile: card.app.profile) { [weak self] id, r in
        self?.evaluate("window.__lunacyIOS&&__lunacyIOS.net(\(id),\(jsonString(r)))")
    }
    private lazy var sql = WebSql(appId: card.app.appId) { [weak self] id, r in
        self?.evaluate("window.__lunacyIOS&&__lunacyIOS.sql(\(id),\(jsonString(r)))")
    }

    init(card: LunaCardViewController, webView: WKWebView, attributes: [String: Any]) {
        self.card = card; self.webView = webView; self.attributes = attributes
    }

    func evaluate(_ js: String) { webView.evaluateJavaScript(js, completionHandler: nil) }

    /// The methods ios-native.js posts.
    func handle(_ m: String, _ a: [Any]) {
        func int(_ i: Int) -> Int { (a.count > i ? a[i] as? Int : nil) ?? 0 }
        func str(_ i: Int) -> String { (a.count > i ? a[i] as? String : nil) ?? "" }
        func bool(_ i: Int) -> Bool { (a.count > i ? a[i] as? Bool : nil) ?? false }
        switch m {
        case "call":
            let token = int(0)
            calls[token]?.cancel()
            let c = card.bus.call(appId: card.app.appId, url: str(1), params: str(2)) { [weak self] reply in
                self?.evaluate("PalmServiceBridge.__reply(\(token),\(jsonString(reply)))")
            }
            if c.subscribe && !c.cancelled { calls[token] = c }
        case "cancel": calls.removeValue(forKey: int(0))?.cancel()
        case "log": lunaLog("[%@] palm %@", card.app.appId, str(0))
        case "console": lunaLog("[%@] console.%@ %@", card.app.appId, str(0), str(1))
        case "netSend": net.send(id: int(0), request: str(1))
        case "netAbort": net.abort(id: int(0))
        case "sqlRequest": sql.request(id: int(0), request: str(1))
        case "fullScreen": if self === card.root { card.setFullScreen(bool(0)) }
        case "blockScreenTimeout": UIApplication.shared.isIdleTimerDisabled = bool(0)
        case "nextWindow":
            nextAttributes = (try? JSONSerialization.jsonObject(with: Data(str(0).utf8))) as? [String: Any] ?? [:]
        case "mediaKey":
            lunaLog("[LunaKit] [%@] media key %@", card.app.appId, str(0))
            card.keys.press(str(0))
        case "mediaSession":
            lunaLog("[LunaKit] [%@] media buttons via %@", card.app.appId, bool(0) ? "Media Session" : "the command center")
            if bool(0) { card.keys.pageHasMediaSession = true }
        case "setClipboard": UIPasteboard.general.string = str(0)
        case "paste":
            if let text = UIPasteboard.general.string { evaluate("window.__lunacyPaste&&__lunacyPaste(\(jsonString(text)))") }
        case "stageReady", "pageDrawn", "activate", "keyboard", "keyboardResizes", "statusBarColor", "requestOrientation":
            break  // the card is always the one on screen, and iOS owns the keyboard and the bar
        default:
            lunaLog("[LunaKit] [%@] LunacyNative.%@ isn't on iOS", card.app.appId, m)
        }
    }

    /// The calls that must answer at once, through prompt().
    private func sync(_ m: String, _ a: [Any]) -> Any? {
        func str(_ i: Int) -> String { (a.count > i ? a[i] as? String : nil) ?? "" }
        switch m {
        case "sqlOpen": return sql.open(name: str(0), version: str(1), creationCallback: (a.count > 3 ? a[3] as? Bool : nil) ?? false)
        case "netSendSync": return net.sendSync(request: str(0))
        case "screenSize":
            let s = webView.window?.screen.bounds.size ?? UIScreen.main.bounds.size
            let z = card.app.profile.pointsPerPixel
            return orderedJSON([("width", Int((s.width / z).rounded())), ("height", Int((s.height / z).rounded()))])
        case "cardSize":
            // In TouchPad px, with `scale` the device px in each: compat.js pins the viewport at
            // scale / devicePixelRatio, so this is what makes one CSS px a TouchPad px.
            let s = webView.bounds.size
            let z = card.app.profile.pointsPerPixel
            let scale = Double((webView.window?.screen.scale ?? UIScreen.main.scale) * z)
            return orderedJSON([("width", Int((s.width / z).rounded())), ("height", Int((s.height / z).rounded())), ("scale", scale)])
        case "screenOrientation":
            // webOS's names: the TouchPad says "up" in portrait and "right" in landscape.
            switch webView.window?.windowScene?.interfaceOrientation {
            case .portraitUpsideDown: return "down"
            case .landscapeLeft: return "left"
            case .landscapeRight: return "right"
            default: return "up"
            }
        case "clipboardHasText": return UIPasteboard.general.hasStrings
        default: return nil
        }
    }

    // MARK: WKUIDelegate

    func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (String?) -> Void) {
        guard prompt == "\u{0}lunacy", let d = defaultText,
              let j = (try? JSONSerialization.jsonObject(with: Data(d.utf8))) as? [String: Any], let m = j["m"] as? String else {
            return completionHandler(nil)
        }
        let v = sync(m, j["a"] as? [Any] ?? [])
        completionHandler(orderedJSON([("v", v ?? NSNull())]))
    }

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        lunaLog("[%@] alert: %@", card.app.appId, message)
        completionHandler()
    }

    /// Another window of the app: a dashboard, a popup alert or a card. It runs, so the app's
    /// windows work as they did, but isn't shown: an iOS port is one card (see README, deltas).
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        let attrs = nextAttributes
        nextAttributes = [:]
        lunaLog("[LunaKit] [%@] window %@ %@ (not shown)", card.app.appId, action.request.url?.absoluteString ?? "", "\(attrs["window"] ?? "card")")
        let w = card.makeWindow(config: configuration, attributes: attrs)
        children.append(w)
        return w.webView
    }

    func webViewDidClose(_ webView: WKWebView) {
        guard webView !== self.webView, let i = children.firstIndex(where: { $0.webView === webView }) else { return }
        card.closed(children.remove(at: i))
    }

    // MARK: WKNavigationDelegate

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        // A new page: the old one's requests, statements and subscriptions end with it.
        net.reset(); sql.reset()
        calls.values.forEach { $0.cancel() }; calls = [:]
    }

    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        // A link off the app's origin goes to the system, as webOS handed it to the browser.
        if let url = action.request.url, url.scheme != AppServer.scheme, url.scheme != "about",
           action.targetFrame?.isMainFrame != false, action.navigationType == .linkActivated {
            UIApplication.shared.open(url)
            return decisionHandler(.cancel)
        }
        decisionHandler(.allow)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        lunaLog("[LunaKit] [%@] web content process ended; reloading", card.app.appId)
        webView.reload()
    }
}
