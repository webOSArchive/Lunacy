/**
 * A stack container for StackView components (simple or swipe-able).
 * 
 * components: [
 *     {kind: "StackPane", name: "group", flex: 1, components: [
 *         {kind: "StackView", name: "emptyPane"}
 *     ]}
 * ]
 * 
 * The optional StackView defined in the components section cannot be closed.
 * When the container is empty, it will call the onEvent handler.
 * 
 * Usage Example:
 * this.$.group.push({kind: "SwipeableStackView", ...});
 * this.$.group.pop(true);
 * this.$.group.popAll();
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.StackPane",
	kind: "Control",
	className: "enyo-pane help-stack-pane",
	published: { },
	events: {
		/**
		 * Called when the stack pane is empty
		 */
		onEmpty: ""
	},
	//* @protected
	defaultKind: "help.StackView",
	constructor: function() {
		this.stack = [];
		this.lastzindex = 0;
		this.inherited(arguments);
	},	
	initComponents: function() {
		this.inherited(arguments);
	},
	//* @public	
	/**
	 * Tell the panes to resize
	 */
	resize: function() {
		var i;
		for (i = 0; i < this.stack.length; i++) {
			var view = this.stack[i];
			if (view) {
				view.resize();
			}
		}
	},
	/**
	 * Add a new view to the stack pane.
	 * @param inView	A control object to be created
	 * @param immediate	If the view should appear immediately without transition
	 */
	pushView: function(inView, immediate) {
		this.lastzindex++;
		inView.setZIndex(this.lastzindex);
		inView.setDepth(this.stack.length);
		this.stack.push(inView);
		inView.open(immediate);
	},
	/**
	 * Remove the top view from the stack pane.
	 * @param inView	(optional) Reference to the view that should close if on top
	 * @param immediate	If the view should be removed immediately without transition
	 */
	popView: function(inView, immediate) {
		// Get the last pane on the stack
		var view = this.stack[this.stack.length-1];
		if ((inView && (view === inView)) || (!inView && view)) {
			if (this.lastzindex > 0) {
				this.lastzindex--;
			}
			this.stack.pop();
			view.close(immediate);
		}
		if (this.stack.length === 0) {
			this.doEmpty();
		}
	},
	/**
	 * Remove all the views but keep the empty view if any.
	 */
	popAllViews: function() {
		while (this.stack.length > 0) {
			var view = this.stack.pop();
			if (view) {
				view.close(true /*immediate*/);
			}
		}
		this.lastzindex = 0;
	},
	/**
	 * Removes all the views but one.
	 */
	popAllViewsButOne: function() {
		while (this.stack.length > 1) {
			var view = this.stack.pop();
			if (view) {
				view.close(true /*immediate*/);
			}
		}
		this.lastzindex = 1;
	},
	/**
	 * Returns the top view chat info
	 */
	getChatInfo: function() {
		var info = {};
		if (this.stack.length) {
			info = this.stack[this.stack.length - 1].getChatInfo();
		}
		return info;
	},
	/**
	 * Send some custom info to views that want to handle it
	 */
	sendInfo: function(info) {
		var i;
		for (i = 0; i < this.stack.length; i++) {
			var view = this.stack[i];
			if (view) {
				view.handleInfo(info);
			}
		}
	}
});
