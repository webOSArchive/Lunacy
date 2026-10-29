import Foundation
import SQLite3

/// The store behind Lunacy's WebSQL polyfill (lunacy/websql.js), as AndroidLuna's WebSql.kt
/// keeps it: one SQLite file per app and database name, the version in WebKit's own
/// `__WebKitDatabaseInfo__` table, and each transaction a native one held open across the
/// page's round trips. Statements run in order on a queue of their own, never the main thread.
final class WebSql {
    static let unknownErr = 0, databaseErr = 1, versionErr = 2, quotaErr = 4, syntaxErr = 5, constraintErr = 6
    private static let infoTable = "__WebKitDatabaseInfo__"
    private static let versionKey = "WebKitDatabaseVersionKey"
    private static let leadsWithRows = try! NSRegularExpression(pattern: "^\\s*(WITH|PRAGMA|EXPLAIN|SELECT|VALUES)\\b", options: .caseInsensitive)
    private static let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    private let dir: URL
    private let deliver: (Int, String) -> Void
    private let queue = DispatchQueue(label: "LunaKit.websql")
    private var open: [String: OpaquePointer] = [:]
    private var inTransaction: [String: Bool] = [:]  // name -> readOnly
    private var page = 0

    private struct Failure: Error { let code: Int; let message: String }

