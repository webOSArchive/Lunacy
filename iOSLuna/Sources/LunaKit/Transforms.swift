import Foundation

// Lunacy's global serve-time transforms (AndroidLuna's AppServer.kt: HtmlTransforms,
// JsTransforms, CssTransforms), ported as they are. Each is one mechanical rule applied to
// every file of its type; none names an app. The reasons are recorded at length in the Kotlin
// originals and Lunacy's Docs/fix-log.md; a change to the rule belongs there first.

enum HtmlTransforms {
    private static let void: Set<String> = [
        "area", "base", "basefont", "bgsound", "br", "col", "command", "embed", "frame", "hr",
        "img", "input", "isindex", "keygen", "link", "meta", "param", "source", "track", "wbr"]
    private static let rawText: Set<String> = ["script", "style", "textarea", "title"]

    /// `<script src="x.js" />` closes the element, as webOS's pre-HTML5 tokenizer did.
    static func selfClosingTags(_ html: String) -> String {
        guard html.contains("/>") else { return html }
        let s = Array(html.utf16)
        var out = [UInt16](); out.reserveCapacity(s.count + 64)
        func isWord(_ c: UInt16) -> Bool {
            (c >= 48 && c <= 57) || (c >= 65 && c <= 90) || (c >= 97 && c <= 122) || c == 45 || c > 127
        }
        func isSpace(_ c: UInt16) -> Bool { c == 32 || c == 9 || c == 10 || c == 13 || c == 12 }
        func starts(_ p: String, at i: Int) -> Bool {
            let u = Array(p.utf16)
            guard i + u.count <= s.count else { return false }
            for k in 0..<u.count where s[i + k] != u[k] { return false }
            return true
        }
        func find(_ p: String, from: Int) -> Int? {
            let u = Array(p.utf16)
            guard !u.isEmpty, from <= s.count - u.count else { return nil }
            var i = from
            while i <= s.count - u.count { if starts(p, at: i) { return i }; i += 1 }
            return nil
        }
        /// `</name` followed by a non-word character, ignoring case.
        func findClose(_ name: String, from: Int) -> Int? {
            let u = Array(("</" + name).lowercased().utf16)
            var i = from
            while i + u.count <= s.count {
                var match = true
                for k in 0..<u.count {
                    var c = s[i + k]; if c >= 65 && c <= 90 { c += 32 }
                    if c != u[k] { match = false; break }
                }
                if match && (i + u.count == s.count || !isWord(s[i + u.count])) { return i }
                i += 1
            }
            return nil
        }
        var i = 0
        while true {
            guard let lt = s[i...].firstIndex(of: 60) else { out += s[i...]; break }  // '<'
            out += s[i..<lt]
            if starts("<!--", at: lt) {
                let stop = find("-->", from: lt + 4).map { $0 + 3 } ?? s.count
                out += s[lt..<stop]; i = stop; continue
            }
            var j = lt + 1
            while j < s.count && isWord(s[j]) { j += 1 }
            let name = String(utf16CodeUnits: Array(s[(lt + 1)..<j]), count: j - lt - 1).lowercased()
            if name.isEmpty { out.append(60); i = lt + 1; continue }
            var k = j
            var quote: UInt16 = 0
            while k < s.count {
                let c = s[k]
                if quote != 0 { if c == quote { quote = 0 } }
                else if c == 34 || c == 39 { quote = c }
                else if c == 62 { break }  // '>'
                k += 1
            }
            if k >= s.count { out += s[lt...]; i = s.count; continue }
            var slash = k - 1
            while slash > j && isSpace(s[slash]) { slash -= 1 }
            let closes = slash >= j && s[slash] == 47  // '/'
            if closes && !void.contains(name) {
                out += s[lt..<slash]; out += Array("></\(name)>".utf16)
            } else {
                out += s[lt...k]
            }
            i = k + 1
            if rawText.contains(name) && !closes {
                let stop = findClose(name, from: i) ?? s.count
                out += s[i..<stop]; i = stop
            }
        }
        return String(utf16CodeUnits: out, count: out.count)
    }
}

enum JsTransforms {
    private static let directive = try! NSRegularExpression(
        pattern: "((?:^|[{};>])(?:\\s|/\\*[\\s\\S]*?\\*/|//[^\\n]*\\n)*)([\"'])use strict\\2")

    /// `"use strict"` does nothing, as on the device (WebKit 534.6 predates strict mode).
    static func sloppyMode(_ js: String) -> String {
        guard js.contains("use strict") else { return js }
        return directive.stringByReplacingMatches(
            in: js, range: NSRange(js.startIndex..., in: js), withTemplate: "$1$2not strict$2")
    }
}

enum CssTransforms {
    private static let rule = try! NSRegularExpression(pattern: "\\{[^{}]*\\}")
    private static let borderImage = try! NSRegularExpression(
        pattern: "-webkit-border-image\\s*:(?!\\s*none)", options: .caseInsensitive)
    private static let mouseTarget = try! NSRegularExpression(
        pattern: "-webkit-palm-mouse-target\\s*:\\s*ignore", options: .caseInsensitive)
    private static let selectorRule = try! NSRegularExpression(pattern: "([^{}]+)\\{([^{}]*)\\}")

    static func apply(_ css: String) -> String { mouseTargetIgnore(borderImageNeedsStyle(css)) }

    /// webOS's `-webkit-palm-mouse-target: ignore` as `pointer-events: none` on the element alone.
    static func mouseTargetIgnore(_ css: String) -> String {
        replace(selectorRule, in: css) { m in
            let body = m[2]
            guard contains(mouseTarget, body) else { return m[0] }
            let children = m[1].split(separator: ",").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) + " > *" }
                .joined(separator: ",")
            var trimmed = body
            while let last = trimmed.last, last.isWhitespace { trimmed.removeLast() }
            if trimmed.hasSuffix(";") { trimmed.removeLast() }
            return m[1] + "{" + trimmed + ";pointer-events:none}" + children + "{pointer-events:auto}"
        }
    }

    /// 2011 WebKit drew -webkit-border-image whatever the border-style.
    static func borderImageNeedsStyle(_ css: String) -> String {
        replace(rule, in: css) { m in
            contains(borderImage, m[0]) ? String(m[0].dropLast()) + ";border-style:solid;border-color:transparent}" : m[0]
        }
    }
}

/// A regex replacement whose replacement is computed from the match's groups.
func replace(_ re: NSRegularExpression, in s: String, _ f: ([String]) -> String) -> String {
    let ns = s as NSString
    var out = ""
    var last = 0
    for m in re.matches(in: s, range: NSRange(location: 0, length: ns.length)) {
        out += ns.substring(with: NSRange(location: last, length: m.range.location - last))
        let groups = (0..<m.numberOfRanges).map { i -> String in
            let r = m.range(at: i)
            return r.location == NSNotFound ? "" : ns.substring(with: r)
        }
        out += f(groups)
        last = m.range.location + m.range.length
    }
    out += ns.substring(from: last)
    return out
}

func contains(_ re: NSRegularExpression, _ s: String) -> Bool {
    re.firstMatch(in: s, range: NSRange(location: 0, length: (s as NSString).length)) != nil
}
