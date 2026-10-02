// Lunacy WebSQL polyfill. ES5 only: must run on Chromium 37, where it does nothing.
//
// Mojo apps keep their data in WebSQL (Mojo.Depot is a table layout over openDatabase), and
// many Enyo 1 apps call openDatabase themselves. Chromium removed WebSQL in version 119, so
// on a WebView from then on `openDatabase` is undefined and an app's first store of anything
// throws: Apollo logs "Transaction Error: openDatabase is not defined" and sits on its splash.
// Every WebView an Android 5 device can run still has it natively, and this file steps aside
// there (compat.js wraps the native one for the two things WebKit 534.6 did differently).
//
// This is the API as the device had it (WebKit 534.6, measured with Workbench/probe 0.1.0 and
// the standard's processing model): openDatabase with an arity of 0 and a two-argument form,
// transactions that run one at a time per database, statements whose callbacks may queue
// more statements into the same transaction, an error callback that keeps the transaction
// alive by returning false, SQLite's own wording in SQLError.message, and changeVersion with
// its preflight. Storage is native (WebSql.kt): one SQLite file per app and database name,
// so an app's data is the app's whatever origin its windows are on, and the statements run
// on a thread of their own, as WebKit's did. The page talks to it like the network shim: a
// request id, an answer delivered through evaluateJavascript, and the result fetched back.
(function () {
	var N = window.LunacyNative;
	if (window.openDatabase || !N || !N.sqlOpen) { return; }

	var UNKNOWN_ERR = 0, DATABASE_ERR = 1, VERSION_ERR = 2, QUOTA_ERR = 4, SYNTAX_ERR = 5;
	var CALLBACK_THREW = "the statement callback raised an exception or statement error callback did not return false";

	// SQLError and SQLException, with the constants WebKit put on each instance and the class.
	function SQLError(code, message) { this.code = code; this.message = message; }
	var CODES = { UNKNOWN_ERR: 0, DATABASE_ERR: 1, VERSION_ERR: 2, TOO_LARGE_ERR: 3, QUOTA_ERR: 4, SYNTAX_ERR: 5, CONSTRAINT_ERR: 6, TIMEOUT_ERR: 7 };
	for (var k in CODES) { SQLError[k] = CODES[k]; SQLError.prototype[k] = CODES[k]; }
	function SQLException(code, message) { SQLError.call(this, code, message); }
	SQLException.prototype = SQLError.prototype;
	window.SQLError = SQLError;
	window.SQLException = SQLException;

	// The exceptions openDatabase and executeSql throw synchronously: DOMExceptions on the
	// device, which Chromium 37 doesn't let a page construct.
	function domException(code, name, message) {
		var e = new Error(message);
		e.code = code; e.name = name;
		return e;
	}
	var INVALID_STATE_ERR = 11, INVALID_ACCESS_ERR = 15;

	// Request ids come from one counter shared by a window's same-origin frames, and an
	// answer is handed to whichever frame is waiting for it: native answers through
	// evaluateJavascript, which only runs in the main frame (as net.js).
	var shared = (function () {
		try {
			var t = window.top;
			if (!t.__lunacySqlPending) { t.__lunacySqlPending = {}; t.__lunacySqlId = 0; }
			return t;
		} catch (e) { return null; }
	})();
	var holder = shared || window;
	if (!holder.__lunacySqlPending) { holder.__lunacySqlPending = {}; holder.__lunacySqlId = 0; }

	function request(body, cb) {
		var id = ++holder.__lunacySqlId;
		holder.__lunacySqlPending[id] = cb;
		N.sqlRequest(id, JSON.stringify(body));
	}
	window.__lunacySqlDone = function (id) {
		var pending = holder.__lunacySqlPending, cb = pending[id];
		if (!cb) { return; }
		delete pending[id];
		var r;
		try { r = JSON.parse(N.sqlResult(id)); } catch (e) { r = { error: { code: UNKNOWN_ERR, message: String(e) } }; }
		cb(r);
	};

	// Handlers run as the browser runs them: an exception in one is reported with its own
	// stack, and still counts as the failure the standard says it is.
	function report(err) { console.error("Uncaught " + (err && err.stack || err)); }

	// ---- the result set ----

	function RowList(columns, rows) {
		this.__columns = columns; this.__rows = rows; this.__items = [];
		this.length = rows.length;
	}
	RowList.prototype.item = function (i) {
		i = Number(i);
		if (!(i >= 0 && i < this.length)) { return null; }
		var o = this.__items[i];
		if (!o) {
			o = {};
			var row = this.__rows[i], cols = this.__columns;
			for (var c = 0; c < cols.length; c++) { o[cols[c]] = row[c]; }
			this.__items[i] = o;
		}
		return o;
	};

	function ResultSet(r) {
		this.rows = new RowList(r.columns || [], r.rows || []);
		this.rowsAffected = r.rowsAffected || 0;
		this.__insertId = r.insertId;
	}
	// insertId throws on a statement that inserted nothing, as WebKit's did.
	Object.defineProperty(ResultSet.prototype, "insertId", {
		get: function () {
			if (this.__insertId === null || this.__insertId === undefined) {
				throw domException(INVALID_ACCESS_ERR, "InvalidAccessError", "INVALID_ACCESS_ERR: DOM Exception 15");
			}
			return this.__insertId;
		}
	});

	// How WebKit bound a statement's arguments: null and undefined as NULL, numbers as
	// numbers, everything else as its string.
	function bindable(v) {
		if (v === null || v === undefined) { return null; }
		if (typeof v === "number") { return isFinite(v) ? v : null; }
		return String(v);
	}

	// ---- transactions ----

	// One transaction's queue of statements and the SQLTransaction the app sees.
	function Transaction(db, spec) {
		this.db = db; this.spec = spec;
		this.queue = [];
		this.active = false;    // between begin and commit/rollback
		this.allowed = false;   // inside one of the app's callbacks: executeSql is legal
		this.tx = new SQLTransaction(this);
	}

	function SQLTransaction(t) { this.__t = t; }
	SQLTransaction.prototype.executeSql = function (sql, args, ok, fail) {
		var t = this.__t;
		if (!t.active || !t.allowed) {
			throw domException(INVALID_STATE_ERR, "InvalidStateError", "INVALID_STATE_ERR: DOM Exception 11");
		}
		var list = [];
		if (args !== null && args !== undefined && typeof args === "object" && typeof args.length === "number") {
			for (var i = 0; i < args.length; i++) { list.push(bindable(args[i])); }
		}
		t.queue.push({ sql: String(sql), args: list, ok: typeof ok === "function" ? ok : null, fail: typeof fail === "function" ? fail : null });
	};

	// Runs one of the app's callbacks with executeSql allowed; returns [threw, value].
	function invoke(t, fn, a, b) {
		t.allowed = true;
		try { return [false, fn.call(null, a, b)]; }
		catch (e) { report(e); return [true, e]; }
		finally { t.allowed = false; }
	}

	function run(t) {
		var spec = t.spec, db = t.db;
		var begin = { op: "begin", db: db.__name, readOnly: !!spec.readOnly };
		if (spec.oldVersion !== undefined) { begin.oldVersion = spec.oldVersion; }
		request(begin, function (r) {
			if (r.error) { return finish(t, r.error); }
			t.active = true;
			if (spec.callback) {
				var res = invoke(t, spec.callback, t.tx);
				if (res[0]) {
					t.queue = [];
					return finish(t, { code: UNKNOWN_ERR, message: "the SQLTransactionCallback was null or threw an exception" });
				}
			}
			step(t);
		});
	}

	// The processing model: run what is queued, hand each result to its callback (which
	// may queue more), and commit once nothing is left.
	function step(t) {
		if (!t.queue.length) {
			var commit = { op: "commit", db: t.db.__name };
			if (t.spec.newVersion !== undefined) { commit.newVersion = t.spec.newVersion; }
			t.active = false;
			return request(commit, function (r) {
				if (r.error) { return finish(t, r.error, true); }
				if (t.spec.newVersion !== undefined) { setVersion(t.db.__name, t.spec.newVersion); }
				if (t.spec.success) { try { t.spec.success(); } catch (e) { report(e); } }
				next(t.db);
			});
		}
		var batch = t.queue;
		t.queue = [];
		request({ op: "exec", db: t.db.__name, statements: batch.map(function (s) { return { sql: s.sql, args: s.args }; }) }, function (r) {
			if (r.error) { return finish(t, r.error); }
			var results = r.results || [];
			for (var i = 0; i < results.length; i++) {
				var s = batch[i], res = results[i];
				if (res.error) {
					if (!s.fail) { return finish(t, res.error); }
					var e = invoke(t, s.fail, t.tx, new SQLError(res.error.code, res.error.message));
					if (e[0] || e[1] !== false) { return finish(t, { code: UNKNOWN_ERR, message: CALLBACK_THREW }); }
				} else if (s.ok) {
					var o = invoke(t, s.ok, t.tx, new ResultSet(res));
					if (o[0]) { return finish(t, { code: UNKNOWN_ERR, message: CALLBACK_THREW }); }
				}
			}
			// Statements native didn't reach (those after a failure the app forgave) go
			// first, ahead of what the callbacks queued, to keep the app's order.
			if (results.length < batch.length) { t.queue = batch.slice(results.length).concat(t.queue); }
			step(t);
		});
	}

	// The transaction failed: roll back whatever is open, then tell the app.
	function finish(t, error, ended) {
		var tell = function () {
			t.active = false;
			if (t.spec.error) { try { t.spec.error(new SQLError(error.code, error.message)); } catch (e) { report(e); } }
			next(t.db);
		};
		if (t.active && !ended) { request({ op: "rollback", db: t.db.__name }, tell); } else { tell(); }
	}

	// ---- databases ----

	// Transactions run one at a time per database name, however many Database objects a
	// page holds for it; the name is the store's key in native.
	var stores = {};
	function store(name) {
		return stores[name] || (stores[name] = { queue: [], running: null, objects: [] });
	}
	function enqueue(db, spec) {
		var s = store(db.__name), t = new Transaction(db, spec);
		s.queue.push(t);
		if (!s.running) { next(db); }
	}
	function next(db) {
		var s = store(db.__name);
		s.running = s.queue.shift() || null;
		if (s.running) { run(s.running); }
	}
	function setVersion(name, version) {
		var objects = store(name).objects;
		for (var i = 0; i < objects.length; i++) { objects[i].version = version; }
	}

	function Database(name, version) { this.__name = name; this.version = version; }
	function callbacks(args, from) {
		// transaction(callback, errorCallback, successCallback); the first is required.
		var cb = args[from];
		if (typeof cb !== "function") { throw new TypeError("The callback provided as parameter " + (from + 1) + " is not a function."); }
		return { callback: cb, error: typeof args[from + 1] === "function" ? args[from + 1] : null, success: typeof args[from + 2] === "function" ? args[from + 2] : null };
	}
	Database.prototype.transaction = function () { enqueue(this, callbacks(arguments, 0)); };
	Database.prototype.readTransaction = function () {
		var spec = callbacks(arguments, 0);
		spec.readOnly = true;
		enqueue(this, spec);
	};
	Database.prototype.changeVersion = function (oldVersion, newVersion, cb, fail, ok) {
		enqueue(this, {
			callback: typeof cb === "function" ? cb : null,
			error: typeof fail === "function" ? fail : null,
			success: typeof ok === "function" ? ok : null,
			oldVersion: oldVersion === null || oldVersion === undefined ? "" : String(oldVersion),
			newVersion: newVersion === null || newVersion === undefined ? "" : String(newVersion)
		});
	};

	// Declared with no parameters: the device's openDatabase.length is 0, and a name and a
	// version are all it needs (Docs/mojo.md). The display name and size hint only ever fed
	// quota prompts webOS never showed.
	window.openDatabase = function openDatabase() {
		var name = String(arguments[0] === undefined ? "" : arguments[0]);
		var version = arguments[1] === null || arguments[1] === undefined ? "" : String(arguments[1]);
		var creation = typeof arguments[4] === "function" ? arguments[4] : null;
		var r;
		try { r = JSON.parse(N.sqlOpen(name, version, location.host, !!creation)); }
		catch (e) { r = { error: String(e) }; }
		if (r.error) {
			throw domException(INVALID_STATE_ERR, "InvalidStateError", "INVALID_STATE_ERR: DOM Exception 11: " + r.error);
		}
		if (r.mismatch !== undefined) {
			throw domException(INVALID_STATE_ERR, "InvalidStateError",
				"INVALID_STATE_ERR: DOM Exception 11: unable to open database, version mismatch, '" + version +
				"' does not match the currentVersion of '" + r.mismatch + "'");
		}
		var db = new Database(name, r.version);
		store(name).objects.push(db);
		// A brand-new database with a creation callback starts at version "" and the
		// callback, run before anything else, is where the app sets it up.
		if (r.created && creation) { setTimeout(function () { try { creation(db); } catch (e) { report(e); } }, 0); }
		return db;
	};
})();
