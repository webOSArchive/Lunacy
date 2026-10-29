import CryptoKit
import UIKit

/// What a ported app is told about the device it runs on: a reference device's values
/// (measured for Lunacy: AndroidLuna's DeviceProfile.kt), with this device's own screen and ids.
/// As in Lunacy, a tablet is told it is a TouchPad and a phone that it is a Pre3.
///
/// One CSS px is one point by default. An iPad point is, near enough, a TouchPad pixel (both
/// about 132 per inch), so Enyo's layouts fill the real screen at the TouchPad's own sizes; on a
/// phone the page is laid out at the card's width in points, as Lunacy lays it out in dips.
public struct DeviceProfile {
    /// The reference TouchPad, webOS CE 3.1.0.
    public static let touchPad = DeviceProfile(
        name: "TOUCHPAD", modelName: "TouchPad", modelNameAscii: "TouchPad", deviceName: "HP TouchPad",
        model: "HSTNH-I29C", boardType: "topaz-Wifi-pvt\n", platformVersion: (3, 1, 0), versionPrefix: "webOS CE",
        userAgent: "Mozilla/5.0 (hp-tablet; Linux; hpwOS/3.1.0; U; en-US) AppleWebKit/534.6 " +
            "(KHTML, like Gecko) wOSSystem/234.83 Safari/534.6 TouchPad/1.0",
        carrierCode: "c090-01", carrierName: "", productLineVersion: "1.0", browserOsName: "hpwOS",
        buildName: "Nova-HP-Topaz", buildNumber: "86", serialPrefix: "5CL", serialBodyLength: 7,
        keyboard: nil, swappableBattery: false, naturalLandscape: true,
        minimumCardHeight: 318, touchableRows: 14, positiveSpaceTopPadding: 28, reportsCarrierAvailable: true)

    /// The reference Pre3, HP webOS 2.2.4 (an AT&T unit: its carrier values are that unit's).
    public static let pre3 = DeviceProfile(
        name: "PRE3", modelName: "Prē3", modelNameAscii: "Pre3", deviceName: "HP Pre3",
        model: "HSTNH-F30CN", boardType: "mantaray-pvt", platformVersion: (2, 2, 4), versionPrefix: "HP webOS",
        userAgent: "Mozilla/5.0 (Linux; webOS/2.2.4; U; en-US) AppleWebKit/534.6 " +
            "(KHTML, like Gecko) webOSSystem/221.56 Safari/534.6 Pre/3.0",
        carrierCode: "c001-01", carrierName: "ATT", productLineVersion: "3.0", browserOsName: nil,
        buildName: "Nova-ATT-Mantaray", buildNumber: "2211", serialPrefix: "MTRE", serialBodyLength: 10,
        keyboard: "QWERTY", swappableBattery: true, naturalLandscape: false,
        minimumCardHeight: 318, touchableRows: 14, positiveSpaceTopPadding: 42, reportsCarrierAvailable: false)

    /// The one Lunacy would choose for this device: a Pre3 on a phone, a TouchPad otherwise.
    public static var forThisDevice: DeviceProfile { UIDevice.current.userInterfaceIdiom == .phone ? .pre3 : .touchPad }

    /// iOS points per webOS pixel: how large the app is drawn.
    public var pointsPerPixel: CGFloat = 1

    let name: String
    let modelName: String
    let modelNameAscii: String
    let deviceName: String
    let model: String
    let boardType: String
    let platformVersion: (Int, Int, Int)
    let versionPrefix: String
    let userAgent: String
    /// X-Palm-Carrier, which webOS put on every request.
    let carrierCode: String
    let carrierName: String
    let productLineVersion: String
    /// 2.2.4 hasn't got this property at all.
    let browserOsName: String?
    let buildName: String
    let buildNumber: String
    let serialPrefix: String
    let serialBodyLength: Int
    /// The hardware keyboard's type, if the device has one (a Pre3's slides out).
    let keyboard: String?
    let swappableBattery: Bool
    /// Which side deviceInfo calls the screen's width: a TouchPad names its screen 1024 x 768,
    /// a Pre3 its own 480 x 800.
    let naturalLandscape: Bool
    let minimumCardHeight: Int
    let touchableRows: Int
    let positiveSpaceTopPadding: Int
    /// The Pre3's deviceInfo has no carrierAvailable member at all.
    let reportsCarrierAvailable: Bool

    var versionString: String { "\(versionPrefix) \(platformVersion.0).\(platformVersion.1).\(platformVersion.2)" }

