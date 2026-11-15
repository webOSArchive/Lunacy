// The shell page's reach into Luna DE (main.js): the launcher's pages, launching, the cards'
// live views (up, hidden, snapshots, closed, popped out), and what the main process tells it.
"use strict";
const { contextBridge, ipcRenderer } = require("electron");
const on = (channel) => (cb) => ipcRenderer.on(channel, (e, data) => cb(data));
contextBridge.exposeInMainWorld("lunade", {
	shell: () => ipcRenderer.invoke("lunade:shell"),
	launch: (entry) => ipcRenderer.invoke("lunade:launch", entry),
	maximize: (id) => ipcRenderer.invoke("lunade:maximize", id),
	cardView: () => ipcRenderer.invoke("lunade:cardview"),
	snapshot: (id) => ipcRenderer.invoke("lunade:snapshot", id),
	close: (id) => ipcRenderer.invoke("lunade:close", id),
	popOut: (id) => ipcRenderer.invoke("lunade:popout", id),
	devTools: (id) => ipcRenderer.invoke("lunade:devtools", id),
	reload: (id) => ipcRenderer.invoke("lunade:reload", id),
	ready: () => ipcRenderer.send("lunade:ready"),
	onHome: on("lunade:home"),
	onCardAdded: on("lunade:card-added"),
	onCardReady: on("lunade:card-ready"),
	onCardRemoved: on("lunade:card-removed"),
	onCardTitle: on("lunade:card-title"),
	onShow: on("lunade:show"),
});
