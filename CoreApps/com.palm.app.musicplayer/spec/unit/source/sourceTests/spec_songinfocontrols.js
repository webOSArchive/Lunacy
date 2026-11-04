/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return new kindSongInfoControls();
//			}
//var objName = "songInfoControls";
//
//var before = function (obj, callbacks) {
//		obj.startWatch=function(){};
//	}
//var event = [[null, {rowIndex:0}]];
//var params = {
//		updateTrackInfoDisplay : [[{strTrackTitle: "title", strTrackArtist: "artist", strTrackAlbum: "album"}]],
//		updateTrackTimeDisplay : [[{floatTrackCurrentTime: "title", floatTrackDuration: "artist"}]],
//		
//};
//specOut(creator, objName, before, params);


describe('songInfoControls tests', function(){
	var creator = function (){
				return new kindSongInfoControls();
			};
	var songInfoControls;
	beforeEach(function(){
		songInfoControls = creator();
		var before = function (obj, callbacks) {
		obj.startWatch=function(){};
	};
		var callback = function(one, fun){return fun;};
		before(songInfoControls,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof songInfoControls.controls).toBe('object');
		expect(typeof songInfoControls.children).toBe('object');
		expect(songInfoControls._componentNameMap).toEqual({"vFlexBox":2,"control":4});
		expect(typeof songInfoControls.$).toBe('object');
		expect(songInfoControls.domStyles).toEqual({});
		expect(songInfoControls.domAttributes).toEqual({"id":"kindSongInfoControls"});
		expect(typeof songInfoControls.owner).toBe('object');
		expect(songInfoControls.name).toEqual("kindSongInfoControls");
		expect(songInfoControls.id).toEqual("kindSongInfoControls");
		expect(songInfoControls.controlParent).toEqual(undefined);
		expect(typeof songInfoControls.layout).toBe('object');
		expect(songInfoControls.kind).toEqual("HFlexBox");
		expect(songInfoControls.events).toEqual({"onSetPlaybackTime":""});
		expect(songInfoControls._boolUpdateSlider).toEqual(true);
		expect(songInfoControls._boolUpdateTimeDisplay).toEqual(true);
		expect(songInfoControls._intCurrentDuration).toEqual(0);
		expect(songInfoControls.kindName).toEqual("kindSongInfoControls");
		expect(songInfoControls.kindComponents).toEqual([{"name":"imgAlbumArt","kind":"Image","src":"images/imgAlbumArt.png","className":"imgAlbumArt","showing":false},{"kind":"VFlexBox","className":"songInfoContainter","components":[{"kind":"Control","className":"sliderContainer","components":[{"name":"sliderSongTime","kind":"ProgressSlider","className":"sliderSongTime","onChange":"onChange_sliderSongTime","onChanging":"onChanging_sliderSongTime","tapPosition":true},{"name":"lblArtistName","content":"&nbsp;","className":"songInfoLabel","showing":true},{"name":"lblSongTime","content":"--:--","className":"timeDisplay","style":"position: fixed; bottom: 41px; width: 40px; left: 230px; background-color: none;"},{"content":"/","className":"timeDisplay","style":"z-index: 105;","showing":false},{"kind":"Control","style":"position: fixed; bottom: 41px; width: 40px; right: 270px; background-color: none; text-align: right;","components":[{"name":"lblSongDuration","content":"--:--","className":"timeDisplay"}]}]}]}]);
		expect(songInfoControls.onSetPlaybackTime).toEqual("");
		expect(songInfoControls.published).toEqual({"layoutKind":""});
		expect(songInfoControls.controlParentName).toEqual("client");
		expect(songInfoControls.defaultKind).toEqual("Control");
		expect(songInfoControls.onclick).toEqual("");
		expect(songInfoControls.onmousedown).toEqual("");
		expect(songInfoControls.onmouseup).toEqual("");
		expect(songInfoControls.style).toEqual("");
		expect(songInfoControls.className).toEqual("");
		expect(songInfoControls.container).toEqual(null);
		expect(songInfoControls.parent).toEqual(null);
		expect(songInfoControls.generated).toEqual(false);
		expect(songInfoControls.allowHtml).toEqual(false);
		expect(songInfoControls.content).toEqual("");
		expect(songInfoControls.nodeTag).toEqual("div");
		expect(songInfoControls.node).toEqual(null);
		expect(songInfoControls.showing).toEqual(true);
		expect(songInfoControls.prepend).toEqual(false);
		expect(songInfoControls.wantsEvents).toEqual(true);
	});
	describe('test (startWatch)', function(){
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(songInfoControls,'inherited');
			songInfoControls.create();
			expect(songInfoControls.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (updateTrackInfoDisplay)', function(){
		describe('testing (updateTrackInfoDisplay) with params [{"strTrackTitle":"title","strTrackArtist":"artist","strTrackAlbum":"album"}]', function(){
			it("should call this.$.lblArtistName.setContent", function(){
				spyOn(songInfoControls.$.lblArtistName, "setContent");
				songInfoControls.updateTrackInfoDisplay({"strTrackTitle":"title","strTrackArtist":"artist","strTrackAlbum":"album"});
				expect(songInfoControls.$.lblArtistName.setContent).toHaveBeenCalled();
			});
			it("should set _boolUpdateSlider", function(){
				songInfoControls._boolUpdateSlider = false;
				songInfoControls.updateTrackInfoDisplay({"strTrackTitle":"title","strTrackArtist":"artist","strTrackAlbum":"album"});
				expect(songInfoControls._boolUpdateSlider).toBe(true);
			});
			it("should set _boolUpdateSlider", function(){
				songInfoControls._boolUpdateTimeDisplay = false;
				songInfoControls.updateTrackInfoDisplay({"strTrackTitle":"title","strTrackArtist":"artist","strTrackAlbum":"album"});
				expect(songInfoControls._boolUpdateTimeDisplay).toBe(true);
			});
		
		
		
		});
	});
	describe('test (updateTrackTimeDisplay)', function(){
	describe('testing (updateTrackTimeDisplay) with params [{"floatTrackCurrentTime":"title","floatTrackDuration":"artist"}]', function(){
		describe("when _boolUpdateTimeDisplay is true", function(){
			beforeEach(function(){
				songInfoControls._boolUpdateTimeDisplay = true;
			});
			it("should call formatTime", function(){
				spyOn(Utilities, "formatTime");
				songInfoControls.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
				expect(Utilities.formatTime).toHaveBeenCalledWith("title");
			});
			it("should call setContent", function(){
				spyOn(songInfoControls.$.lblSongTime, "setContent");
				songInfoControls.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
				expect(songInfoControls.$.lblSongTime.setContent).toHaveBeenCalled();
			});
			it('should change _intCurrentDuration',function(){
				songInfoControls._intCurrentDuration = false;
				songInfoControls.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
				expect(songInfoControls._intCurrentDuration).toEqual("artist");
			});
			it("should call setContent", function(){
				spyOn(songInfoControls.$.lblSongDuration, "setContent");
				songInfoControls.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
				expect(songInfoControls.$.lblSongDuration.setContent).toHaveBeenCalled();
			});
		});
		describe("when _boolUpdateTimeDisplay is false", function(){
			beforeEach(function(){
				songInfoControls._boolUpdateTimeDisplay = false;
			});
			it("shouldnt call formatTime", function(){
				spyOn(Utilities, "formatTime");
				songInfoControls.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
				expect(Utilities.formatTime).not.toHaveBeenCalled();
			});
			it("shouldnt call setContent", function(){
				spyOn(songInfoControls.$.lblSongTime, "setContent");
				songInfoControls.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
				expect(songInfoControls.$.lblSongTime.setContent).not.toHaveBeenCalled();
			});
			it('shouldnt change _intCurrentDuration',function(){
				var prior = songInfoControls._intCurrentDuration;
				songInfoControls.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
				expect(songInfoControls._intCurrentDuration).toEqual(prior);
			});
			it("shouldnt call setContent", function(){
				spyOn(songInfoControls.$.lblSongDuration, "setContent");
				songInfoControls.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
				expect(songInfoControls.$.lblSongDuration.setContent).not.toHaveBeenCalled();
			});
		});
		
		describe("when _boolUpdateSlider is true", function(){
			beforeEach(function(){
				songInfoControls._boolUpdateSlider = true;
			});
			it("should call songInfoControls.$.sliderSongTime.setLockBar", function(){
				spyOn(songInfoControls.$.sliderSongTime, "setLockBar");
				songInfoControls.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
				expect(songInfoControls.$.sliderSongTime.setLockBar).toHaveBeenCalledWith(true);
			});
			it("should call songInfoControls.$.sliderSongTime.setPositionImmediate", function(){
				spyOn(songInfoControls.$.sliderSongTime, "setPositionImmediate");
				songInfoControls.updateTrackTimeDisplay({"floatTrackCurrentTime":12, "floatTrackDuration":34});
				expect(songInfoControls.$.sliderSongTime.setPositionImmediate).toHaveBeenCalledWith(35);
			});
		});
		
		describe("when _boolUpdateSlider is false", function(){
			beforeEach(function(){
				songInfoControls._boolUpdateSlider = false;
			});
			it("shouldnt call songInfoControls.$.sliderSongTime.setLockBar", function(){
				spyOn(songInfoControls.$.sliderSongTime, "setLockBar");
				songInfoControls.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
				expect(songInfoControls.$.sliderSongTime.setLockBar).not.toHaveBeenCalled();
			});
			it("shouldnt call songInfoControls.$.sliderSongTime.setPositionImmediate", function(){
				spyOn(songInfoControls.$.sliderSongTime, "setPositionImmediate");
				songInfoControls.updateTrackTimeDisplay({"floatTrackCurrentTime":12, "floatTrackDuration":34});
				expect(songInfoControls.$.sliderSongTime.setPositionImmediate).not.toHaveBeenCalled();
			});
		});
	
	});
	});
	describe('test (onChange_sliderSongTime)', function(){
		it('should change _boolUpdateSlider',function(){
			spyOn(songInfoControls,'updateSliderSongTime');
			spyOn(songInfoControls.$.sliderSongTime,'setPosition');			
			songInfoControls._boolUpdateSlider = true;
			songInfoControls.onChange_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(songInfoControls._boolUpdateSlider).toEqual(false);
		});
		it('should change _boolUpdateTimeDisplay',function(){
			songInfoControls._boolUpdateTimeDisplay = false;
			songInfoControls.onChange_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(songInfoControls._boolUpdateTimeDisplay).toEqual(true);
		});
		it('should call this.$.sliderSongTime.setPosition',function(){
			spyOn(songInfoControls.$.sliderSongTime,'setPosition');
			songInfoControls.onChange_sliderSongTime(null,6);
			expect(songInfoControls.$.sliderSongTime.setPosition).toHaveBeenCalledWith(6);
		});
		it('should call updateSliderSongTime',function(){
			spyOn(songInfoControls,'updateSliderSongTime');
			songInfoControls.onChange_sliderSongTime(null,6);
			expect(songInfoControls.updateSliderSongTime).toHaveBeenCalledWith(6);
		});
	});
	describe('test (updateSliderSongTime)', function(){
		it('should change _boolUpdateSlider',function(){
			songInfoControls._boolUpdateSlider = false;
			songInfoControls.updateSliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(songInfoControls._boolUpdateSlider).toEqual(true);
		});
		it('should change _boolUpdateSlider',function(){
			songInfoControls._boolUpdateSlider = false;
			songInfoControls.updateSliderSongTime();
			expect(songInfoControls._boolUpdateSlider).toEqual(true);
		});
		it('should call doSetPlaybackTime',function(){
			spyOn(songInfoControls,'doSetPlaybackTime');
			songInfoControls.updateSliderSongTime();
			expect(songInfoControls.doSetPlaybackTime).toHaveBeenCalledWith(undefined);
		});
	});
	describe('test (onChanging_sliderSongTime)', function(){
		it('should change _boolUpdateSlider',function(){
			songInfoControls._boolUpdateSlider = true;
			songInfoControls.onChanging_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(songInfoControls._boolUpdateSlider).toEqual(false);
		});
		it('should change _boolUpdateTimeDisplay',function(){
			songInfoControls._boolUpdateTimeDisplay = true;
			songInfoControls.onChanging_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(songInfoControls._boolUpdateTimeDisplay).toEqual(false);
		});
		it('should call $.lblSongTime.setContent',function(){
			spyOn(songInfoControls.$.sliderSongTime,'setPosition');
			songInfoControls.onChanging_sliderSongTime(undefined, 5);
			expect(songInfoControls.$.sliderSongTime.setPosition).toHaveBeenCalledWith(5);
		});
		it('should call $.lblSongTime.setContent',function(){
			spyOn(songInfoControls.$.lblSongTime,'setContent');
			songInfoControls.onChanging_sliderSongTime();
			expect(songInfoControls.$.lblSongTime.setContent).toHaveBeenCalled();
		});

		it('should call Utilities.formatTime',function(){
			spyOn(Utilities,'formatTime');
			songInfoControls.onChanging_sliderSongTime();
			expect(Utilities.formatTime).toHaveBeenCalled();
		});
		it('should call doSetPlaybackTime',function(){
			spyOn(songInfoControls,'doSetPlaybackTime');
			songInfoControls.onChanging_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(songInfoControls.doSetPlaybackTime).toHaveBeenCalledWith({"rowIndex":0,"dispatchTarget":null});
		});
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(songInfoControls,'_constructor');
			songInfoControls.base();
			expect(songInfoControls._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(songInfoControls,'constructed');
			songInfoControls.base();
			expect(songInfoControls.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(songInfoControls,'_constructor');
			songInfoControls.ctor();
			expect(songInfoControls._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(songInfoControls,'constructed');
			songInfoControls.ctor();
			expect(songInfoControls.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doSetPlaybackTime)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(songInfoControls,'dispatchIndirectly');
			songInfoControls.doSetPlaybackTime();
			expect(songInfoControls.dispatchIndirectly).toHaveBeenCalledWith("onSetPlaybackTime",{});
		});
	});
	describe('test (layoutKind)', function(){
		it('should call _constructor',function(){
			spyOn(songInfoControls,'_constructor');
			songInfoControls.layoutKind();
			expect(songInfoControls._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(songInfoControls,'constructed');
			songInfoControls.layoutKind();
			expect(songInfoControls.constructed).toHaveBeenCalled();
		});
	});
	describe('test (destroy)', function(){
		it('should call unregisterEvents',function(){
			spyOn(songInfoControls,'unregisterEvents');
			songInfoControls.destroy();
			expect(songInfoControls.unregisterEvents).toHaveBeenCalled();
		});
		it('should call destroyControls',function(){
			spyOn(songInfoControls,'destroyControls');
			songInfoControls.destroy();
			expect(songInfoControls.destroyControls).toHaveBeenCalled();
		});
		it('should call inherited',function(){
			spyOn(songInfoControls,'inherited');
			songInfoControls.destroy();
			expect(songInfoControls.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(songInfoControls,'inherited');
			songInfoControls.importProps();
			expect(songInfoControls.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
		it('should call createChrome',function(){
			spyOn(songInfoControls,'createChrome');
			songInfoControls.initComponents();
			expect(songInfoControls.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(songInfoControls,'inherited');
			songInfoControls.initComponents();
			expect(songInfoControls.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(songInfoControls,'inherited');
			songInfoControls.createComponents();
			expect(songInfoControls.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(songInfoControls,'discoverControlParent');
			songInfoControls.createComponents();
			expect(songInfoControls.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(songInfoControls,'createComponents');
			songInfoControls.createChrome();
			expect(songInfoControls.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});