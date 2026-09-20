/**
 * Simple fallback image
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.HelpImage", 
	kind: "Image",
	errorHandler: function() {
		this.setSrc("images/basics.png");
	}
});