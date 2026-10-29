// swift-tools-version:5.9
// iOSLuna: a toolkit for porting a single webOS app to iOS. See README.md.
import PackageDescription

let package = Package(
    name: "iOSLuna",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "LunaKit", targets: ["LunaKit"]),
    ],
    targets: [
        .target(
            name: "LunaKit",
            resources: [.copy("JS"), .copy("PrivacyInfo.xcprivacy")],
            linkerSettings: [.linkedLibrary("sqlite3")]
        ),
    ]
)
