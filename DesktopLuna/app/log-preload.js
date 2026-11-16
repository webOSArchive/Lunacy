// The log window's side of main.js: the lines so far, each new one, and clearing them.
"use strict";
const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("lunaLog", {
	history: () => ipcRenderer.invoke("luna-de:log-history"),
	clear: () => ipcRenderer.invoke("luna-de:log-clear"),
	onLine: (fn) => ipcRenderer.on("luna-de:log-line", (e, line) => fn(line)),
});
