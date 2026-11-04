/*globals specOut, enyo, $L, event, Utilities, window, LibraryTools */

/*
var creator = function(){
				return enyo.create({kind: "AlphaPicker"});
			};
var objName = "AlphaPicker";

var before = function (obj, callbacks) {
	
        
};
var params = {
		 
};

specOut(creator, objName, before, params);*/

/*
describe('AlphaPicker tests', function(){
	var creator = function (){
				return enyo.create({kind: "AlphaPicker"});
			};
	var AlphaPicker = creator();
	beforeEach(function(){
		var before = function (obj, callbacks) {
        
};
		var callback = function(one, fun){return fun;};
		before(AlphaPicker,callback);
	});
	it('should correctly set up object', function(){
		expect(AlphaPicker.controls).toEqual([]);
		expect(AlphaPicker.children).toEqual([]);
		expect(AlphaPicker._componentNameMap).toEqual({});
		expect(AlphaPicker.$).toEqual({});
		expect(AlphaPicker.domStyles).toEqual({});
		expect(AlphaPicker.kind).toEqual("AlphaPicker");
		expect(typeof AlphaPicker.owner).toBe('object');
		expect(AlphaPicker.name).toEqual("alphaPicker");
		expect(AlphaPicker.id).toEqual("alphaPicker");
		expect(typeof AlphaPicker.controlParent).toBe('undefined');
		expect(typeof AlphaPicker.layout).toBe('object');
		expect(AlphaPicker.defaultKind).toEqual("Control");
		expect(AlphaPicker.published).toEqual({});
		expect(AlphaPicker.className).toEqual("superScrollAlpha");
		expect(AlphaPicker.kindName).toEqual("AlphaPicker");
		expect(AlphaPicker.kindComponents).toEqual([]);
		expect(AlphaPicker.onAlphaPicked).toEqual("");
		expect(AlphaPicker.controlParentName).toEqual("client");
		expect(AlphaPicker.onclick).toEqual("");
		expect(AlphaPicker.onmousedown).toEqual("");
		expect(AlphaPicker.onmouseup).toEqual("");
		expect(AlphaPicker.style).toEqual("");
		expect(AlphaPicker.container).toEqual(null);
		expect(AlphaPicker.parent).toEqual(null);
		expect(AlphaPicker.generated).toEqual(false);
		expect(AlphaPicker.allowHtml).toEqual(false);
		expect(AlphaPicker.content).toEqual("");
		expect(AlphaPicker.nodeTag).toEqual("div");
		expect(AlphaPicker.node).toEqual(null);
		expect(AlphaPicker.showing).toEqual(true);
		expect(AlphaPicker.prepend).toEqual(false);
		expect(AlphaPicker.wantsEvents).toEqual(true);
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(AlphaPicker,'inherited');
			AlphaPicker.create();
			expect(AlphaPicker.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (ready)', function(){
		it('should call inherited',function(){
			spyOn(AlphaPicker,'inherited');
			AlphaPicker.ready();
			expect(AlphaPicker.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (populate)', function(){
		it('should call destroyControls',function(){
			spyOn(AlphaPicker,'destroyControls');
			AlphaPicker.populate({});
			expect(AlphaPicker.destroyControls).toHaveBeenCalled();
		});
		it('should call contentChanged',function(){
			spyOn(AlphaPicker,'contentChanged');
			AlphaPicker.populate({});
			expect(AlphaPicker.contentChanged).toHaveBeenCalled();
		});
		it('should call fromCharCode',function(){
			spyOn(String,'fromCharCode');
			AlphaPicker.populate({});
			expect(String.fromCharCode).toHaveBeenCalled();
		});
		


		
	});
	describe('test (onClick_Alpha)', function(){
		it('should call doAlphaPicked',function(){
			spyOn(AlphaPicker,'doAlphaPicked');
			AlphaPicker.onClick_Alpha({content: ""},{"rowIndex":0,"dispatchTarget":null});
			expect(AlphaPicker.doAlphaPicked).toHaveBeenCalled();
		});
	});
	describe('test (onMouseDown_Alpha)', function(){
		AlphaPicker.onMouseDown_Alpha();
	});
	describe('test (onMouseUp_Alpha)', function(){
		AlphaPicker.onMouseUp_Alpha();
	});
	describe('test (onMouseOver_Alpha)', function(){
		var obj = {addClass: function(){}};
		spyOn(obj, 'addClass');
		AlphaPicker.onMouseOver_Alpha(obj);
		expect(obj.addClass).toHaveBeenCalledWith("alphaGrow");
	});
	describe('test (onMouseOut_Alpha)', function(){
		var obj = {removeClass: function(){}};
		spyOn(obj, 'removeClass');
		AlphaPicker.onMouseOut_Alpha(obj);
		expect(obj.removeClass).toHaveBeenCalledWith("alphaGrow");
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(AlphaPicker,'_constructor');
			AlphaPicker.base();
			expect(AlphaPicker._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(AlphaPicker,'constructed');
			AlphaPicker.base();
			expect(AlphaPicker.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(AlphaPicker,'_constructor');
			AlphaPicker.ctor();
			expect(AlphaPicker._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(AlphaPicker,'constructed');
			AlphaPicker.ctor();
			expect(AlphaPicker.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doAlphaPicked)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(AlphaPicker,'dispatchIndirectly');
			AlphaPicker.doAlphaPicked();
			expect(AlphaPicker.dispatchIndirectly).toHaveBeenCalledWith("onAlphaPicked",{});
		});
	});
	describe('test (layoutKind)', function(){
		it('should call _constructor',function(){
			spyOn(AlphaPicker,'_constructor');
			AlphaPicker.layoutKind();
			expect(AlphaPicker._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(AlphaPicker,'constructed');
			AlphaPicker.layoutKind();
			expect(AlphaPicker.constructed).toHaveBeenCalled();
		});
	});
	describe('test (destroy)', function(){
		it('should call unregisterEvents',function(){
			spyOn(AlphaPicker,'unregisterEvents');
			AlphaPicker.destroy();
			expect(AlphaPicker.unregisterEvents).toHaveBeenCalled();
		});
		it('should call destroyControls',function(){
			spyOn(AlphaPicker,'destroyControls');
			AlphaPicker.destroy();
			expect(AlphaPicker.destroyControls).toHaveBeenCalled();
		});
		it('should call inherited',function(){
			spyOn(AlphaPicker,'inherited');
			AlphaPicker.destroy();
			expect(AlphaPicker.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(AlphaPicker,'inherited');
			AlphaPicker.importProps();
			expect(AlphaPicker.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
		it('should call createChrome',function(){
			spyOn(AlphaPicker,'createChrome');
			AlphaPicker.initComponents();
			expect(AlphaPicker.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(AlphaPicker,'inherited');
			AlphaPicker.initComponents();
			expect(AlphaPicker.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(AlphaPicker,'inherited');
			AlphaPicker.createComponents();
			expect(AlphaPicker.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(AlphaPicker,'discoverControlParent');
			AlphaPicker.createComponents();
			expect(AlphaPicker.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(AlphaPicker,'createComponents');
			AlphaPicker.createChrome();
			expect(AlphaPicker.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});

*/