import AVFoundation
import MediaPlayer
import Network
import UIKit

// The bus services a ported app gets, each backed by iOS and answering in the reference
// TouchPad's shapes (AndroidLuna's SystemProperties.kt, ConnectionManager.kt, Keys.kt,
// DisplayService.kt). Only what an app has needed is here; anything else gets the bus's
// honest error.

/// palm://com.palm.preferences/systemProperties/Get (and getSysProperty, which the TouchPad
/// answers the same way). A subscription gets the one reply, as on the TouchPad.
final class SystemProperties {
    let profile: DeviceProfile
    init(profile: DeviceProfile) { self.profile = profile }

    func register(_ bus: Bus) {
        for m in ["systemProperties/Get", "systemProperties/getSysProperty"] {
            bus.register("com.palm.preferences", m) { [unowned self] c in
                let key = c.params["key"] as? String ?? ""
                // The TouchPad's errors here have no errorCode.
                if key.isEmpty { return c.reply(orderedJSON([("returnValue", false), ("errorText", "missing parameter key")])) }
                guard let v = self.profile.systemProperties[key] else {
                    return c.reply(orderedJSON([("returnValue", false), ("errorText", "no such key")]))
                }
                c.reply(orderedJSON([(key, v), ("returnValue", true)]))
            }
        }
    }
}

/// palm://com.palm.connectionmanager/getstatus (and getStatus), from Network's path monitor.
/// A subscription gets the status at once and again whenever it changes.
final class ConnectionManager {
    private let monitor = NWPathMonitor()
    private var path: NWPath?
    private var subscribers: [ObjectIdentifier: Bus.Call] = [:]
    private var last = ""

    init() {
        monitor.pathUpdateHandler = { [weak self] p in DispatchQueue.main.async { self?.changed(p) } }
        monitor.start(queue: DispatchQueue(label: "LunaKit.connectionmanager"))
    }
    deinit { monitor.cancel() }

    func register(_ bus: Bus) {
        let h: Bus.Handler = { [unowned self] c in
            let s = self.status()
            c.reply(s)
            if c.subscribe && !c.cancelled {
                let key = ObjectIdentifier(c)
                self.subscribers[key] = c
                self.last = s
                c.onCancel { [weak self] in self?.subscribers[key] = nil }
            }
        }
        bus.register("com.palm.connectionmanager", "getstatus", h)
        bus.register("com.palm.connectionmanager", "getStatus", h)
    }

    private func changed(_ p: NWPath) {
        path = p
        let s = status()
        guard s != last else { return }
        last = s
        subscribers.values.forEach { $0.reply(s) }
    }

    func status() -> String {
        let p = path ?? monitor.currentPath
        let online = p.status == .satisfied
        let wifi: [(String, Any)] = online && p.usesInterfaceType(.wifi)
            // eth0 is the TouchPad's Wi-Fi interface, as an app that shows it would have shown.
            ? [("state", "connected"), ("interfaceName", "eth0"), ("networkConfidenceLevel", "excellent"),
               ("onInternet", "yes"), ("isWakeOnWifiEnabled", true)]
            : [("state", "disconnected")]
        let wan: [(String, Any)] = online && p.usesInterfaceType(.cellular)
            ? [("state", "connected"), ("onInternet", "yes")] : [("state", "disconnected")]
        // An iPad on some other interface (wired, say) is reported as Wi-Fi: webOS had no third kind.
        let other = online && !p.usesInterfaceType(.wifi) && !p.usesInterfaceType(.cellular)
        return "{" + [
            "\"returnValue\":true",
            "\"isInternetConnectionAvailable\":\(online)",
            "\"wifi\":" + orderedJSON(other ? [("state", "connected"), ("interfaceName", "eth0"), ("onInternet", "yes")] : wifi),
            "\"wan\":" + orderedJSON(wan),
            "\"vpn\":{\"state\":\"disconnected\"}",
            "\"bridge\":{\"state\":\"disconnected\"}",
        ].joined(separator: ",") + "}"
    }
}

/// palm://com.palm.keys: the headset, and the media keys - which on iOS are the lock screen's,
/// Control Center's, a car's and a headset's play, pause and skip buttons. While a page plays,
/// WebKit owns those and the page hears them through the Media Session API (ios-native.js), which
/// hands them here as key presses; MPRemoteCommandCenter is the fallback where it can't.
/// The TouchPad answers these only to a subscriber.
final class Keys {
    private var headsetSubscribers: [ObjectIdentifier: Bus.Call] = [:]
    private var mediaSubscribers: [ObjectIdentifier: Bus.Call] = [:]
    private var routeObserver: NSObjectProtocol?
    private var commandTargets: [(MPRemoteCommand, Any)] = []
    /// The page has the Media Session API, so the buttons come from it rather than the command center.
    var pageHasMediaSession = false {
        didSet { if pageHasMediaSession { releaseCommands() } }
    }

