import Foundation
import os

/// LunaKit's log, and every page's console and palm log with it, in the unified log as public
/// text. NSLog's arguments are `<private>` in a log collected off a device, which left a log
/// from the road with nothing of the app's own in it (2026-10-29). Secrets are blanked first,
/// as AndroidLuna's Bus.redact blanks them in Android's log: tokens and passwords in JSON and
/// in query strings, and email addresses, which Apollo logs as the account's username.
private let logger = Logger(subsystem: "org.webosarchive.lunakit", category: "LunaKit")

func lunaLog(_ format: String, _ args: CVarArg...) {
    let text = redact(String(format: format, arguments: args))
    logger.log("\(text, privacy: .public)")
}

private let secretJSON = try! NSRegularExpression(
    pattern: "(\"(?:[A-Za-z]*password|passwd|pin|secret|[A-Za-z]*[Tt]oken|client_secret|username)\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"",
    options: .caseInsensitive)
private let secretQuery = try! NSRegularExpression(
    pattern: "([?&](?:auth_token|token|password|[A-Za-z]*Token)=)[^&\\s\"]*", options: .caseInsensitive)
private let email = try! NSRegularExpression(pattern: "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

func redact(_ s: String) -> String {
    var out = s
    for (re, template) in [(secretJSON, "$1\"…\""), (secretQuery, "$1…"), (email, "…@…")] {
        out = re.stringByReplacingMatches(in: out, range: NSRange(out.startIndex..., in: out), withTemplate: template)
    }
    return out
}
