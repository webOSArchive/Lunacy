// The log window's side of main.js: the lines so far, each new one, and clearing them.
"use strict";
const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("lunadeLog", {
	history: () => ipcRenderer.invoke("lunade:log-history"),
	clear: () => ipcRenderer.invoke("lunade:log-clear"),
	onLine: (fn) => ipcRenderer.on("lunade:log-line", (e, line) => fn(line)),
});
