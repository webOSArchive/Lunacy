/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return new PlayModeControls();
//			}
//var objName = "playModeControls";
//
//var before = function (obj, callbacks) {
//		obj.startWatch=function(){};
//	}
//var event = [[null, {rowIndex:0}]];
//var params = {
//};
//specOut(creator, objName, before, params);

describe('playModeControls tests', function(){
	var creator = function (){
				return new PlayModeControls();
			};
	var playModeControls;
	beforeEach(function(){
		playModeControls = creator();
		var before = function (obj, callbacks) {
		obj.startWatch=function(){};
	};
		var callback = function(one, fun){return fun;};
		before(playModeControls,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof playModeControls.controls).toBe('object');
		expect(typeof playModeControls.children).toBe('object');
		expect(playModeControls._componentNameMap).toEqual({"control":2});
		expect(typeof playModeControls.$).toBe('object');
		expect(playModeControls.domStyles).toEqual({});
		expect(playModeControls.domAttributes).toEqual({"className":"playmodecontrols","id":"playModeControls"});
		expect(typeof playModeControls.owner).toBe('object');
		expect(playModeControls.name).toEqual("playModeControls");
		expect(playModeControls.id).toEqual("playModeControls");
		expect(playModeControls.controlParent).toEqual(undefined);
		expect(typeof playModeControls.layout).toBe('object');
		expect(playModeControls.kind).toEqual("HFlexBox");
		expect(playModeControls.className).toEqual("playmodecontrols");
		expect(playModeControls.events).toEqual({"onShuffleClick":"","onRepeatClick":"","onSetVolume":"","onRequestVolume":""});
		expect(playModeControls.kindName).toEqual("PlayModeControls");
		expect(playModeControls.kindComponents).toEqual([{"name":"btnRepeat","kind":"Image","src":"images/btnRepeat_off.png","className":"playmodeButton","onclick":"doRepeatClick"},{"name":"btnShuffle","kind":"Image","src":"images/btnShuffle.png","className":"playmodeButton","onclick":"doShuffleClick"},{"kind":"Control","className":"volSliderContainer","components":[{"name":"sliderVolume","className":"volSlider","kind":"Slider","width":"120px","position":"100","onChange":"onChange_sliderVolume","onChanging":"onChanging_sliderVolume"}]}]);
		expect(playModeControls.onShuffleClick).toEqual("");
		expect(playModeControls.onRepeatClick).toEqual("");
		expect(playModeControls.onSetVolume).toEqual("");
		expect(playModeControls.onRequestVolume).toEqual("");
		expect(playModeControls.published).toEqual({"layoutKind":""});
		expect(playModeControls.controlParentName).toEqual("client");
		expect(playModeControls.defaultKind).toEqual("Control");
		expect(playModeControls.onclick).toEqual("");
		expect(playModeControls.onmousedown).toEqual("");
		expect(playModeControls.onmouseup).toEqual("");
		expect(playModeControls.style).toEqual("");
		expect(playModeControls.container).toEqual(null);
		expect(playModeControls.parent).toEqual(null);
		expect(playModeControls.generated).toEqual(false);
		expect(playModeControls.allowHtml).toEqual(false);
		expect(playModeControls.content).toEqual("");
		expect(playModeControls.nodeTag).toEqual("div");
		expect(playModeControls.node).toEqual(null);
		expect(playModeControls.showing).toEqual(true);
		expect(playModeControls.prepend).toEqual(false);
		expect(playModeControls.wantsEvents).toEqual(true);
	});
	describe('test (startWatch)', function(){
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(playModeControls,'inherited');
			playModeControls.create();
			expect(playModeControls.inherited).toHaveBeenCalledWith({});
		});
		it('should call doRequestVolume',function(){
			spyOn(playModeControls,'doRequestVolume');
			playModeControls.create();
			expect(playModeControls.doRequestVolume).toHaveBeenCalled();
		});
	});
	describe('test (setShuffleButton)', function(){
		
		
		it("should set src true", function(){
			playModeControls.$.btnShuffle.src = "asdf";
			playModeControls.setShuffleButton(true);
			expect(playModeControls.$.btnShuffle.src).toBe("images/btnShuffle_on.png");
		});
		it("should set src undefined", function(){
			playModeControls.$.btnShuffle.src = "asdf";
			playModeControls.setShuffleButton();
			expect(playModeControls.$.btnShuffle.src).toBe("images/btnShuffle.png");
		});
		
		it("should call srcChanged", function(){
			spyOn(playModeControls.$.btnShuffle, "srcChanged");
			playModeControls.setShuffleButton();
			expect(playModeControls.$.btnShuffle.srcChanged).toHaveBeenCalled();
		});
		
		
	});
	describe('test (setRepeatButton)', function(){
		it("should set src true", function(){
			playModeControls.$.btnRepeat.src = "asdf";
			var strRepeatMode = "something";
			playModeControls.setRepeatButton(strRepeatMode);
			expect(playModeControls.$.btnRepeat.src).toBe("images/btnRepeat_" + strRepeatMode + ".png");
		});
		
		it("should call srcChanged", function(){
			spyOn(playModeControls.$.btnRepeat, "srcChanged");
			playModeControls.setRepeatButton();
			expect(playModeControls.$.btnRepeat.srcChanged).toHaveBeenCalled();
		});
		
	});
	describe('test (onGetVolume)', function(){
		it("should call setPositionImmediate", function(){
			spyOn(playModeControls.$.sliderVolume, "setPositionImmediate");
			playModeControls.onGetVolume(11);
			expect(playModeControls.$.sliderVolume.setPositionImmediate).toHaveBeenCalledWith(11);
		});
		
		
	});
	describe('test (onChange_sliderVolume)', function(){
		it('should call doSetVolume',function(){
			spyOn(playModeControls,'doSetVolume');
			playModeControls.onChange_sliderVolume(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playModeControls.doSetVolume).toHaveBeenCalledWith({"rowIndex":0,"dispatchTarget":null});
		});
	});
	describe('test (onChanging_sliderVolume)', function(){
		it('should call doSetVolume',function(){
			spyOn(playModeControls,'doSetVolume');
			playModeControls.onChanging_sliderVolume(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playModeControls.doSetVolume).toHaveBeenCalledWith({"rowIndex":0,"dispatchTarget":null});
		});
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(playModeControls,'_constructor');
			playModeControls.base();
			expect(playModeControls._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(playModeControls,'constructed');
			playModeControls.base();
			expect(playModeControls.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(playModeControls,'_constructor');
			playModeControls.ctor();
			expect(playModeControls._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(playModeControls,'constructed');
			playModeControls.ctor();
			expect(playModeControls.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doShuffleClick)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playModeControls,'dispatchIndirectly');
			playModeControls.doShuffleClick();
			expect(playModeControls.dispatchIndirectly).toHaveBeenCalledWith("onShuffleClick",{});
		});
	});
	describe('test (doRepeatClick)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playModeControls,'dispatchIndirectly');
			playModeControls.doRepeatClick();
			expect(playModeControls.dispatchIndirectly).toHaveBeenCalledWith("onRepeatClick",{});
		});
	});
	describe('test (doSetVolume)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playModeControls,'dispatchIndirectly');
			playModeControls.doSetVolume();
			expect(playModeControls.dispatchIndirectly).toHaveBeenCalledWith("onSetVolume",{});
		});
	});
	describe('test (doRequestVolume)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playModeControls,'dispatchIndirectly');
			playModeControls.doRequestVolume();
			expect(playModeControls.dispatchIndirectly).toHaveBeenCalledWith("onRequestVolume",{});
		});
	});
	describe('test (layoutKind)', function(){
		it('should call _constructor',function(){
			spyOn(playModeControls,'_constructor');
			playModeControls.layoutKind();
			expect(playModeControls._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(playModeControls,'constructed');
			playModeControls.layoutKind();
			expect(playModeControls.constructed).toHaveBeenCalled();
		});
	});
	describe('test (destroy)', function(){
		it('should call unregisterEvents',function(){
			spyOn(playModeControls,'unregisterEvents');
			playModeControls.destroy();
			expect(playModeControls.unregisterEvents).toHaveBeenCalled();
		});
		it('should call destroyControls',function(){
			spyOn(playModeControls,'destroyControls');
			playModeControls.destroy();
			expect(playModeControls.destroyControls).toHaveBeenCalled();
		});
		it('should call inherited',function(){
			spyOn(playModeControls,'inherited');
			playModeControls.destroy();
			expect(playModeControls.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(playModeControls,'inherited');
			playModeControls.importProps();
			expect(playModeControls.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
		it('should call createChrome',function(){
			spyOn(playModeControls,'createChrome');
			playModeControls.initComponents();
			expect(playModeControls.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(playModeControls,'inherited');
			playModeControls.initComponents();
			expect(playModeControls.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(playModeControls,'inherited');
			playModeControls.createComponents();
			expect(playModeControls.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(playModeControls,'discoverControlParent');
			playModeControls.createComponents();
			expect(playModeControls.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(playModeControls,'createComponents');
			playModeControls.createChrome();
			expect(playModeControls.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});