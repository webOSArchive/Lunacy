/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return new KindPlaycontrols();
//			}
//var objName = "playcontrols";
//
//var before = function (obj, callbacks) {
//		obj.startWatch=function(){};
//	}
//var event = [[null, {rowIndex:0}]];
//var params = {
//};
//specOut(creator, objName, before, params);

describe('playcontrols tests', function(){
	var creator = function (){
				return new KindPlaycontrols();
			};
	var playcontrols;
	beforeEach(function(){
		playcontrols = creator();
		var before = function (obj, callbacks) {
		obj.startWatch=function(){};
	};
		var callback = function(one, fun){return fun;};
		before(playcontrols,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof playcontrols.controls).toBe('object');
		expect(typeof playcontrols.children).toBe('object');
		expect(playcontrols._componentNameMap).toEqual({});
		expect(typeof playcontrols.$).toBe('object');
		//expect(playcontrols.domStyles).toEqual({});
		expect(playcontrols.domAttributes).toEqual({"className":"playcontrols","id":"kindPlaycontrols"});
		expect(typeof playcontrols.owner).toBe('object');
		expect(playcontrols.name).toEqual("kindPlaycontrols");
		expect(playcontrols.id).toEqual("kindPlaycontrols");
		expect(playcontrols.controlParent).toEqual(undefined);
		expect(typeof playcontrols.layout).toBe('object');
		expect(playcontrols.kind).toEqual("HFlexBox");
		expect(playcontrols.className).toEqual("playcontrols");
		expect(playcontrols.events).toEqual({"onClickNext":"","onClickPrev":"","onClickPlayPause":""});
		expect(playcontrols.kindName).toEqual("KindPlaycontrols");
		expect(playcontrols.kindComponents).toEqual([{"name":"btnPrev","kind":"Image","src":"images/btnPrev.png","className":"playButton","onclick":"onclick_prev"},{"name":"btnPlay","kind":"Image","src":"images/btnPlay.png","className":"playButton","onclick":"onclick_playpause"},{"name":"btnNext","kind":"Image","src":"images/btnNext.png","className":"playButton","onclick":"onclick_next"}]);
		expect(playcontrols.onClickNext).toEqual("");
		expect(playcontrols.onClickPrev).toEqual("");
		expect(playcontrols.onClickPlayPause).toEqual("");
		expect(playcontrols.published).toEqual({"layoutKind":""});
		expect(playcontrols.controlParentName).toEqual("client");
		expect(playcontrols.defaultKind).toEqual("Control");
		expect(playcontrols.onclick).toEqual("");
		expect(playcontrols.onmousedown).toEqual("");
		expect(playcontrols.onmouseup).toEqual("");
		expect(playcontrols.style).toEqual("");
		expect(playcontrols.container).toEqual(null);
		expect(playcontrols.parent).toEqual(null);
		expect(playcontrols.generated).toEqual(false);
		expect(playcontrols.allowHtml).toEqual(false);
		expect(playcontrols.content).toEqual("");
		expect(playcontrols.nodeTag).toEqual("div");
		expect(playcontrols.node).toEqual(null);
		expect(playcontrols.showing).toEqual(true);
		expect(playcontrols.prepend).toEqual(false);
		expect(playcontrols.wantsEvents).toEqual(true);
	});
	describe('test (startWatch)', function(){
	});
	describe('test (onclick_next)', function(){
		it('should call doClickNext',function(){
			spyOn(playcontrols,'doClickNext');
			playcontrols.onclick_next(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playcontrols.doClickNext).toHaveBeenCalled();
		});
	});
	describe('test (onclick_prev)', function(){
		it('should call doClickPrev',function(){
			spyOn(playcontrols,'doClickPrev');
			playcontrols.onclick_prev(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playcontrols.doClickPrev).toHaveBeenCalled();
		});
	});
	describe('test (onclick_playpause)', function(){
		it('should call doClickPlayPause',function(){
			spyOn(playcontrols,'doClickPlayPause');
			playcontrols.onclick_playpause(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playcontrols.doClickPlayPause).toHaveBeenCalled();
		});
	});
	describe('test (setPlayPause)', function(){
		
		
		it("should set his.$.btnPlay.src true", function(){
			playcontrols.$.btnPlay.src = "asdf";
			playcontrols.setPlayPause(true);
			expect(playcontrols.$.btnPlay.src).toBe("images/btnPause.png");
		});
		
		it("should set his.$.btnPlay.src false", function(){
			playcontrols.$.btnPlay.src = "asdf";
			playcontrols.setPlayPause(false);
			expect(playcontrols.$.btnPlay.src).toBe("images/btnPlay.png");
		});
		
		it("should call srcChanged", function(){
			spyOn(playcontrols.$.btnPlay,'srcChanged');
			playcontrols.setPlayPause(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playcontrols.$.btnPlay.srcChanged).toHaveBeenCalled();
		});
		
		
		
		
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(playcontrols,'_constructor');
			playcontrols.base();
			expect(playcontrols._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(playcontrols,'constructed');
			playcontrols.base();
			expect(playcontrols.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(playcontrols,'_constructor');
			playcontrols.ctor();
			expect(playcontrols._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(playcontrols,'constructed');
			playcontrols.ctor();
			expect(playcontrols.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doClickNext)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playcontrols,'dispatchIndirectly');
			playcontrols.doClickNext();
			expect(playcontrols.dispatchIndirectly).toHaveBeenCalledWith("onClickNext",{});
		});
	});
	describe('test (doClickPrev)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playcontrols,'dispatchIndirectly');
			playcontrols.doClickPrev();
			expect(playcontrols.dispatchIndirectly).toHaveBeenCalledWith("onClickPrev",{});
		});
	});
	describe('test (doClickPlayPause)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playcontrols,'dispatchIndirectly');
			playcontrols.doClickPlayPause();
			expect(playcontrols.dispatchIndirectly).toHaveBeenCalledWith("onClickPlayPause",{});
		});
	});
	describe('test (layoutKind)', function(){
		it('should call _constructor',function(){
			spyOn(playcontrols,'_constructor');
			playcontrols.layoutKind();
			expect(playcontrols._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(playcontrols,'constructed');
			playcontrols.layoutKind();
			expect(playcontrols.constructed).toHaveBeenCalled();
		});
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(playcontrols,'inherited');
			playcontrols.create();
			expect(playcontrols.inherited).toHaveBeenCalledWith({});
		});
		it('should call registerEvents',function(){
			spyOn(playcontrols,'registerEvents');
			playcontrols.create();
			expect(playcontrols.registerEvents).toHaveBeenCalled();
		});
		it('should call layoutKindChanged',function(){
			spyOn(playcontrols,'layoutKindChanged');
			playcontrols.create();
			expect(playcontrols.layoutKindChanged).toHaveBeenCalled();
		});
	});
	describe('test (destroy)', function(){
		it('should call unregisterEvents',function(){
			spyOn(playcontrols,'unregisterEvents');
			playcontrols.destroy();
			expect(playcontrols.unregisterEvents).toHaveBeenCalled();
		});
		it('should call destroyControls',function(){
			spyOn(playcontrols,'destroyControls');
			playcontrols.destroy();
			expect(playcontrols.destroyControls).toHaveBeenCalled();
		});
		it('should call inherited',function(){
			spyOn(playcontrols,'inherited');
			playcontrols.destroy();
			expect(playcontrols.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(playcontrols,'inherited');
			playcontrols.importProps();
			expect(playcontrols.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
		it('should call createChrome',function(){
			spyOn(playcontrols,'createChrome');
			playcontrols.initComponents();
			expect(playcontrols.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(playcontrols,'inherited');
			playcontrols.initComponents();
			expect(playcontrols.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(playcontrols,'inherited');
			playcontrols.createComponents();
			expect(playcontrols.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(playcontrols,'discoverControlParent');
			playcontrols.createComponents();
			expect(playcontrols.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(playcontrols,'createComponents');
			playcontrols.createChrome();
			expect(playcontrols.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});