// The launcher's only reach into Luna DE: the apps, and opening one.
"use strict";
const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("lunade", {
	apps: () => ipcRenderer.invoke("lunade:apps"),
	launch: (entry) => ipcRenderer.invoke("lunade:launch", entry),
});
