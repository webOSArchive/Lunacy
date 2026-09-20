// db8probe.sh's calls, run in a Lunacy page through PalmServiceBridge (evaluate it with
// Workbench/cdp.sh in the probe app, then read window.__db8out). Prints the same "== label" blocks.
(function () {
	var K = "org.webosarchive.lunacy.probe.test:1", A = "org.webosarchive.lunacy.probe", out = [], ID = null;
	function call(method, params, cb) {
		var b = new PalmServiceBridge(), done = false;
		b.onservicecallback = function (r) { if (!done) { done = true; cb(r); } };
		b.call("palm://com.palm.db/" + method, JSON.stringify(params));
	}
	var q = function (x) { x.from = K; return { query: x }; };
	var steps = [
		["delKind (cleanup)", "delKind", { id: K }],
		["putKind", "putKind", { id: K, owner: A, indexes: [{ name: "byName", props: [{ name: "name" }] }, { name: "byAgeName", props: [{ name: "age" }, { name: "name" }] }] }],
		["putKind wrong owner", "putKind", { id: "org.other.kind:1", owner: "org.other" }],
		["put 3", "put", { objects: [{ _kind: K, name: "carol", age: 30 }, { _kind: K, name: "alice", age: 25 }, { _kind: K, name: "bob", age: 30, tags: ["x", "y"] }] }, function (r) { ID = JSON.parse(r).results[2].id; }],
		["get by id", "get", function () { return { ids: [ID] }; }],
		["merge by id", "merge", function () { return { objects: [{ _id: ID, nick: "cc" }] }; }],
		["get after merge", "get", function () { return { ids: [ID] }; }],
		["put replace by id", "put", function () { return { objects: [{ _id: ID, _kind: K, name: "carol", age: 33 }] }; }],
		["get after put", "get", function () { return { ids: [ID] }; }],
		["del by id", "del", function () { return { ids: [ID] }; }],
		["del by id purge", "del", function () { return { ids: [ID], purge: true }; }],
		["put no kind", "put", { objects: [{ name: "x" }] }],
		["put unknown kind", "put", { objects: [{ _kind: "org.nope:1", name: "x" }] }],
		["find all", "find", q({})],
		["find orderBy name", "find", q({ orderBy: "name" })],
		["find orderBy name desc limit 2", "find", q({ orderBy: "name", desc: true, limit: 2 })],
		["find where name = bob", "find", q({ where: [{ prop: "name", op: "=", val: "bob" }] })],
		["find where name prefix", "find", q({ where: [{ prop: "name", op: "%", val: "a" }] })],
		["find where age = 30 orderBy name", "find", q({ where: [{ prop: "age", op: "=", val: 30 }], orderBy: "name" })],
		["find where age > 26", "find", q({ where: [{ prop: "age", op: ">", val: 26 }] })],
		["find where name in list", "find", q({ where: [{ prop: "name", op: "=", val: ["alice", "bob"] }] })],
		["find no index (tags)", "find", q({ where: [{ prop: "tags", op: "=", val: "x" }] })],
		["find orderBy without index", "find", q({ orderBy: "age" })],
		["find select", "find", q({ select: ["name"], orderBy: "name" })],
		["find count", "find", (function () { var x = q({}); x.count = true; return x; })()],
		["find limit 1 (page)", "find", q({ orderBy: "name", limit: 1 })],
		["find unknown kind", "find", { query: { from: "org.nope:1" } }],
		["find filter (op on non-index)", "find", q({ where: [{ prop: "name", op: "=", val: "bob" }], filter: [{ prop: "age", op: "=", val: 30 }] })],
		["search", "search", q({ where: [{ prop: "age", op: "=", val: 30 }] })],
		["get missing", "get", { ids: ["nope"] }],
		["del query age 25", "del", q({ where: [{ prop: "name", op: "=", val: "alice" }] })],
		["find after del", "find", q({ orderBy: "name" })],
		["find incDel", "find", q({ orderBy: "name", incDel: true })],
		["merge query", "merge", (function () { var x = q({ where: [{ prop: "name", op: "=", val: "bob" }] }); x.props = { age: 31 }; return x; })()],
		["reserveIds", "reserveIds", { count: 2 }],
		["batch", "batch", { operations: [{ method: "put", params: { objects: [{ _kind: K, name: "dave", age: 40 }] } }, { method: "find", params: q({ orderBy: "name" }) }] }],
		["getProperties", "getProperties", { id: K }],
		["bad method", "nosuchmethod", {}],
		["find bad json query", "find", { query: {} }],
		["delKind", "delKind", { id: K }],
		["find after delKind", "find", q({})]
	];
	(function next(i) {
		if (i >= steps.length) { window.__db8out = out.join("\n"); return; }
		var s = steps[i], params = typeof s[2] === "function" ? s[2]() : s[2];
		call(s[1], params, function (r) { out.push("== " + s[0]); out.push(r); if (s[3]) { try { s[3](r); } catch (e) {} } next(i + 1); });
	})(0);
	return "running";
})()
