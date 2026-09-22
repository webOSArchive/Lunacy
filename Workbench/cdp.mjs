// Talks to a Lunacy page over the WebView's DevTools protocol (Node 22+, for fetch and WebSocket).
// Run it through cdp.sh, which forwards the port first.
//
//   node cdp.mjs eval '<js expression>' [page]    value of the expression (returnByValue)
//   node cdp.mjs send <Method> '<params json>' [page]   one raw DevTools method
//   node cdp.mjs inject '<js source>' [page]      runs the source before every page load, reloads
//   node cdp.mjs list                             the pages
//
// [page] is a substring of the page URL, usually the app id. Port: CDP_PORT, default 9223.
const port = process.env.CDP_PORT || 9223;
const [cmd, a, b, c] = process.argv.slice(2);

const pages = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
if (cmd === "list") {
  for (const p of pages) console.log(p.type, p.url);
  process.exit(0);
}
const match = cmd === "send" ? c : b;
const page = pages.find(p => p.type === "page" && p.url.includes(match || ""));
if (!page) { console.error("no page matching", match, pages.map(p => p.url)); process.exit(1); }

const ws = new WebSocket(page.webSocketDebuggerUrl);
let n = 0;
const waiting = new Map();
const send = (method, params = {}) => new Promise(done => {
  waiting.set(++n, done);
  ws.send(JSON.stringify({ id: n, method, params }));
});
ws.onmessage = m => {
  const r = JSON.parse(m.data);
  if (r.id && waiting.has(r.id)) { waiting.get(r.id)(r); waiting.delete(r.id); }
};
await new Promise(ok => { ws.onopen = ok; });

if (cmd === "eval") {
  const r = await send("Runtime.evaluate", { expression: a, returnByValue: true, awaitPromise: true });
  const v = r.result?.result;
  console.log(r.result?.exceptionDetails ? "THROWS " + v?.description : JSON.stringify(v?.value));
} else if (cmd === "send") {
  console.log(JSON.stringify(await send(a, JSON.parse(b || "{}"))));
} else if (cmd === "inject") {
  await send("Page.enable");
  await send("Page.addScriptToEvaluateOnNewDocument", { source: a });
  await send("Page.reload");
  console.log("injected and reloaded");
} else {
  console.error("usage: cdp.mjs eval|send|inject|list ...");
  process.exit(1);
}
process.exit(0);
