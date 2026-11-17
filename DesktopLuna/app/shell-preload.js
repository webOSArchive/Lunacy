// The shell page's reach into Luna DE (main.js): the launcher's pages, launching, the cards'
// live views (up, hidden, snapshots, closed, popped out), and what the main process tells it.
"use strict";
const { contextBridge, ipcRenderer, webUtils } = require("electron");
const on = (channel) => (cb) => ipcRenderer.on(channel, (e, data) => cb(data));
contextBridge.exposeInMainWorld("luna", {
	shell: () => ipcRenderer.invoke("luna-de:shell"),
	launch: (entry) => ipcRenderer.invoke("luna-de:launch", entry),
	maximize: (id) => ipcRenderer.invoke("luna-de:maximize", id),
	cardView: () => ipcRenderer.invoke("luna-de:cardview"),
	snapshot: (id) => ipcRenderer.invoke("luna-de:snapshot", id),
	close: (id) => ipcRenderer.invoke("luna-de:close", id),
	popOut: (id) => ipcRenderer.invoke("luna-de:popout", id),
	devTools: (id) => ipcRenderer.invoke("luna-de:devtools", id),
	phoneZoom: (id, flip) => ipcRenderer.invoke("luna-de:phone-zoom", id, !!flip),
	toolsMenu: () => ipcRenderer.invoke("luna-de:tools-menu"),
	log: () => ipcRenderer.invoke("luna-de:log"),
	reload: (id) => ipcRenderer.invoke("luna-de:reload", id),
	ready: () => ipcRenderer.send("luna-de:ready"),
	// Files dropped on the shell: their paths go to the main process to be installed.
	// One File at a time: a File crosses into this world as itself, a FileList doesn't.
	install: (file) => {
		let path = "";
		try { path = webUtils.getPathForFile(file); } catch (e) {}
		return path ? ipcRenderer.invoke("luna-de:install", [path]) : Promise.resolve(false);
	},
	remove: (id) => ipcRenderer.invoke("luna-de:remove", id),
	place: (rects, dashboardsOpen) => ipcRenderer.invoke("luna-de:place", rects, dashboardsOpen),
	appMenu: () => ipcRenderer.invoke("luna-de:appmenu"),
	back: () => ipcRenderer.invoke("luna-de:back"),
	rotate: () => ipcRenderer.invoke("luna-de:rotate"),
	device: (id) => ipcRenderer.invoke("luna-de:device", id),
	fullScreen: () => ipcRenderer.invoke("luna-de:fullscreen"),
	screenNow: () => ipcRenderer.invoke("luna-de:screen-now"),
	key: (key) => ipcRenderer.invoke("luna-de:key", key),
	realKeyboard: () => ipcRenderer.invoke("luna-de:real-keyboard"),
	keyboardSize: (size) => ipcRenderer.invoke("luna-de:keyboard-size", size),
	onKeyboard: on("luna-de:keyboard"),
	onToggleCard: on("luna-de:toggle-card"),
	onLauncher: on("luna-de:launcher"),
	onScreen: on("luna-de:screen"),
	onPanelAdded: on("luna-de:panel-added"),
	onPanelRemoved: on("luna-de:panel-removed"),
	onBannerRemove: on("luna-de:banner-remove"),
	onNetwork: on("luna-de:network"),
	onHome: on("luna-de:home"),
	onCardAdded: on("luna-de:card-added"),
	onCardLoading: on("luna-de:card-loading"),
	onCardWithdrawn: on("luna-de:card-withdrawn"),
	onCardReady: on("luna-de:card-ready"),
	onCardRemoved: on("luna-de:card-removed"),
	onCardTitle: on("luna-de:card-title"),
	onShow: on("luna-de:show"),
	onApps: on("luna-de:apps"),
	onBanner: on("luna-de:banner"),
});