    /// PalmSystem.deviceInfo, in the device's member order. The screen is named in its natural
    /// orientation and never changes as it turns.
    func deviceInfo(screen: CGSize) -> String {
        let long = Int(max(screen.width, screen.height)), short = Int(min(screen.width, screen.height))
        let w = naturalLandscape ? long : short, h = naturalLandscape ? short : long
        var pairs: [(String, Any)] = [
            ("modelName", modelName), ("modelNameAscii", modelNameAscii),
            ("platformVersion", "\(platformVersion.0).\(platformVersion.1).\(platformVersion.2)"),
            ("platformVersionMajor", platformVersion.0), ("platformVersionMinor", platformVersion.1),
            ("platformVersionDot", platformVersion.2),
            ("carrierName", carrierName), ("serialNumber", Self.serial(self)),
            ("screenWidth", w), ("screenHeight", h),
            ("minimumCardWidth", w), ("minimumCardHeight", minimumCardHeight),
            ("maximumCardWidth", w), ("maximumCardHeight", h - positiveSpaceTopPadding),
            ("touchableRows", touchableRows),
            ("keyboardAvailable", keyboard != nil), ("keyboardSlider", keyboard != nil), ("keyboardType", keyboard ?? "Unknown"),
            ("wifiAvailable", true), ("bluetoothAvailable", true),
        ]
        if reportsCarrierAvailable { pairs.append(("carrierAvailable", false)) }
        pairs += [("coreNaviButton", false), ("swappableBattery", swappableBattery), ("dockModeEnabled", true)]
        return orderedJSON(pairs)
    }

    /// com.palm.preferences/systemProperties values.
    var systemProperties: [String: String] {
        var p = [
            "com.palm.properties.nduid": Self.nduid,
            "com.palm.properties.ProdSN": Self.serial(self),
            "com.palm.properties.version": versionString,
            "com.palm.properties.deviceName": deviceName,
            "com.palm.properties.deviceNameShort": modelNameAscii,
            "com.palm.properties.DMMODEL": model,
            "com.palm.properties.PRODoID": model,
            "com.palm.properties.boardType": boardType,
            "com.palm.properties.DMCARRIER": carrierName,
            "com.palm.properties.productLineVersion": productLineVersion,
            "com.palm.properties.buildName": buildName,
            "com.palm.properties.buildNumber": buildNumber,
        ]
        if let browserOsName { p["com.palm.properties.browserOsName"] = browserOsName }
        return p
    }

    /// webOS's device id: 40 hex digits, a SHA-1. Made once per install from the vendor id and
    /// kept, so it stays the same across launches and updates.
    static var nduid: String {
        let key = "LunaKit.nduid"
        if let id = UserDefaults.standard.string(forKey: key) { return id }
        let seed = "lunacy-nduid:" + (UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString)
        let id = Insecure.SHA1.hash(data: Data(seed.utf8)).map { String(format: "%02x", $0) }.joined()
        UserDefaults.standard.set(id, forKey: key)
        return id
    }

    /// A serial in HP's shape, derived from the device id (not a copy of a real one).
    static func serial(_ p: DeviceProfile) -> String {
        let alphabet = Array("0123456789ABCDEFGHJKLMNPQRSTUVWXYZ")
        let digest = Array(Insecure.SHA1.hash(data: Data(("lunacy-serial:" + p.name + ":" + nduid).utf8)))
        return p.serialPrefix + (0..<p.serialBodyLength).map { String(alphabet[Int(digest[$0]) % alphabet.count]) }.joined()
    }
}

/// JSON with its members in the order given, as a device's were.
func orderedJSON(_ pairs: [(String, Any)]) -> String {
    "{" + pairs.map { k, v in jsonString(k) + ":" + jsonValue(v) }.joined(separator: ",") + "}"
}

func jsonString(_ s: String) -> String {
    let data = try! JSONSerialization.data(withJSONObject: [s], options: [.fragmentsAllowed, .withoutEscapingSlashes])
    return String(data: data, encoding: .utf8)!.dropFirst().dropLast().description
}

func jsonValue(_ v: Any) -> String {
    switch v {
    case let b as Bool: return b ? "true" : "false"
    case let s as String: return jsonString(s)
    case let i as Int: return String(i)
    default:
        let data = (try? JSONSerialization.data(withJSONObject: v, options: [.fragmentsAllowed, .withoutEscapingSlashes])) ?? Data("null".utf8)
        return String(data: data, encoding: .utf8)!
    }
}

/// A JSON object as a string, for replies.
func jsonText(_ o: [String: Any]) -> String {
    let data = (try? JSONSerialization.data(withJSONObject: o, options: [.withoutEscapingSlashes])) ?? Data("{}".utf8)
    return String(data: data, encoding: .utf8)!
}
