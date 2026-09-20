/**
 * The default View used in a StackPane.
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.StackView",
	kind: "VFlexBox",
	className: "enyo-view enyo-bg",
	published: {
		/**
		 * z-index used for the displaying the component
		 */
		zIndex: 0,
		/**
		 * Current view depth (for the "home" button)
		 */
		depth: 0
	},
	events: {
		/**
		 * Called when the view is closed
		 */
		onClose: ""
	},
	rendered: function() {
		try {
			this.inherited(arguments);
		} catch (e) {
			this.handleException(e);
		}
	},
	handleException: function(inException) {
		this.log("Ouch: " + inException.message);			
	},
	resize: function() {	
	},
	getChatInfo: function() {
		return {};
	},
	handleInfo: function() {
	}
});

/**
 * A swipe-able StackView.
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */ 
enyo.kind({
	name: "help.SwipeableStackView",
	kind: "help.StackView",
	domStyles: {
		/**
		 * Hide the Swipeable View
		 * We want the view to be visible so the list is calculated correctly
		 * So we start the view hiding behind the empty view or any other views
		 */
		"z-index": -1
	},
	published: {
		/**
		 * The label for the Home button
		 */
		homeLabel: $L("Home")
	},
	events: {
		/**
		 * Called when the Home button is pressed
		 */
		onHome: "",
		/**
		 * Called when the view open animation is done 
		 */
		onOpenDone: "",
		/**
		 * Called when the view close animation is done
		 */
		onCloseDone: ""
	},
	//* @protected
	chrome: [
		{kind: "Control", name: "shadow", className: "enyo-sliding-view-shadow"},
		{kind: "Control", name: "client", layoutKind: "VFlexLayout", flex: 1},
		{kind: "Toolbar", className: "enyo-toolbar-light", components: [
			{kind: "GrabButton", onclick: "onClick"},
			{kind: "Control", flex: 1},
			{kind: "Button", name: "home", caption: "", onclick: "doHome"}
		]}
	],
	create: function() {
		this.inherited(arguments);
		this.homeLabelChanged();
	},
	homeLabelChanged: function() {
		this.$.home.setCaption(this.homeLabel);
	},
	depthChanged: function() {
		if (this.depth < 2) { 
			this.$.home.hide();
		}
	},
	//* @public
	open: function(immediate) {
		this.log("Open View");
		this.render();
		var style = this.hasNode().style; // After the render!
		
		if (immediate) {
			this.doOpenDone();
		} else {
			// Check the transition end event
			var transitionEndFn = function() {
				style.webkitTransition = "-webkit-transform 0s";
				this.hasNode().removeEventListener('webkitTransitionEnd', transitionEndFn, false);
				this.doOpenDone();
			}.bind(this);
			
			this.hasNode().addEventListener('webkitTransitionEnd', transitionEndFn, false );
			style.webkitTransform = "translate3d(100%, 0, 0)";
			setTimeout( function() {
				style.webkitTransition = "-webkit-transform 0.7s";
				style.webkitTransform = "translate3d(0, 0, 0)";
			}, 50);
		}
		// Set the z-index
		style.zIndex = this.zIndex;
	},
	close: function(immediate) {
		this.log("Close View");
		var style = this.hasNode().style;
		
		if (immediate) {
			this.endCloseAnimation();
		} else {
			// Check the transition end event
			var transitionEndFn = function() {
				this.hasNode().removeEventListener('webkitTransitionEnd', transitionEndFn, false);
				this.endCloseAnimation();			
			}.bind(this);
			
			this.hasNode().addEventListener('webkitTransitionEnd', transitionEndFn, false );
			setTimeout( function() {
				style.webkitTransition = "-webkit-transform 0.3s";
				style.webkitTransform = "translate3d(100%, 0, 0)";
			}, 50);
		}
	},
	//* @protected
	endCloseAnimation: function(inSender) {
		this.destroy();
		this.doCloseDone();
	},
	flickHandler: function(inSender, inEvent) {
		if ((inSender.kind === "GrabButton") && (inEvent.xVel > 0) && (Math.abs(inEvent.xVel) > Math.abs(inEvent.yVel))) {
			this.doClose(this);
			return true;
		}
	},
	onClick: function(inSender, inEvent) {
		this.doClose(this);
	},	
	dragstartHandler: function(inSender, inEvent) {
		if ((inSender.kind === "GrabButton") && Math.abs(inEvent.dx) > Math.abs(inEvent.dy)) {
			this.isDragging = true;
			return true;
		}
	},
	dragHandler: function(inSender, inEvent) {
		if (this.isDragging && this.hasNode() && inEvent.dx > 0) {
			this.node.style.viewDx = inEvent.dx;
			this.node.style.webkitTransform = "translate3d(" + inEvent.dx + "px, 0, 0)";
		}
	},
	dragfinishHandler: function(inSender, inEvent) {
		if (this.isDragging) {
			var trigger = this.hasNode().offsetWidth / 4;
			if (inEvent.dx > trigger) {
				this.doClose(this);
			} else { 
				this.resetPosition();
			}
			this.isDragging = false;
			inEvent.preventClick();
		}
	},
	resetPosition: function() {
		if (this.hasNode()) {
			this.node.style.webkitTransform = "";
		}
	}
});
 