    func register(_ bus: Bus) {
        bus.register("com.palm.keys", "headset/status") { [unowned self] c in
            guard c.subscribe else { return c.reply(Self.needsSubscription) }
            c.reply(Self.subscribed)
            let key = ObjectIdentifier(c)
            self.headsetSubscribers[key] = c
            c.onCancel { [weak self] in self?.headsetSubscribers[key] = nil }
            self.watchRoute()
        }
        bus.register("com.palm.keys", "media/status") { [unowned self] c in
            guard c.subscribe else { return c.reply(Self.needsSubscription) }
            c.reply(Self.subscribed)
            let key = ObjectIdentifier(c)
            self.mediaSubscribers[key] = c
            c.onCancel { [weak self] in
                self?.mediaSubscribers[key] = nil
                if self?.mediaSubscribers.isEmpty == true { self?.releaseCommands() }
            }
            self.takeCommands()
        }
    }

    /// webOS's own wording, typo and all.
    static let needsSubscription = orderedJSON([
        ("errorCode", -1), ("errorText", "We were expecting a subscribe type message, but we did not recieve one."),
        ("returnValue", false), ("subscribed", false)])
    static let subscribed = orderedJSON([("returnValue", true), ("subscribed", true)])

    // MARK: media keys

    /// A key press as webOS's keys service sent it: a down, then an up.
    func press(_ key: String) {
        for state in ["down", "up"] {
            let event = orderedJSON([("key", key), ("state", state)])
            mediaSubscribers.values.forEach { $0.reply(event) }
        }
    }

    private func takeCommands() {
        guard commandTargets.isEmpty, !pageHasMediaSession else { return }
        let cc = MPRemoteCommandCenter.shared()
        func on(_ cmd: MPRemoteCommand, _ key: String) {
            cmd.isEnabled = true
            let t = cmd.addTarget { [weak self] _ in self?.press(key); return .success }
            commandTargets.append((cmd, t))
        }
        on(cc.playCommand, "play")
        on(cc.pauseCommand, "pause")
        on(cc.togglePlayPauseCommand, "togglePause")
        on(cc.nextTrackCommand, "next")
        on(cc.previousTrackCommand, "prev")
        on(cc.stopCommand, "stop")
    }

    private func releaseCommands() {
        commandTargets.forEach { cmd, t in cmd.removeTarget(t); cmd.isEnabled = false }
        commandTargets = []
    }

    // MARK: headset

    private func watchRoute() {
        guard routeObserver == nil else { return }
        routeObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self else { return }
            let event = orderedJSON([("returnValue", true), ("state", Self.headsetState())])
            self.headsetSubscribers.values.forEach { $0.reply(event) }
        }
    }

    private static func headsetState() -> String {
        let outputs = AVAudioSession.sharedInstance().currentRoute.outputs.map(\.portType)
        let inputs = AVAudioSession.sharedInstance().currentRoute.inputs.map(\.portType)
        guard outputs.contains(.headphones) || outputs.contains(.bluetoothA2DP) else { return "none" }
        return inputs.contains(.headsetMic) ? "headset" : "headset_mic_less"
    }
}

/// palm://com.palm.display/control: the screen's state. Apps ask while starting (Apollo does).
final class DisplayService {
    private var subscribers: [ObjectIdentifier: Bus.Call] = [:]
    private var observers: [NSObjectProtocol] = []
    private var screenOn = true

    init() {
        let nc = NotificationCenter.default
        observers.append(nc.addObserver(forName: UIApplication.protectedDataWillBecomeUnavailableNotification, object: nil, queue: .main) { [weak self] _ in self?.set(on: false) })
        observers.append(nc.addObserver(forName: UIApplication.protectedDataDidBecomeAvailableNotification, object: nil, queue: .main) { [weak self] _ in self?.set(on: true) })
    }

    func register(_ bus: Bus) {
        bus.register("com.palm.display", "control/status") { [unowned self] c in
            c.reply(self.statusReply(subscribed: c.subscribe))
            guard c.subscribe && !c.cancelled else { return }
            let key = ObjectIdentifier(c)
            self.subscribers[key] = c
            c.onCancel { [weak self] in self?.subscribers[key] = nil }
        }
    }

    private func set(on: Bool) {
        guard on != screenOn else { return }
        screenOn = on
        let event = statusReply(subscribed: true)
        subscribers.values.forEach { $0.reply(event) }
    }

    /// The reference TouchPad's shape; blockDisplay really is a string there.
    private func statusReply(subscribed: Bool) -> String {
        orderedJSON([("returnValue", true), ("event", "request"), ("state", screenOn ? "on" : "off"),
                     ("timeout", 1800), ("blockDisplay", "false"), ("active", screenOn), ("subscribed", subscribed)])
    }
}

/// palm://com.palm.applicationManager/open and launch, for what iOS can open: a URL (web,
/// mail, phone) hands it to the system. Launching another webOS app by id can't happen here -
/// a port is one app - and says so.
final class ApplicationManager {
    let appId: String
    init(appId: String) { self.appId = appId }

    func register(_ bus: Bus) {
        let h: Bus.Handler = { [unowned self] c in
            let params = c.params["params"] as? [String: Any]
            let target = c.params["target"] as? String ?? params?["target"] as? String
            if let target, let url = URL(string: target), url.scheme != nil {
                UIApplication.shared.open(url)
                return c.reply(Bus.ok())
            }
            if let id = c.params["id"] as? String, id == self.appId { return c.reply(Bus.ok()) }
            c.reply(Bus.error("iOSLuna can't launch another webOS app (\(c.params["id"] as? String ?? "no target"))"))
        }
        bus.register("com.palm.applicationManager", "open", h)
        bus.register("com.palm.applicationManager", "launch", h)
    }
}