    init(appId: String, deliver: @escaping (Int, String) -> Void) {
        dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("websql/\(appId)", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        self.deliver = deliver
    }

    deinit { open.values.forEach { sqlite3_close_v2($0) } }

    func open(name: String, version: String, creationCallback: Bool) -> String {
        queue.sync {
            do { return try openOn(name: name, version: version, creationCallback: creationCallback) }
            catch let f as Failure { return orderedJSON([("error", f.message)]) }
            catch { return orderedJSON([("error", "unable to open database")]) }
        }
    }

    private func openOn(name: String, version: String, creationCallback: Bool) throws -> String {
        let db = try open[name] ?? openFile(name)
        open[name] = db
        try exec(db, "CREATE TABLE IF NOT EXISTS \(Self.infoTable) (key TEXT NOT NULL ON CONFLICT FAIL UNIQUE ON CONFLICT REPLACE, value TEXT NOT NULL ON CONFLICT FAIL)")
        guard let stored = try readVersion(db) else {
            let v = creationCallback ? "" : version
            try writeVersion(db, v)
            return orderedJSON([("version", v), ("created", true)])
        }
        if !version.isEmpty && version != stored { return orderedJSON([("mismatch", stored)]) }
        return orderedJSON([("version", stored), ("created", false)])
    }

    private func openFile(_ name: String) throws -> OpaquePointer {
        let safe = Data(name.utf8).base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        var db: OpaquePointer?
        guard sqlite3_open(dir.appendingPathComponent(safe + ".db").path, &db) == SQLITE_OK, let db else {
            throw Failure(code: Self.databaseErr, message: "unable to open database")
        }
        return db
    }

    func request(id: Int, request: String) {
        queue.async { [weak self] in
            guard let self else { return }
            let from = self.page
            let r: String
            if let j = (try? JSONSerialization.jsonObject(with: Data(request.utf8))) as? [String: Any] {
                r = self.handle(j)
            } else {
                r = Self.error(Self.unknownErr, "bad request")
            }
            DispatchQueue.main.async { if from == self.page { self.deliver(id, r) } }
        }
    }

    /// A new page: its predecessor's open transactions are rolled back.
    func reset() {
        queue.async { [weak self] in
            guard let self else { return }
            self.page += 1
            for name in self.inTransaction.keys { if let db = self.open[name] { _ = try? self.exec(db, "ROLLBACK") } }
            self.inTransaction = [:]
        }
    }

    private func handle(_ r: [String: Any]) -> String {
        let name = r["db"] as? String ?? ""
        guard let db = open[name] else { return Self.error(Self.databaseErr, "unable to open database") }
        switch r["op"] as? String {
        case "begin": return begin(name, db, readOnly: r["readOnly"] as? Bool == true, oldVersion: r["oldVersion"] as? String)
        case "exec": return execute(name, db, r["statements"] as? [[String: Any]] ?? [])
        case "commit": return commit(name, db, newVersion: r["newVersion"] as? String)
        case "rollback": return rollback(name, db)
        default: return Self.error(Self.unknownErr, "unknown operation")
        }
    }

    private func begin(_ name: String, _ db: OpaquePointer, readOnly: Bool, oldVersion: String?) -> String {
        if inTransaction[name] != nil { _ = rollback(name, db) }
        do { try exec(db, "BEGIN") } catch let f as Failure {
            return Self.error(Self.databaseErr, "unable to begin transaction (\(f.message))")
        } catch { return Self.error(Self.databaseErr, "unable to begin transaction") }
        inTransaction[name] = readOnly
        if let oldVersion, (try? readVersion(db)) ?? nil != oldVersion {
            _ = rollback(name, db)
            return Self.error(Self.versionErr, "current version of the database and `oldVersion` argument do not match")
        }
        return Self.ok
    }

    private func commit(_ name: String, _ db: OpaquePointer, newVersion: String?) -> String {
        guard inTransaction[name] != nil else { return Self.error(Self.databaseErr, "unable to commit transaction (none open)") }
        do {
            if let newVersion { try writeVersion(db, newVersion) }
            try exec(db, "COMMIT")
        } catch {
            inTransaction[name] = nil
            _ = try? exec(db, "ROLLBACK")
            return Self.error(Self.databaseErr, "unable to commit transaction (\((error as? Failure)?.message ?? "\(error)"))")
        }
        inTransaction[name] = nil
        return Self.ok
    }

    private func rollback(_ name: String, _ db: OpaquePointer) -> String {
        if inTransaction.removeValue(forKey: name) != nil { _ = try? exec(db, "ROLLBACK") }
        return Self.ok
    }

    private func execute(_ name: String, _ db: OpaquePointer, _ statements: [[String: Any]]) -> String {
        guard let readOnly = inTransaction[name] else { return Self.error(Self.databaseErr, "no transaction is open") }
        var results: [String] = []
        for s in statements {
            do {
                results.append(try statement(db, s["sql"] as? String ?? "", s["args"] as? [Any] ?? [], readOnly: readOnly))
            } catch let f as Failure {
                results.append(Self.error(f.code, f.message)); break
            } catch {
                results.append(Self.error(Self.unknownErr, "\(error)")); break
            }
        }
        return "{\"results\":[" + results.joined(separator: ",") + "]}"
    }

    private func statement(_ db: OpaquePointer, _ sql: String, _ args: [Any], readOnly: Bool) throws -> String {
        let query = contains(Self.leadsWithRows, sql)
        if readOnly && !query { throw Failure(code: Self.syntaxErr, message: "not authorized") }
        let expected = Self.parameterCount(sql)
        if expected >= 0 && expected != args.count {
            throw Failure(code: Self.syntaxErr, message: "number of '?'s in statement string does not match argument count")
        }
        var st: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &st, nil) == SQLITE_OK, let st else {
            throw Failure(code: Self.syntaxErr, message: Self.message(db))
        }
        defer { sqlite3_finalize(st) }
        for (i, v) in args.enumerated() {
            let n = Int32(i + 1)
            switch v {
            case is NSNull: sqlite3_bind_null(st, n)
            case let num as NSNumber where CFGetTypeID(num) == CFBooleanGetTypeID(): sqlite3_bind_int64(st, n, num.boolValue ? 1 : 0)
            case let num as NSNumber:
                if CFNumberIsFloatType(num) && num.doubleValue != num.doubleValue.rounded() { sqlite3_bind_double(st, n, num.doubleValue) }
                else if CFNumberIsFloatType(num) && abs(num.doubleValue) >= 9.2e18 { sqlite3_bind_double(st, n, num.doubleValue) }
                else { sqlite3_bind_int64(st, n, num.int64Value) }
            default: sqlite3_bind_text(st, n, "\(v)", -1, Self.transient)
            }
        }
        var columns: [String] = []
        for c in 0..<sqlite3_column_count(st) { columns.append(String(cString: sqlite3_column_name(st, c))) }
        var rows: [String] = []
        while true {
            let rc = sqlite3_step(st)
            if rc == SQLITE_DONE { break }
            guard rc == SQLITE_ROW else {
                let code = (rc & 0xff) == SQLITE_CONSTRAINT ? Self.constraintErr : (rc & 0xff) == SQLITE_FULL ? Self.quotaErr : Self.databaseErr
                throw Failure(code: code, message: Self.message(db))
            }
            var row: [String] = []
            for c in 0..<sqlite3_column_count(st) {
                switch sqlite3_column_type(st, c) {
                case SQLITE_NULL: row.append("null")
                case SQLITE_INTEGER: row.append(String(sqlite3_column_int64(st, c)))
                case SQLITE_FLOAT: row.append(jsonValue(sqlite3_column_double(st, c)))
                case SQLITE_BLOB:
                    let n = Int(sqlite3_column_bytes(st, c))
                    let bytes = sqlite3_column_blob(st, c).map { Data(bytes: $0, count: n) } ?? Data()
                    row.append(jsonString(String(data: bytes, encoding: .isoLatin1) ?? ""))
                default: row.append(jsonString(String(cString: sqlite3_column_text(st, c))))
                }
            }
            rows.append("[" + row.joined(separator: ",") + "]")
        }
        let changed = query ? 0 : Int(sqlite3_changes(db))
        let insertId = !query && changed > 0 ? String(sqlite3_last_insert_rowid(db)) : "null"
        return "{\"columns\":" + jsonValue(columns) + ",\"rows\":[" + rows.joined(separator: ",") +
            "],\"rowsAffected\":\(changed),\"insertId\":" + insertId + "}"
    }

