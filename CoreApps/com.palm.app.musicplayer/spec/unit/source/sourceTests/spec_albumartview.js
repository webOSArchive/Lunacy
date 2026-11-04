/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return new ScrollTracks();
//			}
//var objName = "scrollTracks";
//
//var before = function (obj, callbacks) {
//		obj.startWatch=function(){};
//	}
//var event = [[null, {rowIndex:0}]];
//var params = {
//		
//};
//
//specOut(creator, objName, before, params);

describe('scrollTracks tests', function(){
	var creator = function (){
				return new ScrollTracks();
			};
	var scrollTracks;
	beforeEach(function(){
		scrollTracks = creator();
		var before = function (obj, callbacks) {
		obj.startWatch=function(){};
	};
		var callback = function(one, fun){return fun;};
		before(scrollTracks,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof scrollTracks.controls).toBe('object');
		expect(typeof scrollTracks.children).toBe('object');
		expect(scrollTracks._componentNameMap).toEqual({});
		expect(typeof scrollTracks.$).toBe('object');
		expect(scrollTracks.domStyles).toEqual({});
		expect(scrollTracks.domAttributes).toEqual({"className":"enyo-scroller","id":"scrollTracks","onscroll":"enyo.bubble(arguments[0])"});
		expect(typeof scrollTracks.owner).toBe('object');
		expect(scrollTracks.name).toEqual("scrollTracks");
		expect(scrollTracks.id).toEqual("scrollTracks");
		expect(typeof scrollTracks.controlParent).toBe('object');
		expect(scrollTracks.scrollH).toEqual(true);
		expect(scrollTracks.scrollTop).toEqual(0);
		expect(scrollTracks.scrollLeft).toEqual(0);
		expect(scrollTracks.kindName).toEqual("ScrollTracks");
		expect(scrollTracks.published).toEqual({"index":0});
		expect(scrollTracks.events).toEqual({"onSnap":"","onSnapFinish":""});
		expect(scrollTracks.layoutKind).toEqual("HFlexLayout");
		expect(scrollTracks.dragSnapWidth).toEqual(0);
		expect(scrollTracks.revealAmount).toEqual(0);
		expect(scrollTracks.onSnap).toEqual("");
		expect(scrollTracks.onSnapFinish).toEqual("");
		expect(scrollTracks.index).toEqual(0);
		expect(scrollTracks.className).toEqual("enyo-scroller");
		expect(scrollTracks.chrome).toEqual([{"name":"client"}]);
		expect(scrollTracks.onScrollStart).toEqual("");
		expect(scrollTracks.onScroll).toEqual("");
		expect(scrollTracks.onScrollStop).toEqual("");
		expect(scrollTracks.autoHorizontal).toEqual(true);
		expect(scrollTracks.autoVertical).toEqual(false);
		expect(scrollTracks.fpsShowing).toEqual(false);
		expect(scrollTracks.accelerated).toEqual(true);
		expect(scrollTracks.preventDragPropagation).toEqual(true);
		expect(scrollTracks.tools).toEqual([{"name":"scroll","kind":"ScrollStrategy"}]);
		//expect(scrollTracks.horizontal).toEqual(false);
		expect(scrollTracks.vertical).toEqual(true);
		expect(scrollTracks.controlParentName).toEqual("client");
		expect(scrollTracks.defaultKind).toEqual("Control");
		expect(scrollTracks.onclick).toEqual("");
		expect(scrollTracks.onmousedown).toEqual("");
		expect(scrollTracks.onmouseup).toEqual("");
		expect(scrollTracks.style).toEqual("");
		expect(scrollTracks.container).toEqual(null);
		expect(scrollTracks.parent).toEqual(null);
		expect(scrollTracks.generated).toEqual(false);
		expect(scrollTracks.allowHtml).toEqual(false);
		expect(scrollTracks.content).toEqual("");
		expect(scrollTracks.nodeTag).toEqual("div");
		expect(scrollTracks.node).toEqual(null);
		expect(scrollTracks.showing).toEqual(true);
		expect(scrollTracks.prepend).toEqual(false);
		expect(scrollTracks.wantsEvents).toEqual(true);
	});
	
	describe('test (kind)', function(){
		it('should call _constructor',function(){
			spyOn(scrollTracks,'_constructor');
			scrollTracks.kind();
			expect(scrollTracks._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(scrollTracks,'constructed');
			scrollTracks.kind();
			expect(scrollTracks.constructed).toHaveBeenCalled();
		});
	});
	describe('test (dragstartHandler)', function(){
		it('should call inherited',function(){
			spyOn(scrollTracks,'inherited');
			scrollTracks.dragstartHandler();
			expect(scrollTracks.inherited).toHaveBeenCalledWith({});
		});
		it('shouldn\'t call  call inherited dragging',function(){
			spyOn(scrollTracks,'inherited');
			scrollTracks.dragging = true;
			var ret = scrollTracks.dragstartHandler();
			expect(ret).toBe(true);
			expect(scrollTracks.inherited).not.toHaveBeenCalled();
		});
		it('shouldn\'t call  call inherited snapping',function(){
			spyOn(scrollTracks,'inherited');
			scrollTracks.snapping = true;
			var ret =  scrollTracks.dragstartHandler();
			expect(ret).toBe(true);
			expect(scrollTracks.inherited).not.toHaveBeenCalled();
		});
	});
	describe('test (dragHandler)', function(){
		it('should call inherited',function(){
			spyOn(scrollTracks,'inherited');
			scrollTracks.dragHandler();
			expect(scrollTracks.inherited).toHaveBeenCalledWith({});
		});
		it('shouldn\'t call inherited snapping',function(){
			spyOn(scrollTracks,'inherited');
			scrollTracks.snapping = true;
			var ret = scrollTracks.dragHandler();
			expect(ret).toBe(true);
			expect(scrollTracks.inherited).not.toHaveBeenCalled();
		});
	});
	describe('test (flickHandler)', function(){
		it('should call inherited',function(){
			spyOn(scrollTracks,'inherited');
			scrollTracks.flickHandler();
			expect(scrollTracks.inherited).toHaveBeenCalledWith({});
		});
		it('shouldn\'t call inherited snapping',function(){
			spyOn(scrollTracks,'inherited');
			scrollTracks.snapping = true;
			var ret = scrollTracks.flickHandler();
			expect(ret).toBe(true);
			expect(scrollTracks.inherited).not.toHaveBeenCalled();
		});
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(scrollTracks,'_constructor');
			scrollTracks.base();
			expect(scrollTracks._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(scrollTracks,'constructed');
			scrollTracks.base();
			expect(scrollTracks.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(scrollTracks,'_constructor');
			scrollTracks.ctor();
			expect(scrollTracks._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(scrollTracks,'constructed');
			scrollTracks.ctor();
			expect(scrollTracks.constructed).toHaveBeenCalled();
		});
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(scrollTracks,'inherited');
			scrollTracks.create();
			expect(scrollTracks.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (layoutKindChanged)', function(){
		it('should call inherited',function(){
			spyOn(scrollTracks,'inherited');
			scrollTracks.layoutKindChanged();
			expect(scrollTracks.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (indexChanged)', function(){
		it('should call calcPos',function(){
			spyOn(scrollTracks,'calcPos');
			scrollTracks.indexChanged();
			expect(scrollTracks.calcPos).toHaveBeenCalledWith(0);
		});
	});
	describe('test (getCurrentPos)', function(){
		it('should call getScrollLeft',function(){
			spyOn(scrollTracks,'getScrollLeft');
			scrollTracks.getCurrentPos();
			expect(scrollTracks.getScrollLeft).toHaveBeenCalled();
		});
	});
	describe('test (scrollStart)', function(){
		it('should call inherited',function(){
			spyOn(scrollTracks,'inherited');
			scrollTracks.scrollStart();
			expect(scrollTracks.inherited).toHaveBeenCalledWith({});
		});
		it('should call getCurrentPos',function(){
			spyOn(scrollTracks,'getCurrentPos');
			scrollTracks.scrollStart();
			expect(scrollTracks.getCurrentPos).toHaveBeenCalled();
		});
	});
	describe('test (scroll)', function(){
		it('should call inherited',function(){
			spyOn(scrollTracks,'inherited');
			scrollTracks.scroll();
			expect(scrollTracks.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (scrollStop)', function(){
	});
	describe('test (snapFinish)', function(){
		it('should call doSnapFinish',function(){
			spyOn(scrollTracks,'doSnapFinish');
			scrollTracks.snapFinish();
			expect(scrollTracks.doSnapFinish).toHaveBeenCalled();
		});
	});
	describe('test (snapScrollTo)', function(){
		it('should change snapping',function(){
			scrollTracks.snapScrollTo();
			expect(scrollTracks.snapping).toEqual(true);
		});
		it('should call scrollTo',function(){
			spyOn(scrollTracks,'scrollTo');
			scrollTracks.snapScrollTo();
			expect(scrollTracks.scrollTo).toHaveBeenCalledWith(0,undefined);
		});
	});
	describe('test (scrollToDirect)', function(){
		it('should call calcBoundaries',function(){
			spyOn(scrollTracks,'calcBoundaries');
			scrollTracks.scrollToDirect();
			expect(scrollTracks.calcBoundaries).toHaveBeenCalled();
		});
		it('should call stop',function(){
			spyOn(scrollTracks,'stop');
			scrollTracks.scrollToDirect();
			expect(scrollTracks.stop).toHaveBeenCalled();
		});
		it('should call setScrollPositionDirect',function(){
			spyOn(scrollTracks,'setScrollPositionDirect');
			scrollTracks.scrollToDirect();
			expect(scrollTracks.setScrollPositionDirect).toHaveBeenCalledWith(undefined,0);
		});
	});
});


//var creator = function(){
//return new KindAlbumArtView();
//}
//var objName = "albumArtView";
//
//var before = function (obj, callbacks) {
//	obj.startWatch=function(){};
//	//obj.$.scrollTracks = {};
//	//is.$.scrollTracks.setClassName = function(){};
//	obj.$.scrollTracks.destroyControls = function(){};
//}
//var event = [[null, {rowIndex:0}]];
//var params = {
//		gotTracks : [[[{}],{},{}]],
//		changeTrack : [[{intTrackIndex: 2}]]
//};
//
//specOut(creator, objName, before, params);

describe('albumArtView tests', function(){
	var creator = function (){
return new KindAlbumArtView();
};
	var albumArtView;
	beforeEach(function(){
		albumArtView = creator();
		var before = function (obj, callbacks) {
	obj.startWatch=function(){};
	//obj.$.scrollTracks = {};
	//is.$.scrollTracks.setClassName = function(){};
	obj.$.scrollTracks.destroyControls = function(){};
};
		var callback = function(one, fun){return fun;};
		before(albumArtView,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof albumArtView.controls).toBe('object');
		expect(typeof albumArtView.children).toBe('object');
		expect(albumArtView._componentNameMap).toEqual({});
		expect(typeof albumArtView.$).toBe('object');
		expect(albumArtView.domStyles).toEqual({});
		expect(albumArtView.domAttributes).toEqual({"className":"flow","id":"kindAlbumArtView"});
		expect(typeof albumArtView.owner).toBe('object');
		expect(albumArtView.name).toEqual("kindAlbumArtView");
		expect(albumArtView.id).toEqual("kindAlbumArtView");
		expect(albumArtView.controlParent).toEqual(undefined);
		expect(albumArtView.index).toEqual(1);
		expect(albumArtView.kind).toEqual("Control");
		expect(albumArtView.className).toEqual("flow");
		expect(albumArtView.events).toEqual({"onRequestTracks":"","onClickAlbumArtView":""});
		expect(albumArtView._arFullTrackList).toEqual([]);
		expect(albumArtView.intCurrSnapPos).toEqual(0);
		expect(albumArtView.intCurrListMin).toEqual(0);
		expect(albumArtView.intCurrListMax).toEqual(0);
		expect(albumArtView.intCurrListIndex).toEqual(0);
		expect(albumArtView.boolGotTracks).toEqual(false);
		expect(albumArtView.boolScrollerDrawn).toEqual(false);
		expect(albumArtView.intCurrTrackIndex).toEqual(-1);
		expect(albumArtView.boolAudioPlaying).toEqual(false);
		expect(albumArtView.boolUpdateScroller).toEqual(false);
		expect(albumArtView.boolLandscape).toEqual(true);
		expect(albumArtView.kindName).toEqual("KindAlbumArtView");
		expect(albumArtView.kindComponents).toEqual([{"name":"scrollTracks","kind":"ScrollTracks","className":"scrollH","layoutKind":"HFlexLayout","autohorizontal":true,"horizontal":true,"autovertical":false,"vertical":false,"onSnap":"onSnap_scrollTracks","onSnapFinish":"onSnapFinish_scrollTracks","onScrollStart":"onScrollStart_scrollTracks","onScrollStop":"onScrollStop_scrollTracks","showing":true},{"kind":"Control","name":"shadowMask","className":"mask","style":"-webkit-palm-mouse-target:ignore;"}]);
		expect(albumArtView.onRequestTracks).toEqual("");
		expect(albumArtView.onClickAlbumArtView).toEqual("");
		expect(albumArtView.published).toEqual({"layoutKind":""});
		expect(albumArtView.controlParentName).toEqual("client");
		expect(albumArtView.defaultKind).toEqual("Control");
		expect(albumArtView.onclick).toEqual("");
		expect(albumArtView.onmousedown).toEqual("");
		expect(albumArtView.onmouseup).toEqual("");
		expect(albumArtView.layoutKind).toEqual("");
		expect(albumArtView.style).toEqual("");
		expect(albumArtView.container).toEqual(null);
		expect(albumArtView.parent).toEqual(null);
		expect(albumArtView.generated).toEqual(false);
		expect(albumArtView.allowHtml).toEqual(false);
		expect(albumArtView.content).toEqual("");
		expect(albumArtView.nodeTag).toEqual("div");
		expect(albumArtView.node).toEqual(null);
		expect(albumArtView.showing).toEqual(true);
		expect(albumArtView.prepend).toEqual(false);
		expect(albumArtView.wantsEvents).toEqual(true);
	});
	describe('test (startWatch)', function(){
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(albumArtView,'inherited');
			albumArtView.create();
			expect(albumArtView.inherited).toHaveBeenCalledWith({});
		});
		it('should set index to 1',function(){
			albumArtView.index = 0;
			albumArtView.create();
			expect(albumArtView.index).toBe(1);
		});
	});
	describe('test (orientationChanged)', function(){
		it('should change boolLandscape true',function(){
			albumArtView.orientationChanged(true);
			expect(albumArtView.boolLandscape).toEqual(true);
		});
		it('should change boolLandscape false',function(){
			albumArtView.orientationChanged(false);
			expect(albumArtView.boolLandscape).toEqual(false);
		});

		it('should call scrollH',function(){
			spyOn(albumArtView.$.scrollTracks, "setClassName");
			albumArtView.boolLandscape = false;
			albumArtView.orientationChanged(true);
			expect(albumArtView.$.scrollTracks.setClassName).toHaveBeenCalledWith("scrollH");
		});

		it('should call scrollV',function(){
			spyOn(albumArtView.$.scrollTracks, "setClassName");
			albumArtView.orientationChanged(false);
			expect(albumArtView.$.scrollTracks.setClassName).toHaveBeenCalledWith("scrollV");
		});
		it('shouldnt call setClassName when staying the same true',function(){
			spyOn(albumArtView.$.scrollTracks, "setClassName");
			albumArtView.boolLandscape = true;
			albumArtView.orientationChanged(true);
			expect(albumArtView.$.scrollTracks.setClassName).not.toHaveBeenCalled();
		});
		it('shouldnt call setClassName when staying the same false',function(){
			spyOn(albumArtView.$.scrollTracks, "setClassName");
			albumArtView.boolLandscape = false;
			albumArtView.orientationChanged(false);
			expect(albumArtView.$.scrollTracks.setClassName).not.toHaveBeenCalled();
		});
	});
	describe('test (populateScroller)', function(){
		it('should change intCurrListIndex',function(){
			albumArtView.populateScroller();
			expect(albumArtView.intCurrListIndex).toEqual(undefined);
		});
		it('should change intCurrListIndex 4',function(){
			albumArtView.populateScroller(4);
			expect(albumArtView.intCurrListIndex).toEqual(4);
		});
		describe("checking intCurrListMin", function(){
			it('should change intCurrListMin -1',function(){
				albumArtView.populateScroller(1);
				expect(albumArtView.intCurrListMin).toEqual(-1);
			});

			it('should change intCurrListMin -1',function(){
				albumArtView.populateScroller(5);
				expect(albumArtView.intCurrListMin).toEqual(-1);
			});

			it('should change intCurrListMin 0',function(){
				albumArtView.populateScroller(6);
				expect(albumArtView.intCurrListMin).toEqual(0);
			});

			it('should change intCurrListMin 1',function(){
				albumArtView.populateScroller(7);
				expect(albumArtView.intCurrListMin).toEqual(1);
			});
		});
		it('should call clearScroller',function(){
			spyOn(albumArtView,'clearScroller');
			albumArtView.populateScroller();
			expect(albumArtView.clearScroller).toHaveBeenCalled();
		});
		
		describe("when needing to add more albumartitems", function(){
			beforeEach(function(){
				albumArtView._arFullTrackList = [{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{}]
			});
			it("should create components", function(){
				spyOn(albumArtView,'createComponent');
				albumArtView.populateScroller(10);
				expect(albumArtView.createComponent).toHaveBeenCalled();
			});
			it("should create components on scrollTracks", function(){
				spyOn(albumArtView.$.scrollTracks,'createComponent');
				albumArtView.populateScroller(5);
				expect(albumArtView.$.scrollTracks.createComponent).toHaveBeenCalled();
			});
			it("should set parent and container when on scrollTracks", function(){
				//can't test due to pitfalls of jasmine
				var obj = {setParent: function(){}, setContainer: function(){}};
				spyOn(albumArtView.$.scrollTracks,'createComponent').andReturn(obj);
				spyOn(obj, "setParent");
				spyOn(obj, "setContainer");
				albumArtView.populateScroller(5);
				expect(obj.setParent).toHaveBeenCalled();
				expect(obj.setContainer).toHaveBeenCalled();
			});
			it("should set parent and container when on component", function(){
				//can't test due to pitfalls of jasmine
				var obj = {setParent: function(){}, setContainer: function(){}};
				spyOn(albumArtView,'createComponent').andReturn(obj);
				spyOn(obj, "setParent");
				spyOn(obj, "setContainer");
				albumArtView.populateScroller(10);
				expect(obj.setParent).toHaveBeenCalled();
				expect(obj.setContainer).toHaveBeenCalled();
			});
		});
		
		it("should call contentChanged", function(){
			spyOn(albumArtView.$.scrollTracks,'contentChanged');
			albumArtView.populateScroller(5);
			expect(albumArtView.$.scrollTracks.contentChanged).toHaveBeenCalled();
		});
		
		it("should change boolUpdateScroller", function(){
			albumArtView.boolUpdateScroller = true;
			spyOn(albumArtView.$.scrollTracks,'contentChanged');
			albumArtView.populateScroller(5);
			expect(albumArtView.boolUpdateScroller).toBe(false);
		});
		describe("check intCurrSnapPos", function(){
			it('should change intCurrSnapPos 5',function(){
				albumArtView.populateScroller(6);
				expect(albumArtView.intCurrSnapPos).toEqual(5);
			});
			it('should change intCurrSnapPos 4',function(){
				albumArtView.populateScroller(5);
				expect(albumArtView.intCurrSnapPos).toEqual(5);
			});
			it('should change intCurrSnapPos 2',function(){
				albumArtView.populateScroller(2);
				expect(albumArtView.intCurrSnapPos).toEqual(2);
			});
			it('should change intCurrSnapPos 1',function(){
				albumArtView.populateScroller(1);
				expect(albumArtView.intCurrSnapPos).toEqual(1);
			});
			it('should change intCurrSnapPos 0',function(){
				albumArtView.populateScroller(0);
				expect(albumArtView.intCurrSnapPos).toEqual(0);
			});
		});
		it("should call setIndex 5", function(){
			spyOn(albumArtView.$.scrollTracks,'setIndex');
			albumArtView.populateScroller(5);
			expect(albumArtView.$.scrollTracks.setIndex).toHaveBeenCalledWith(5);
		});
		it("should call setIndex 6", function(){
			spyOn(albumArtView.$.scrollTracks,'setIndex');
			albumArtView.populateScroller(6);
			expect(albumArtView.$.scrollTracks.setIndex).toHaveBeenCalledWith(5);
		});
		it("should call setIndex -1", function(){
			spyOn(albumArtView.$.scrollTracks,'setIndex');
			albumArtView.populateScroller(-1);
			expect(albumArtView.$.scrollTracks.setIndex).toHaveBeenCalledWith(-1);
		});
		it('should change boolScrollerDrawn',function(){
			albumArtView.boolScrollerDrawn = false;
			albumArtView.populateScroller();
			expect(albumArtView.boolScrollerDrawn).toEqual(true);
		});
		
	});
	describe('test (setScrollerIndex)', function(){
		it("should call setIndex -1", function(){
			spyOn(albumArtView.$.scrollTracks,'setIndex');
			albumArtView.setScrollerIndex();
			expect(albumArtView.$.scrollTracks.setIndex).toHaveBeenCalledWith(5);
		});
	});
	describe('test (onclickAlbumArtItemTest)', function(){
		it('should call doClickAlbumArtView',function(){
			spyOn(albumArtView,'doClickAlbumArtView');
			albumArtView.onclickAlbumArtItemTest(null,{"rowIndex":0,"dispatchTarget":null});
			expect(albumArtView.doClickAlbumArtView).toHaveBeenCalled();
		});
	});
	describe('test (clearScroller)', function(){
		it("should call destroyControls", function(){
			spyOn(albumArtView.$.scrollTracks,'destroyControls').andReturn(true);
			albumArtView.clearScroller();
			expect(albumArtView.$.scrollTracks.destroyControls).toHaveBeenCalledWith();
		});
	});
	describe('test (requestTrackList)', function(){
		
		it('should call doRequestTracks',function(){
			spyOn(albumArtView,'doRequestTracks');
			albumArtView.requestTrackList();
			expect(albumArtView.doRequestTracks).toHaveBeenCalled();
		});
		it('should bind',function(){
			spyOn(enyo,'bind');
			albumArtView.requestTrackList();
			expect(enyo.bind).toHaveBeenCalled();
		});
	});
	describe('test (gotTracks)', function(){
		describe('testing (gotTracks) with params [[{}],{},{}]', function(){
			it('should change _arFullTrackList',function(){
				albumArtView._arFullTrackList = undefined
				albumArtView.gotTracks([{}],{},{});
				expect(albumArtView.intCurrListMin).not.toBe(undefined);
			});
			it('should change boolGotTracks',function(){
				albumArtView.boolGotTracks = false;
				albumArtView.gotTracks([{}],{},{});
				expect(albumArtView.boolGotTracks).toEqual(true);
			});
			it('should change intCurrTrackIndex',function(){
				albumArtView.intCurrTrackIndex = false;
				albumArtView.gotTracks([{}],5,{});
				expect(albumArtView.intCurrTrackIndex).toEqual(5);
			});
			it('should change boolAudioPlaying true',function(){
				albumArtView.boolAudioPlaying = false;
				albumArtView.gotTracks([{}],{},true);
				expect(albumArtView.boolAudioPlaying).toEqual(true);
			});
			it('should change boolAudioPlaying false',function(){
				albumArtView.boolAudioPlaying = true;
				albumArtView.gotTracks([{}],{},false);
				expect(albumArtView.boolAudioPlaying).toEqual(false);
			});
			it('should call populateScroller',function(){
				spyOn(albumArtView,'populateScroller');
				albumArtView.gotTracks([{}],{},{});
				expect(albumArtView.populateScroller).toHaveBeenCalledWith({});
			});
		});
	});
	describe('test (onSnap_scrollTracks)', function(){
		it('should exist',function(){
			albumArtView.onSnap_scrollTracks(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	describe('test (onSnapFinish_scrollTracks)', function(){
		beforeEach(function(){
			albumArtView.$.scrollTracks.children = [{children:[{intTrackIndex:2}]}];
		});
		it('should call clearInterval',function(){
			albumArtView.intervalAnimate = 5;
			spyOn(window, "clearInterval");
			albumArtView.onSnapFinish_scrollTracks(null,{"rowIndex":0,"dispatchTarget":null});
			expect(window.clearInterval).toHaveBeenCalledWith(5);
		});
		it('shouldn\'t call clearInterval if undefnied',function(){
			albumArtView.intervalAnimate = undefined;
			spyOn(window, "clearInterval");
			albumArtView.onSnapFinish_scrollTracks(null,{"rowIndex":0,"dispatchTarget":null});
			expect(window.clearInterval).not.toHaveBeenCalled();
		});
		it('should change intCurrSnapPos',function(){
			albumArtView.intCurrSnapPos = false
			albumArtView.onSnapFinish_scrollTracks({index: 5},{"rowIndex":0,"dispatchTarget":null});
			expect(albumArtView.intCurrSnapPos).toEqual(5);
		});
		describe("when this.boolUpdateScroller = true", function(){
			beforeEach(function(){
				albumArtView.boolUpdateScroller = true;
			});
			it("should call populateScroller", function(){
				spyOn(albumArtView,'populateScroller');
				albumArtView.onSnapFinish_scrollTracks({index: -1},{"rowIndex":0,"dispatchTarget":null});
				expect(albumArtView.populateScroller).toHaveBeenCalled();
			});
			it("should call setItemsDisplay", function(){
				spyOn(albumArtView,'setItemsDisplay');
				albumArtView.onSnapFinish_scrollTracks({index: -1},{"rowIndex":0,"dispatchTarget":null});
				expect(albumArtView.setItemsDisplay).toHaveBeenCalledWith(albumArtView.intCurrSnapPos);
			});
		});
		describe("when this.boolUpdateScroller = false", function(){
			beforeEach(function(){
				albumArtView.boolUpdateScroller = false;
			});
			it("should call populateScroller", function(){
				spyOn(albumArtView,'populateScroller');
				albumArtView.onSnapFinish_scrollTracks({index: -1},{"rowIndex":0,"dispatchTarget":null});
				expect(albumArtView.populateScroller).not.toHaveBeenCalled();
			});
			it("should call setItemsDisplay", function(){
				spyOn(albumArtView,'setItemsDisplay');
				albumArtView.onSnapFinish_scrollTracks({index: -1},{"rowIndex":0,"dispatchTarget":null});
				expect(albumArtView.setItemsDisplay).not.toHaveBeenCalled();
			});
		});
	
		
		
		it('should change boolUpdateScroller true',function(){
			this.boolUpdateScroller = false;
			albumArtView.onSnapFinish_scrollTracks(null,{"rowIndex":0,"dispatchTarget":null});
			expect(albumArtView.boolUpdateScroller).toEqual(true);
		});
	});
	describe('test (setItemsDisplay)', function(){
		
		it('should return true',function(){
			var ret = albumArtView.setItemsDisplay();
			expect(ret).toBe(true);
		});
	});
	describe('test (changeTrack)', function(){
		beforeEach(function(){
			albumArtView.intCurrSnapPos = -1;
			albumArtView.$.scrollTracks.children = [{children: [{intTrackIndex: 2}]}];
			albumArtView.updatePlayPauseItems = function(){};
		});
		describe('testing (changeTrack) with params [{"intTrackIndex":2}]', function(){
			it('should change intCurrTrackIndex',function(){
				albumArtView.changeTrack({"intTrackIndex":2});
				expect(albumArtView.intCurrTrackIndex).toEqual(2);
			});
			describe("when boolGotTracks is true", function(){
				beforeEach(function(){
					albumArtView.boolGotTracks = true;
				});
				it("should set boolUpdateScroller to true", function(){
					albumArtView.boolUpdateScroller = false;
					albumArtView.changeTrack({"intTrackIndex":2});
					expect(albumArtView.boolUpdateScroller).toBe(true);
				});
				it("should call snapTo 2", function(){
					spyOn(albumArtView.$.scrollTracks,'snapTo');
					albumArtView.changeTrack({"intTrackIndex":2});
					expect(albumArtView.$.scrollTracks.snapTo).toHaveBeenCalledWith(-1);
				});
				it("should call snapTo 4", function(){
					spyOn(albumArtView.$.scrollTracks,'snapTo');
					albumArtView.changeTrack({"intTrackIndex":4});
					expect(albumArtView.$.scrollTracks.snapTo).toHaveBeenCalledWith(1);
				});
				it("should call updatePlayPauseItems 4", function(){
					spyOn(albumArtView,'updatePlayPauseItems');
					albumArtView.changeTrack({"intTrackIndex":4});
					expect(albumArtView.updatePlayPauseItems).toHaveBeenCalledWith(undefined, 4);
				});
				it("should call updatePlayPauseItems 2", function(){
					spyOn(albumArtView,'updatePlayPauseItems');
					albumArtView.changeTrack({"intTrackIndex":2});
					expect(albumArtView.updatePlayPauseItems).toHaveBeenCalledWith(undefined, 2);
				});
				it("should call populateScroller", function(){
					spyOn(albumArtView,'populateScroller');
					albumArtView.changeTrack({"intTrackIndex":5});
					expect(albumArtView.populateScroller).toHaveBeenCalledWith(5);
				});
			});
			describe("when boolGotTracks is false", function(){
				beforeEach(function(){
					albumArtView.boolGotTracks = false;
				});
				it("should set boolUpdateScroller to true", function(){
					albumArtView.boolUpdateScroller = false;
					albumArtView.changeTrack({"intTrackIndex":2});
					expect(albumArtView.boolUpdateScroller).toBe(false);
				});
				it("should call snapTo 2", function(){
					spyOn(albumArtView.$.scrollTracks,'snapTo');
					albumArtView.changeTrack({"intTrackIndex":2});
					expect(albumArtView.$.scrollTracks.snapTo).not.toHaveBeenCalled();
				});
				
				it("should call updatePlayPauseItems 2", function(){
					spyOn(albumArtView,'updatePlayPauseItems');
					albumArtView.changeTrack({"intTrackIndex":2});
					expect(albumArtView.updatePlayPauseItems).not.toHaveBeenCalled();
				});
				it("should call populateScroller", function(){
					spyOn(albumArtView,'populateScroller');
					albumArtView.changeTrack({"intTrackIndex":5});
					expect(albumArtView.populateScroller).not.toHaveBeenCalled();
				});
			});
			
			
		});
	});
	describe('test (setPlayPause)', function(){
		
		it('should change boolAudioPlaying false',function(){
			albumArtView.boolAudioPlaying = true;
			albumArtView.setPlayPause(false);
			expect(albumArtView.boolAudioPlaying).toEqual(false);
		});
		it('should change boolAudioPlaying false',function(){
			albumArtView.boolAudioPlaying = false;
			albumArtView.setPlayPause(true);
			expect(albumArtView.boolAudioPlaying).toEqual(true);
		});
		it('should change boolAudioPlaying true true',function(){
			albumArtView.boolAudioPlaying = true;
			albumArtView.setPlayPause(true);
			expect(albumArtView.boolAudioPlaying).toEqual(true);
		});
		it('should call updatePlayPauseItems',function(){
			spyOn(albumArtView,'updatePlayPauseItems');
			albumArtView.setPlayPause();
			expect(albumArtView.updatePlayPauseItems).toHaveBeenCalled();
		});
	});
	describe('test (updatePlayPauseItems)', function(){
		it('should change boolAudioPlaying false',function(){
			albumArtView.boolAudioPlaying = true;
			albumArtView.updatePlayPauseItems(false);
			expect(albumArtView.boolAudioPlaying).toEqual(false);
		});
		it('should change boolAudioPlaying false',function(){
			albumArtView.boolAudioPlaying = false;
			albumArtView.updatePlayPauseItems(true);
			expect(albumArtView.boolAudioPlaying).toEqual(true);
		});
		it('should change boolAudioPlaying true true',function(){
			albumArtView.boolAudioPlaying = true;
			albumArtView.updatePlayPauseItems(true);
			expect(albumArtView.boolAudioPlaying).toEqual(true);
		});
		it('should not change boolAudioPlaying to undefined',function(){
			albumArtView.boolAudioPlaying = true;
			albumArtView.updatePlayPauseItems();
			expect(albumArtView.boolAudioPlaying).toEqual(true);
		});
		it("should checkIfCurrentTrack", function(){
			albumArtView.$.scrollTracks.children = [{children: [{checkIfCurrentTrack: function(){}}]}];
			spyOn(albumArtView.$.scrollTracks.children[0].children[0], "checkIfCurrentTrack");
			albumArtView.updatePlayPauseItems(true, 2);
			expect(albumArtView.$.scrollTracks.children[0].children[0].checkIfCurrentTrack).toHaveBeenCalledWith(true, 2);
		});
		it("should checkIfCurrentTrack again", function(){
			albumArtView.$.scrollTracks.children = [{children: [{checkIfCurrentTrack: function(){}}]}];
			spyOn(albumArtView.$.scrollTracks.children[0].children[0], "checkIfCurrentTrack");
			albumArtView.updatePlayPauseItems(false, 3);
			expect(albumArtView.$.scrollTracks.children[0].children[0].checkIfCurrentTrack).toHaveBeenCalledWith(false, 3);
		});
		
	});
	describe('test (onScrollStart_scrollTracks)', function(){
		it('should exist',function(){
			albumArtView.onScrollStart_scrollTracks(null,{"rowIndex":0,"dispatchTarget":null});
		});
	});
	describe('test (onScrollStop_scrollTracks)', function(){
		it('should call clearInterval',function(){
			albumArtView.intervalAnimate = 5;
			spyOn(window, "clearInterval");
			albumArtView.onScrollStop_scrollTracks(null,{"rowIndex":0,"dispatchTarget":null});
			expect(window.clearInterval).toHaveBeenCalledWith(5);
		});
		it('shouldn\'t call clearInterval if undefnied',function(){
			albumArtView.intervalAnimate = undefined;
			spyOn(window, "clearInterval");
			albumArtView.onScrollStop_scrollTracks(null,{"rowIndex":0,"dispatchTarget":null});
			expect(window.clearInterval).not.toHaveBeenCalled();
		});
	});
	describe('test (base)', function(){
	
		it('should call _constructor',function(){
			spyOn(albumArtView,'_constructor');
			albumArtView.base();
			expect(albumArtView._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(albumArtView,'constructed');
			albumArtView.base();
			expect(albumArtView.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		
		it('should call _constructor',function(){
			spyOn(albumArtView,'_constructor');
			albumArtView.ctor();
			expect(albumArtView._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(albumArtView,'constructed');
			albumArtView.ctor();
			expect(albumArtView.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doRequestTracks)', function(){

		it('should call dispatchIndirectly',function(){
			spyOn(albumArtView,'dispatchIndirectly');
			albumArtView.doRequestTracks();
			expect(albumArtView.dispatchIndirectly).toHaveBeenCalledWith("onRequestTracks",{});
		});
	});
	describe('test (doClickAlbumArtView)', function(){
		
		it('should call dispatchIndirectly',function(){
			spyOn(albumArtView,'dispatchIndirectly');
			albumArtView.doClickAlbumArtView();
			expect(albumArtView.dispatchIndirectly).toHaveBeenCalledWith("onClickAlbumArtView",{});
		});
	});
	describe('test (destroy)', function(){
		
		it('should call unregisterEvents',function(){
			spyOn(albumArtView,'unregisterEvents');
			albumArtView.destroy();
			expect(albumArtView.unregisterEvents).toHaveBeenCalled();
		});
		it('should call destroyControls',function(){
			spyOn(albumArtView,'destroyControls');
			albumArtView.destroy();
			expect(albumArtView.destroyControls).toHaveBeenCalled();
		});
		it('should call inherited',function(){
			spyOn(albumArtView,'inherited');
			albumArtView.destroy();
			expect(albumArtView.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
	
		it('should call inherited',function(){
			spyOn(albumArtView,'inherited');
			albumArtView.importProps();
			expect(albumArtView.inherited).toHaveBeenCalledWith({});
		});
	});

	describe('test (initComponents)', function(){
	
		it('should call createChrome',function(){
			spyOn(albumArtView,'createChrome');
			albumArtView.initComponents();
			expect(albumArtView.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(albumArtView,'inherited');
			albumArtView.initComponents();
			expect(albumArtView.inherited).toHaveBeenCalledWith({});
		});
	});

	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(albumArtView,'inherited');
			albumArtView.createComponents();
			expect(albumArtView.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(albumArtView,'discoverControlParent');
			albumArtView.createComponents();
			expect(albumArtView.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(albumArtView,'createComponents');
			albumArtView.createChrome();
			expect(albumArtView.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});

