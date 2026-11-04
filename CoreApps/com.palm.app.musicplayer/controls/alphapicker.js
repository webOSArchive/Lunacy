/*globals enyo, $L, event, console */
enyo.kind({
	name: "AlphaPicker",
	kind: "VFlexBox",
	defaultKind: "Control",
	published: {},
	events: {onAlphaPicked:""},
	className: "superScrollAlpha",
	components: [
					 
					 
							 
					 
	],
	
	boolDragHoldPicked: false,
	
	chrCurrAlpha: "",
	
	create: function () {
		
		try
		{
			this.inherited(arguments);
			
		}
		catch (err)
		{
			this.log("error: ", err);
		}	
		
		
	},
	
	ready: function () {
	
		try
		{
			this.inherited(arguments);

			
		}
		catch (err)
		{
			this.log("error: ", err);
		}	
		
		
	},
	
	
	populate: function (objAlphaJumps)
	{
		

		this.log();

		this.arIndexChars = enyo.g11n.Char.getIndexChars();
		this.log(this.arIndexChars);
	
		this.destroyControls();
			
		this.createComponent({name: "alphaGrow", kind: "Control", className: "alphaGrow", showing: false});
		
		var charAlpha;
		for(var intIndexChar = 0; intIndexChar < this.arIndexChars.length; intIndexChar++)
		{
				
			charAlpha = this.arIndexChars[intIndexChar];
			this.log("charAlpha: ", charAlpha);
			var ctrlAlpha = this.createComponent({name: "ctrlAlpha_" + charAlpha, kind: "Control", content: charAlpha, flex: 1, className: "alphaLetter", onclick: "onClick_Alpha", ondragstart: "ondragstart_Alpha", ondragfinish: "ondragfinish_Alpha", ondrag: "ondrag_Alpha", ondrop: "ondrop_Alpha",ondragover: "ondragover_Alpha", ondragout: "ondragout_Alpha"});
			
			if(objAlphaJumps[charAlpha] !== undefined)
			{
				ctrlAlpha.addClass("alphaOn");
			}
			else
			{
				ctrlAlpha.addClass("alphaOff");				
			}
			
			
		}
		
		this.contentChanged();
		
	},
	
	
	onClick_Alpha: function (sender, event)
	{
		
		this.log();
		this.log(sender);
		//this.log(event);
		
		this.doAlphaPicked(sender.content);
		
	},
	
	
	onMouseDown_Alpha: function ()
	{
		this.log();	
	},
	
	
	onMouseUp_Alpha: function ()
	{
		this.log();	
	},
	
	onMouseOver_Alpha: function (sender, event)
	{
		this.log();
		this.log(sender);
		sender.addClass("alphaGrow");
		
	},
	
	onMouseOut_Alpha: function (sender, event)
	{
		this.log();
		sender.removeClass("alphaGrow");

	},
		
	ondragstart_Alpha: function (sender, event)
	{
		this.log();
		
	},
		
	ondragfinish_Alpha: function (sender, event)
	{
		this.log();
		
	},
		
	ondrag_Alpha: function (sender, event)
	{
		this.log();
		console.log(sender);
		this.log(sender.name);
		this.log(sender.content);
		
		//this.$.alphaGrow.setShowing(true);
		//this.$.alphaGrow.setContent(sender.content);
		//this.$.alphaGrow.applyStyle("top",event.pageY + "px");


	},
	

	ondrop_Alpha: function (sender, event)
	{
		this.log();
		this.doAlphaPicked(sender.content);
		this.$.alphaGrow.setShowing(false);
		
		
	},	
	
		
	ondragover_Alpha: function (sender, event)
	{
		if(this.chrCurrAlpha !== sender.content)
		{
			this.chrCurrAlpha = sender.content;
			//enyo.asyncMethod(this, doAlphaPicked,sender.content)
			//this.doAlphaPicked(sender.content);
			this.timeDragOver = new Date();
			this.boolDragHoldPicked = false;
		}
		
		var now = new Date();
		
		this.log("time over: ", now - this.timeDragOver);
		
		if(now - this.timeDragOver > 1000 && !this.boolDragHoldPicked)
		{
			enyo.asyncMethod(this, "doAlphaPicked", sender.content);
			//this.doAlphaPicked(sender.content);
			this.boolDragHoldPicked = true;
		}
		
		
		this.log();
		console.log(sender);
		this.log(sender.name);
		this.log(sender.content);
		
		this.$.alphaGrow.setShowing(true);
		this.$.alphaGrow.setContent(sender.content);
		this.$.alphaGrow.applyStyle("top",(event.pageY - 40) + "px");
		
		return true;

	},
			
	ondragout_Alpha: function (sender, event)
	{
		this.log();
		console.log(sender);
		this.log(sender.name);
		this.log(sender.content);
		//if(this.chrCurrAlpha === sender.content)
		//{
		//	this.$.alphaGrow.setShowing(false);
		//}
	

	},
	
	raiseAlphaPicked: function (chrAlpha)
	{
	
		this.doAlphaPicked(chrAlpha);
	
	}
	
		
});