    private func exec(_ db: OpaquePointer, _ sql: String) throws {
        guard sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK else { throw Failure(code: Self.databaseErr, message: Self.message(db)) }
    }

    private func readVersion(_ db: OpaquePointer) throws -> String? {
        var st: OpaquePointer?
        guard sqlite3_prepare_v2(db, "SELECT value FROM \(Self.infoTable) WHERE key = ?", -1, &st, nil) == SQLITE_OK, let st else {
            throw Failure(code: Self.databaseErr, message: Self.message(db))
        }
        defer { sqlite3_finalize(st) }
        sqlite3_bind_text(st, 1, Self.versionKey, -1, Self.transient)
        return sqlite3_step(st) == SQLITE_ROW ? String(cString: sqlite3_column_text(st, 0)) : nil
    }

    private func writeVersion(_ db: OpaquePointer, _ version: String) throws {
        var st: OpaquePointer?
        guard sqlite3_prepare_v2(db, "INSERT INTO \(Self.infoTable) (key, value) VALUES (?, ?)", -1, &st, nil) == SQLITE_OK, let st else {
            throw Failure(code: Self.databaseErr, message: Self.message(db))
        }
        defer { sqlite3_finalize(st) }
        sqlite3_bind_text(st, 1, Self.versionKey, -1, Self.transient)
        sqlite3_bind_text(st, 2, version, -1, Self.transient)
        guard sqlite3_step(st) == SQLITE_DONE else { throw Failure(code: Self.databaseErr, message: Self.message(db)) }
    }

    private static func message(_ db: OpaquePointer) -> String { String(cString: sqlite3_errmsg(db)) }
    private static func error(_ code: Int, _ message: String) -> String { "{\"error\":" + orderedJSON([("code", code), ("message", message)]) + "}" }
    private static let ok = "{\"ok\":true}"

    /// The number of `?` parameters, or -1 when the statement names or numbers them.
    static func parameterCount(_ sql: String) -> Int {
        let s = Array(sql)
        var n = 0, i = 0
        while i < s.count {
            let ch = s[i]
            if ch == "'" || ch == "\"" || ch == "`" {
                i += 1
                while i < s.count { if s[i] == ch { if i + 1 < s.count && s[i + 1] == ch { i += 1 } else { break } }; i += 1 }
            } else if ch == "[" { while i < s.count && s[i] != "]" { i += 1 } }
            else if ch == "-" && i + 1 < s.count && s[i + 1] == "-" { while i < s.count && s[i] != "\n" { i += 1 } }
            else if ch == "/" && i + 1 < s.count && s[i + 1] == "*" {
                i += 2
                while i + 1 < s.count && !(s[i] == "*" && s[i + 1] == "/") { i += 1 }
                i += 1
            } else if ch == "?" {
                if i + 1 < s.count && s[i + 1].isNumber { return -1 }
                n += 1
            } else if (ch == ":" || ch == "@" || ch == "$") && i + 1 < s.count && (s[i + 1].isLetter || s[i + 1] == "_") { return -1 }
            i += 1
        }
        return n
    }
}
