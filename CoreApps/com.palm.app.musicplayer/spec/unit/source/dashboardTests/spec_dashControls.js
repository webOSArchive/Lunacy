/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return enyo.create({kind: "DashControlsApp"});
//			}
//var objName = "DashControlsApp";
//
//var before = function (obj, callbacks) {
//	
//	
//	}
//var event = [[null, {rowIndex:0}]];
//var params = {
//		 "updateTrackInfoDisplay" : [[{strTrackTitle: "one", strTrackArtist: "two"}], 
//		                             [{strTrackTitle: "three", strTrackArtist: "four"}]],
//		 "setPlayPause" : [[true],[false]],
//		 "sendCommand"	: [["prev"],["next"],["playpause"]]
//		 
//};
//
//specOut(creator, objName, before, params);

describe('DashControlsApp tests', function(){
	var creator = function (){
				return enyo.create({kind: "DashControlsApp"});
			};
	var DashControlsApp;
	beforeEach(function(){
		DashControlsApp = creator();
		var before = function (obj, callbacks) {
	
	
	};
		var callback = function(one, fun){return fun;};
		before(DashControlsApp,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof DashControlsApp.controls).toBe('object');
		expect(typeof DashControlsApp.children).toBe('object');
		expect(DashControlsApp._componentNameMap).toEqual({"control":3});
		expect(typeof DashControlsApp.$).toBe('object');
		expect(DashControlsApp.domStyles).toEqual({});
		expect(DashControlsApp.domAttributes).toEqual({"className":"music-notification","id":"dashControlsApp"});
		expect(DashControlsApp.kind).toEqual("DashControlsApp");
		expect(typeof DashControlsApp.owner).toBe('object');
		expect(DashControlsApp.name).toEqual("dashControlsApp");
		expect(DashControlsApp.id).toEqual("dashControlsApp");
		expect(DashControlsApp.controlParent).toEqual(undefined);
		expect(typeof DashControlsApp.layout).toBe('object');
		expect(DashControlsApp.align).toEqual("center");
		expect(DashControlsApp.className).toEqual("music-notification");
		expect(DashControlsApp.kindName).toEqual("DashControlsApp");
		expect(DashControlsApp.kindComponents).toEqual([{"name":"appEvent","kind":"ApplicationEvents","onWindowParamsChange":"windowParamsChangeHandler"},{"kind":"Control","flex":1,"className":"info","onclick":"onclick_focus","components":[{"name":"lblSongTitle","content":"Song","className":"title"},{"name":"lblArtistName","content":"Artist","className":"artist"}]},{"kind":"Control","className":"playback-controls","layoutKind":"HFlexLayout","pack":"start","align":"center","components":[{"name":"btnPrev","kind":"IconButton","className":"prev","icon":"images/btn_dashboard_prev.png","onclick":"onclick_prev"},{"name":"btnPlay","kind":"IconButton","className":"play paused","icon":"images/btn_dashboard_play.png","label":" ","onclick":"onclick_playpause"},{"name":"btnNext","kind":"IconButton","className":"next","icon":"images/btn_dashboard_next.png","onclick":"onclick_next"}]}]);
		expect(DashControlsApp.published).toEqual({"layoutKind":""});
		expect(DashControlsApp.events).toEqual({"onclick":"","onmousedown":"","onmouseup":""});
		expect(DashControlsApp.controlParentName).toEqual("client");
		expect(DashControlsApp.defaultKind).toEqual("Control");
		expect(DashControlsApp.onclick).toEqual("");
		expect(DashControlsApp.onmousedown).toEqual("");
		expect(DashControlsApp.onmouseup).toEqual("");
		expect(DashControlsApp.style).toEqual("");
		expect(DashControlsApp.container).toEqual(null);
		expect(DashControlsApp.parent).toEqual(null);
		expect(DashControlsApp.generated).toEqual(false);
		expect(DashControlsApp.allowHtml).toEqual(false);
		expect(DashControlsApp.content).toEqual("");
		expect(DashControlsApp.nodeTag).toEqual("div");
		expect(DashControlsApp.node).toEqual(null);
		expect(DashControlsApp.showing).toEqual(true);
		expect(DashControlsApp.prepend).toEqual(false);
		expect(DashControlsApp.wantsEvents).toEqual(true);
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(DashControlsApp,'inherited');
			DashControlsApp.create();
			expect(DashControlsApp.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (windowParamsChangeHandler)', function(){
		it("should updateTrackInfoDisplay", function(){
			spyOn(DashControlsApp, "updateTrackInfoDisplay");
			enyo.windowParams.objTrackInfo = true;
			DashControlsApp.windowParamsChangeHandler();
			expect(DashControlsApp.updateTrackInfoDisplay).toHaveBeenCalledWith(enyo.windowParams.objTrackInfo);
		});
		it("shouldn't updateTrackInfoDisplay", function(){
			spyOn(DashControlsApp, "updateTrackInfoDisplay");
			enyo.windowParams.objTrackInfo = false;
			DashControlsApp.windowParamsChangeHandler();
			expect(DashControlsApp.updateTrackInfoDisplay).not.toHaveBeenCalled();
		});
		
		
		it("should setPlayPause", function(){
			spyOn(DashControlsApp, "setPlayPause");
			enyo.windowParams.boolAudioPlaying = false;
			DashControlsApp.windowParamsChangeHandler();
			expect(DashControlsApp.setPlayPause).toHaveBeenCalledWith(enyo.windowParams.boolAudioPlaying);
		});
		
		it("shouldn't setPlayPause", function(){
			spyOn(DashControlsApp, "setPlayPause");
			enyo.windowParams.boolAudioPlaying = undefined;
			DashControlsApp.windowParamsChangeHandler();
			expect(DashControlsApp.setPlayPause).not.toHaveBeenCalled();
		});
		
		
		
		
	});
	describe('test (updateTrackInfoDisplay)', function(){
		describe('testing (updateTrackInfoDisplay) with params [{"strTrackTitle":"one","strTrackArtist":"two"}]', function(){
			it("should setTitleContent", function(){
				var param = {"strTrackTitle":"one","strTrackArtist":"two"};
				spyOn(DashControlsApp.$.lblSongTitle, "setContent");
				DashControlsApp.updateTrackInfoDisplay(param);
				expect(DashControlsApp.$.lblSongTitle.setContent).toHaveBeenCalledWith(param.strTrackTitle);
			});
			it("should setArtistContent", function(){
				var param = {"strTrackTitle":"one","strTrackArtist":"two"};
				spyOn(DashControlsApp.$.lblArtistName, "setContent");
				DashControlsApp.updateTrackInfoDisplay(param);
				expect(DashControlsApp.$.lblArtistName.setContent).toHaveBeenCalledWith(param.strTrackArtist);
			});
		});
		describe('testing (updateTrackInfoDisplay) with params [{"strTrackTitle":"three","strTrackArtist":"four"}]', function(){
			it("should setTitleContent", function(){
				var param = {"strTrackTitle":"three","strTrackArtist":"four"};
				spyOn(DashControlsApp.$.lblSongTitle, "setContent");
				DashControlsApp.updateTrackInfoDisplay(param);
				expect(DashControlsApp.$.lblSongTitle.setContent).toHaveBeenCalledWith(param.strTrackTitle);
			});
			it("should setArtistContent", function(){
				var param = {"strTrackTitle":"three","strTrackArtist":"four"};
				spyOn(DashControlsApp.$.lblArtistName, "setContent");
				DashControlsApp.updateTrackInfoDisplay(param);
				expect(DashControlsApp.$.lblArtistName.setContent).toHaveBeenCalledWith(param.strTrackArtist);
			});
		
		});
	});
	describe('test (setPlayPause)', function(){
		it('testing (setPlayPause) with params [true]', function(){
			var param = true;
			spyOn(DashControlsApp.$.btnPlay, "addRemoveClass");
			DashControlsApp.setPlayPause(param);
			expect(DashControlsApp.$.btnPlay.addRemoveClass).toHaveBeenCalledWith("paused", !param);
		});
		it('testing (setPlayPause) with params [false]', function(){
			var param = false;
			spyOn(DashControlsApp.$.btnPlay, "addRemoveClass");
			DashControlsApp.setPlayPause(param);
			expect(DashControlsApp.$.btnPlay.addRemoveClass).toHaveBeenCalledWith("paused", !param);
		});
	});
	describe('test (onclick_prev)', function(){
		it('should call sendCommand',function(){
			spyOn(DashControlsApp,'sendCommand');
			DashControlsApp.onclick_prev(null,{"rowIndex":0,"dispatchTarget":null});
			expect(DashControlsApp.sendCommand).toHaveBeenCalledWith("prev");
		});
	});
	describe('test (onclick_playpause)', function(){
		it('should call sendCommand',function(){
			spyOn(DashControlsApp,'sendCommand');
			DashControlsApp.onclick_playpause(null,{"rowIndex":0,"dispatchTarget":null});
			expect(DashControlsApp.sendCommand).toHaveBeenCalledWith("playpause");
		});
	});
	describe('test (onclick_next)', function(){
		it('should call sendCommand',function(){
			spyOn(DashControlsApp,'sendCommand');
			DashControlsApp.onclick_next(null,{"rowIndex":0,"dispatchTarget":null});
			expect(DashControlsApp.sendCommand).toHaveBeenCalledWith("next");
		});
	});
	it('test (onclick_focus)', function(){
		spyOn(enyo.windows,'activate');
		DashControlsApp.onclick_focus(null,{"rowIndex":0,"dispatchTarget":null});
		expect(enyo.windows.activate).toHaveBeenCalledWith(undefined, "com.palm.app.musicplayer", {"gotoNowPlaying": true});
		
	});
	describe('test (sendCommand)', function(){
		beforeEach(function(){
			enyo.windows.fetchWindow = function(){return true;};
			
		});
		it("should fetchWindow", function(){
			spyOn(enyo.windows,'fetchWindow');
			DashControlsApp.sendCommand(null,{"rowIndex":0,"dispatchTarget":null});
			expect(enyo.windows.fetchWindow).toHaveBeenCalledWith("com.palm.app.musicplayer");
		
		});
		it('testing (sendCommand) with params ["prev"]', function(){
			var param = "prev";
			spyOn(enyo.windows,'setWindowParams');
			DashControlsApp.sendCommand(param);
			expect(enyo.windows.setWindowParams).toHaveBeenCalled();
			console.log(enyo.windows.setWindowParams.mostRecentCall.args);
			expect(enyo.windows.setWindowParams.mostRecentCall.args[1].cmdType).toBe(param);
		
		});
		it('testing (sendCommand) with params ["next"]', function(){
			var param = "next";
			spyOn(enyo.windows,'setWindowParams');
			DashControlsApp.sendCommand(param);
			expect(enyo.windows.setWindowParams).toHaveBeenCalled();
			expect(enyo.windows.setWindowParams.mostRecentCall.args[1].cmdType).toBe(param);
		});
		it('testing (sendCommand) with params ["playpause"]', function(){
			var param = "playpause";
			spyOn(enyo.windows,'setWindowParams');
			DashControlsApp.sendCommand(param);
			expect(enyo.windows.setWindowParams).toHaveBeenCalled();
			expect(enyo.windows.setWindowParams.mostRecentCall.args[1].cmdType).toBe(param);
		});
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(DashControlsApp,'_constructor');
			DashControlsApp.base();
			expect(DashControlsApp._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(DashControlsApp,'constructed');
			DashControlsApp.base();
			expect(DashControlsApp.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(DashControlsApp,'_constructor');
			DashControlsApp.ctor();
			expect(DashControlsApp._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(DashControlsApp,'constructed');
			DashControlsApp.ctor();
			expect(DashControlsApp.constructed).toHaveBeenCalled();
		});
	});
	describe('test (layoutKind)', function(){
		it('should call _constructor',function(){
			spyOn(DashControlsApp,'_constructor');
			DashControlsApp.layoutKind();
			expect(DashControlsApp._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(DashControlsApp,'constructed');
			DashControlsApp.layoutKind();
			expect(DashControlsApp.constructed).toHaveBeenCalled();
		});
	});
	describe('test (destroy)', function(){
		it('should call unregisterEvents',function(){
			spyOn(DashControlsApp,'unregisterEvents');
			DashControlsApp.destroy();
			expect(DashControlsApp.unregisterEvents).toHaveBeenCalled();
		});
		it('should call destroyControls',function(){
			spyOn(DashControlsApp,'destroyControls');
			DashControlsApp.destroy();
			expect(DashControlsApp.destroyControls).toHaveBeenCalled();
		});
		it('should call inherited',function(){
			spyOn(DashControlsApp,'inherited');
			DashControlsApp.destroy();
			expect(DashControlsApp.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(DashControlsApp,'inherited');
			DashControlsApp.importProps();
			expect(DashControlsApp.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
		it('should call createChrome',function(){
			spyOn(DashControlsApp,'createChrome');
			DashControlsApp.initComponents();
			expect(DashControlsApp.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(DashControlsApp,'inherited');
			DashControlsApp.initComponents();
			expect(DashControlsApp.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(DashControlsApp,'inherited');
			DashControlsApp.createComponents();
			expect(DashControlsApp.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(DashControlsApp,'discoverControlParent');
			DashControlsApp.createComponents();
			expect(DashControlsApp.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(DashControlsApp,'createComponents');
			DashControlsApp.createChrome();
			expect(DashControlsApp.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});