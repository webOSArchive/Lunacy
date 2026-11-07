/* Copyright 2009 Palm, Inc.  All rights reserved. */

enyo.kind({
    name: "UpdateDaemonService",
    kind: "PalmService",
    service: "palm://com.palm.update/"
});

enyo.kind({
    name: "ConnectionManagerService",
    kind: "PalmService",
    service: "palm://com.palm.connectionmanager/"
});

enyo.kind({
    name: "SignalBusService",
    kind: "PalmService",
    service: "palm://com.palm.bus/signal/"
});

enyo.kind({
	name: "UpdateValidationService",
	kind: "PalmService",
	service: "palm://com.palm.validation/"
});

enyo.kind({
    name: "SystemPrefsService",
    kind: "PalmService",
    service: "palm://com.palm.systemservice/"
});

enyo.kind({
    name: "SystemProperty",
    kind: "PalmService",
    service: "palm://com.palm.preferences/"
});

enyo.kind({
    name: "ApplicationManagerService",
    kind: "PalmService",
    service: "palm://com.palm.applicationManager/"
});

