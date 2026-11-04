/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return new kindPlayerControl();
//			}
//var objName = "playerControl";
//
//var before = function (obj, callbacks) {
//		obj.startWatch=function(){};
//	}
//var event = [[null, {rowIndex:0}]];
//var params = {
//		updateTrackTimeDisplay : [[{floatTrackCurrentTime: 12, floatTrackDuration : 23}]]
//};
//specOut(creator, objName, before, params);


describe('playerControl tests', function(){
	var creator = function (){
				return new kindPlayerControl();
			};
	var playerControl;
	beforeEach(function(){
		playerControl = creator();
		var before = function (obj, callbacks) {
		obj.startWatch=function(){};
	};
		var callback = function(one, fun){return fun;};
		before(playerControl,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof playerControl.controls).toBe('object');
		expect(typeof playerControl.children).toBe('object');
//		expect(playerControl._componentNameMap).toEqual({"control":5,"spacer":3,"hFlexBox":2});
		expect(typeof playerControl.$).toBe('object');
		expect(playerControl.domStyles).toEqual({"height":"80px"});
		expect(playerControl.domAttributes).toEqual({"className":"enyo-toolbar enyo-hflexbox controls","id":"kindPlayerControl"});
		expect(typeof playerControl.owner).toBe('object');
		expect(playerControl.name).toEqual("kindPlayerControl");
		expect(playerControl.id).toEqual("kindPlayerControl");
		expect(playerControl.controlParent).toEqual(undefined);
		expect(typeof playerControl.layout).toBe('object');
		expect(playerControl.kind).toEqual("Toolbar");
		expect(playerControl.height).toEqual("80px");
		expect(playerControl.className).toEqual("enyo-toolbar enyo-hflexbox controls");
		//expect(playerControl.events).toEqual({"onClickPrev":"","onClickPlayPause":"","onClickNext":"","onShuffleClick":"","onRepeatClick":"","onSetVolume":"","onRequestVolume":"","onSetPlaybackTime":"","onClickFullScreen":""});
		expect(playerControl.published).toEqual({"boolDraggingSlider": false,"playEnabled":false,"prevNextEnabled":false});
		expect(playerControl._boolUpdateSlider).toEqual(true);
		expect(playerControl._boolUpdateTimeDisplay).toEqual(true);
		expect(playerControl._boolUpdateVolumeSlider).toEqual(true);
		expect(playerControl._intCurrentDuration).toEqual(0);
		expect(playerControl.kindName).toEqual("kindPlayerControl");
		//expect(playerControl.kindComponents).toEqual([{"kind":"Control","className":"playback","layoutKind":"HFlexLayout","pack":"start","align":"center","components":[{"name":"btnPrev","kind":"IconButton","className":"prev","icon":"images/btn_controls_prev.png","onclick":"onclick_prev","disabled":true},{"name":"btnPlay","kind":"IconButton","className":"play paused","icon":"images/btn_controls_play.png","label":" ","onclick":"onclick_playpause","disabled":true},{"name":"btnNext","kind":"IconButton","className":"next","icon":"images/btn_controls_next.png","onclick":"onclick_next","disabled":true}]},{"kind":"Spacer","style":"max-width:25px;"},{"kind":"Control","name":"centerBox","flex":1,"align":"center","pack":"justify","components":[{"kind":"Control","name":"innerBox","style":"width: 250px; height: 68px; overflow: hidden;","align":"center","components":[{"kind":"HFlexBox","align":"center","className":"current","pack":"justify","components":[{"name":"btnRepeat","kind":"Control","className":"toggleMode repeat","onclick":"doRepeatClick"},{"kind":"Control","pack":"justify","align":"center","flex":1,"className":"info","components":[{"name":"lblArtistName","content":"","className":"artist"},{"name":"lblSongTitle","content":"","className":"title"}]},{"name":"btnShuffle","kind":"Control","className":"toggleMode shuffle","onclick":"doShuffleClick"}]},{"kind":"Control","layoutKind":"HFlexLayout","className":"progress","pack":"center","components":[{"name":"lblSongTime","style":"width: 67px; height: 14px; overflow: hidden;","kind":"Control","className":"label elapsed","content":"--:--"},{"name":"sliderSongTime","kind":"ProgressSlider","onChange":"onChange_sliderSongTime","onChanging":"onChanging_sliderSongTime","onclick":"onClick_sliderSongTime","animatePosition":false,"tapPosition":true,"lockBar":true,"position":0,"flex":1},{"name":"lblSongDuration","kind":"Control","className":"label duration","content":"--:--"}]}]}]},{"kind":"Spacer","style":"max-width:15px;"},{"kind":"Control","width":"190px","height":"64px","style":"overflow: hidden;","align":"end","components":[{"name":"btnFullscreen","kind":"Control","style":"position: relative; left: 140px;","className":"toggleMode fullscreen","onclick":"onclick_FullScreen","onmousedown":"onmousedown_FullScreen"},{"name":"sliderVolume","kind":"ProgressSlider","lockBar":true,"position":0,"width":"142px","className":"volume","onChange":"onChange_sliderVolume","onChanging":"onChanging_sliderVolume"}]}]);
		expect(playerControl.onClickPrev).toEqual("");
		expect(playerControl.onClickPlayPause).toEqual("");
		expect(playerControl.onClickNext).toEqual("");
		expect(playerControl.onShuffleClick).toEqual("");
		expect(playerControl.onRepeatClick).toEqual("");
		expect(playerControl.onSetVolume).toEqual("");
		expect(playerControl.onRequestVolume).toEqual("");
		expect(playerControl.onSetPlaybackTime).toEqual("");
		expect(playerControl.onClickFullScreen).toEqual("");
		expect(playerControl.playEnabled).toEqual(false);
		expect(playerControl.prevNextEnabled).toEqual(false);
		expect(playerControl.pack).toEqual("center");
		expect(playerControl.align).toEqual("center");
		expect(playerControl.defaultKind).toEqual("ToolButton");
		expect(playerControl.controlParentName).toEqual("client");
		expect(playerControl.onclick).toEqual("");
		expect(playerControl.onmousedown).toEqual("");
		expect(playerControl.onmouseup).toEqual("");
		expect(playerControl.style).toEqual("");
		expect(playerControl.container).toEqual(null);
		expect(playerControl.parent).toEqual(null);
		expect(playerControl.generated).toEqual(false);
		expect(playerControl.allowHtml).toEqual(false);
		expect(playerControl.content).toEqual("");
		expect(playerControl.nodeTag).toEqual("div");
		expect(playerControl.node).toEqual(null);
		expect(playerControl.showing).toEqual(true);
		expect(playerControl.prepend).toEqual(false);
		expect(playerControl.wantsEvents).toEqual(true);
	});
	describe('test (startWatch)', function(){
	});
	describe('test (resized)', function(){
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(playerControl,'inherited');
			playerControl.create();
			expect(playerControl.inherited).toHaveBeenCalledWith({});
		});
		it('should call doRequestVolume',function(){
			spyOn(playerControl,'doRequestVolume');
			playerControl.create();
			expect(playerControl.doRequestVolume).toHaveBeenCalled();
		});
		it('should call resized',function(){
			spyOn(playerControl,'resized');
			playerControl.create();
			expect(playerControl.resized).toHaveBeenCalled();
		});
	});
	describe('test (onclick_next)', function(){
		it('should call doClickNext',function(){
			spyOn(playerControl,'doClickNext');
			playerControl.onclick_next(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl.doClickNext).toHaveBeenCalled();
		});
	});
	describe('test (onclick_prev)', function(){
		it('should call doClickPrev',function(){
			spyOn(playerControl,'doClickPrev');
			playerControl.onclick_prev(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl.doClickPrev).toHaveBeenCalled();
		});
	});
	describe('test (onclick_playpause)', function(){
		it('should call doClickPlayPause',function(){
			spyOn(playerControl,'doClickPlayPause');
			playerControl.onclick_playpause(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl.doClickPlayPause).toHaveBeenCalled();
		});
	});
	describe('test (onclick_FullScreen)', function(){
		it('should call doClickFullScreen',function(){
			spyOn(playerControl,'doClickFullScreen');
			playerControl.onclick_FullScreen(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl.doClickFullScreen).toHaveBeenCalled();
		});
	});
	describe('test (onmousedown_FullScreen)', function(){
		it('should call his.$.btnFullscreen.addClass',function(){
			spyOn(playerControl.$.btnFullscreen,'addClass');
			playerControl.onmousedown_FullScreen(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl.$.btnFullscreen.addClass).toHaveBeenCalledWith("blue");
		});
	});
	describe('test (playEnabledChanged)', function(){
		it('should call his.$.btnPlay.addClass false',function(){
			playerControl.playEnabled = true;
			spyOn(playerControl.$.btnPlay,'setDisabled');
			playerControl.playEnabledChanged();
			expect(playerControl.$.btnPlay.setDisabled).toHaveBeenCalledWith(false);
		});
		it('should call his.$.btnPlay.addClass true',function(){
			playerControl.playEnabled = false;
			spyOn(playerControl.$.btnPlay,'setDisabled');
			playerControl.playEnabledChanged();
			expect(playerControl.$.btnPlay.setDisabled).toHaveBeenCalledWith(true);
		});
	});
	describe('test (prevNextEnabledChanged)', function(){
		describe("when prevNextEnabled is true", function(){
			beforeEach(function(){
				playerControl.prevNextEnabled = true;
			});
			it('should call his.$.btnPrev.addClass true',function(){
				spyOn(playerControl.$.btnPrev,'setDisabled');
				playerControl.prevNextEnabledChanged();
				expect(playerControl.$.btnPrev.setDisabled).toHaveBeenCalledWith(false);
			});
			it('should call his.$.btnNext.addClass true',function(){
				spyOn(playerControl.$.btnNext,'setDisabled');
				playerControl.prevNextEnabledChanged();
				expect(playerControl.$.btnNext.setDisabled).toHaveBeenCalledWith(false);
			});
		});
		describe("when prevNextEnabled is false", function(){
			beforeEach(function(){
				playerControl.prevNextEnabled = false;
			});
			beforeEach(function(){
				playerControl.prevNextEnabled = false;
			});
			it('should call his.$.btnPrev.addClass true',function(){
				spyOn(playerControl.$.btnPrev,'setDisabled');
				playerControl.prevNextEnabledChanged();
				expect(playerControl.$.btnPrev.setDisabled).toHaveBeenCalledWith(true);
			});
			it('should call his.$.btnNext.addClass true',function(){
				spyOn(playerControl.$.btnNext,'setDisabled');
				playerControl.prevNextEnabledChanged();
				expect(playerControl.$.btnNext.setDisabled).toHaveBeenCalledWith(true);
			});
		});
	});
	describe('test (setPlayPause)', function(){
		it('should call his.$.btnPlay.addRemoveClass true',function(){
			spyOn(playerControl.$.btnPlay,'addRemoveClass');
			playerControl.setPlayPause(false);
			expect(playerControl.$.btnPlay.addRemoveClass).toHaveBeenCalledWith("paused", true);
		});
		it('should call his.$.btnPlay.addRemoveClass false',function(){
			spyOn(playerControl.$.btnPlay,'addRemoveClass');
			playerControl.setPlayPause(true);
			expect(playerControl.$.btnPlay.addRemoveClass).toHaveBeenCalledWith("paused", false);
		});
	});
	describe('test (setFullscreen)', function(){
		beforeEach(function(){
			playerControl.$.btnFullscreen.addClass("blue")
			//if the class is not blue, we do a setTimeout to clear it
		});
		it('should call his.$.btnFullscreen.addRemoveClass false',function(){
			spyOn(playerControl.$.btnFullscreen,'addRemoveClass');
			playerControl.setFullscreen(false);
			expect(playerControl.$.btnFullscreen.addRemoveClass).toHaveBeenCalledWith("on", false);
		});
		it('should call his.$.btnFullscreen.removeClass false',function(){
			spyOn(playerControl.$.btnFullscreen,'removeClass');
			playerControl.setFullscreen(false);
			expect(playerControl.$.btnFullscreen.removeClass).toHaveBeenCalledWith("blue");
		});
		
		it('should call his.$.btnFullscreen.addRemoveClass true',function(){
			spyOn(playerControl.$.btnFullscreen,'addRemoveClass');
			playerControl.setFullscreen(true);
			expect(playerControl.$.btnFullscreen.addRemoveClass).toHaveBeenCalledWith("on", true);
		});
		it('should call his.$.btnFullscreen.removeClass true',function(){
			spyOn(playerControl.$.btnFullscreen,'removeClass');
			playerControl.setFullscreen(false);
			expect(playerControl.$.btnFullscreen.removeClass).toHaveBeenCalledWith("blue");
		});
		
		
	});
	describe('test (updateTrackInfoDisplay)', function(){
		describe('testing (updateTrackInfoDisplay) with params [{"strTrackTitle":"title","strTrackArtist":"artist","strTrackAlbum":"album"}]', function(){
			it("should call window.clearinterval", function(){
				playerControl.intervalCheckTrackTime = "blah";
				spyOn(window, "clearInterval");
				playerControl.updateTrackInfoDisplay({"strTrackTitle":"title","strTrackArtist":"artist","strTrackAlbum":"album"});
				expect(window.clearInterval).toHaveBeenCalledWith("blah");
				
			});
			it("shouldnt call window.clearinterval", function(){
				playerControl.intervalCheckTrackTime = undefined;
				spyOn(window, "clearInterval");
				playerControl.updateTrackInfoDisplay({"strTrackTitle":"title","strTrackArtist":"artist","strTrackAlbum":"album"});
				expect(window.clearInterval).not.toHaveBeenCalled();
				
			});
			it("should call this.$.lblSongTitle.setContent", function(){
				spyOn(playerControl.$.lblSongTitle, "setContent");
				playerControl.updateTrackInfoDisplay({"strTrackTitle":"title","strTrackArtist":"artist","strTrackAlbum":"album"});
				expect(playerControl.$.lblSongTitle.setContent).toHaveBeenCalledWith("title");
			});
			
			it("should call this.$.lblArtistName.setContent", function(){
				spyOn(playerControl.$.lblArtistName, "setContent");
				playerControl.updateTrackInfoDisplay({"strTrackTitle":"title","strTrackArtist":"artist","strTrackAlbum":"album"});
				expect(playerControl.$.lblArtistName.setContent).toHaveBeenCalledWith("artist");
			});
			it("should set _boolUpdateSlider", function(){
				playerControl._boolUpdateSlider = false;
				playerControl.updateTrackInfoDisplay({"strTrackTitle":"title","strTrackArtist":"artist","strTrackAlbum":"album"});
				expect(playerControl._boolUpdateSlider).toBe(true);
			});
			it("should set _boolUpdateSlider", function(){
				playerControl._boolUpdateTimeDisplay = false;
				playerControl.updateTrackInfoDisplay({"strTrackTitle":"title","strTrackArtist":"artist","strTrackAlbum":"album"});
				expect(playerControl._boolUpdateTimeDisplay).toBe(true);
			});
		});
	});
	describe('test (updateTrackTimeDisplay)', function(){
		describe('testing (updateTrackTimeDisplay) with params [{"floatTrackCurrentTime":"title","floatTrackDuration":"artist"}]', function(){
			describe("when _boolUpdateTimeDisplay is true", function(){
				beforeEach(function(){
					playerControl._boolUpdateTimeDisplay = true;
				});
				it("should call formatTime", function(){
					spyOn(Utilities, "formatTime");
					playerControl.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
					expect(Utilities.formatTime).toHaveBeenCalledWith("title");
				});
				it("should call setContent", function(){
					spyOn(playerControl.$.lblSongTime, "setContent");
					playerControl.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
					expect(playerControl.$.lblSongTime.setContent).toHaveBeenCalled();
				});
				it('should change _intCurrentDuration',function(){
					playerControl._intCurrentDuration = false;
					playerControl.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
					expect(playerControl._intCurrentDuration).toEqual("artist");
				});
				it("should call setContent", function(){
					spyOn(playerControl.$.lblSongDuration, "setContent");
					playerControl.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
					expect(playerControl.$.lblSongDuration.setContent).toHaveBeenCalled();
				});
			});
			describe("when _boolUpdateTimeDisplay is false", function(){
				beforeEach(function(){
					playerControl._boolUpdateTimeDisplay = false;
				});
				it("shouldnt call formatTime", function(){
					spyOn(Utilities, "formatTime");
					playerControl.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
					expect(Utilities.formatTime).not.toHaveBeenCalled();
				});
				it("shouldnt call setContent", function(){
					spyOn(playerControl.$.lblSongTime, "setContent");
					playerControl.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
					expect(playerControl.$.lblSongTime.setContent).not.toHaveBeenCalled();
				});
				it('shouldnt change _intCurrentDuration',function(){
					var prior = playerControl._intCurrentDuration;
					playerControl.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
					expect(playerControl._intCurrentDuration).toEqual(prior);
				});
				it("shouldnt call setContent", function(){
					spyOn(playerControl.$.lblSongDuration, "setContent");
					playerControl.updateTrackTimeDisplay({"floatTrackCurrentTime":"title","floatTrackDuration":"artist"});
					expect(playerControl.$.lblSongDuration.setContent).not.toHaveBeenCalled();
				});
			});
			
			describe("when _boolUpdateSlider is true", function(){
				beforeEach(function(){
					playerControl._boolUpdateSlider = true;
				});
			
				it("should call playerControl.$.sliderSongTime.setPositionImmediate", function(){
					spyOn(playerControl.$.sliderSongTime, "setPositionImmediate");
					playerControl.updateTrackTimeDisplay({"floatTrackCurrentTime":12, "floatTrackDuration":34});
					expect(playerControl.$.sliderSongTime.setPositionImmediate).toHaveBeenCalledWith(35);
				});
				describe("when this._intCurrentDuration is 0", function(){
					beforeEach(function(){
						playerControl._intCurrentDuration = 0;
						playerControl._boolUpdateTimeDisplay=false;
					});
				
					it("should call playerControl.$.sliderSongTime.setPositionImmediate with 0", function(){
						spyOn(playerControl.$.sliderSongTime, "setPositionImmediate");
						playerControl.updateTrackTimeDisplay({"floatTrackCurrentTime":0, "floatTrackDuration":0});
						expect(playerControl.$.sliderSongTime.setPositionImmediate).toHaveBeenCalledWith(0);
					});
				});
				//We currently need to call render, or ui won't update
				it("should call playerControl.$.sliderSongTime.render", function(){
					spyOn(playerControl.$.sliderSongTime, "render");
					playerControl.updateTrackTimeDisplay({"floatTrackCurrentTime":12, "floatTrackDuration":34});
					expect(playerControl.$.sliderSongTime.render).toHaveBeenCalled();
				});
				
			});
			
			describe("when _boolUpdateSlider is false", function(){
				beforeEach(function(){
					playerControl._boolUpdateSlider = false;
				});
			
				it("shouldnt call playerControl.$.sliderSongTime.setPositionImmediate", function(){
					spyOn(playerControl.$.sliderSongTime, "setPositionImmediate");
					playerControl.updateTrackTimeDisplay({"floatTrackCurrentTime":12, "floatTrackDuration":34});
					expect(playerControl.$.sliderSongTime.setPositionImmediate).not.toHaveBeenCalled();
				});
			});
		
		});
	});
	describe('test (onClick_sliderSongTime)', function(){
		it('should change _boolUpdateSlider',function(){
			playerControl._boolUpdateSlider = true;
			playerControl.onClick_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl._boolUpdateSlider).toEqual(false);
		});
		it('should change _boolUpdateTimeDisplay',function(){
			playerControl._boolUpdateTimeDisplay = false;
			playerControl.onClick_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl._boolUpdateTimeDisplay).toEqual(true);
		});
		it('should setBoolDraggingSlider',function(){
			spyOn(playerControl, "setBoolDraggingSlider");
			playerControl.onClick_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl.setBoolDraggingSlider).toHaveBeenCalledWith(true);
		});
	});
	describe('test (onChange_sliderSongTime)', function(){
		it('should change _boolUpdateSlider',function(){
			playerControl._boolUpdateSlider = false;
			playerControl.onChange_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl._boolUpdateSlider).toEqual(true);
		});
		it('should call updateSliderSongTime',function(){
			spyOn(playerControl,'updateSliderSongTime');
			playerControl.onChange_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl.updateSliderSongTime).toHaveBeenCalledWith({"rowIndex":0,"dispatchTarget":null});
		});
		
		it('should change _boolUpdateTimeDisplay',function(){
			playerControl._boolUpdateTimeDisplay = false;
			playerControl.onChange_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl._boolUpdateTimeDisplay).toEqual(true);
		});
		it('should call this.$.sliderSongTime.setPositionImmediate',function(){
			spyOn(playerControl.$.sliderSongTime,'setPositionImmediate');
			playerControl.onChange_sliderSongTime(null,6);
			expect(playerControl.$.sliderSongTime.setPositionImmediate).not.toHaveBeenCalled();
		});
		it('should call this.$.sliderSongTime.setPositionImmediate not with 100',function(){
			//IMPORTANT: WE NEED THIS TO FIRE ANOTHER SONG END
			spyOn(playerControl.$.sliderSongTime,'setPositionImmediate');
			playerControl.onChange_sliderSongTime(null,100);
			expect(playerControl.$.sliderSongTime.setPositionImmediate).not.toHaveBeenCalled();
		});
		it('should call updateSliderSongTime',function(){
			spyOn(playerControl,'updateSliderSongTime');
			playerControl.onChange_sliderSongTime(null,6);
			expect(playerControl.updateSliderSongTime).toHaveBeenCalledWith(6);
		});
		
		it("should call	this.$.lblSongTime.setContent", function(){
			spyOn(playerControl.$.lblSongTime,'setContent');
			playerControl.onChange_sliderSongTime(null,6);
			expect(playerControl.$.lblSongTime.setContent).toHaveBeenCalled();
		});
		it('should setBoolDraggingSlider',function(){
			spyOn(playerControl, "setBoolDraggingSlider");
			playerControl.onChange_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl.setBoolDraggingSlider).toHaveBeenCalledWith(false);
		});
		
	});
	describe('test (updateSliderSongTime)', function(){
		it('should call doSetPlaybackTime',function(){
			spyOn(playerControl,'doSetPlaybackTime');
			playerControl.updateSliderSongTime();
			expect(playerControl.doSetPlaybackTime).toHaveBeenCalledWith(undefined);
		});
		it('should change _boolUpdateTimeDisplay',function(){
			playerControl._boolUpdateTimeDisplay = false;
			playerControl.updateSliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl._boolUpdateTimeDisplay).toEqual(true);
		});
	});
	describe('test (onChanging_sliderSongTime)', function(){
		describe("when dragging", function(){
			beforeEach(function(){
				playerControl.boolDraggingSlider = true;
			});
			it('should change _boolUpdateSlider',function(){
				playerControl._boolUpdateSlider = true;
				playerControl.onChanging_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
				expect(playerControl._boolUpdateSlider).toEqual(false);
			});
			it('should change _boolUpdateTimeDisplay',function(){
				playerControl._boolUpdateTimeDisplay = true;
				playerControl.onChanging_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
				expect(playerControl._boolUpdateTimeDisplay).toEqual(false);
			});
			
			it('should call $.lblSongTime.setContent',function(){
				spyOn(playerControl.$.lblSongTime,'setContent');
				playerControl.onChanging_sliderSongTime();
				expect(playerControl.$.lblSongTime.setContent).toHaveBeenCalled();
			});

			it('should call Utilities.formatTime',function(){
				spyOn(Utilities,'formatTime');
				playerControl.onChanging_sliderSongTime();
				expect(Utilities.formatTime).toHaveBeenCalled();
			});
			it('should call doSetPlaybackTime',function(){
				spyOn(playerControl,'doSetPlaybackTime');
				playerControl.onChanging_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
				expect(playerControl.doSetPlaybackTime).toHaveBeenCalledWith({"rowIndex":0,"dispatchTarget":null});
			});
		})
		describe("when not dragging", function(){
			beforeEach(function(){
				playerControl.boolDraggingSlider = false;
			});
			it('should not change _boolUpdateSlider',function(){
				playerControl._boolUpdateSlider = true;
				playerControl.onChanging_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
				expect(playerControl._boolUpdateSlider).toEqual(true);
			});
			it('shouldnt change _boolUpdateTimeDisplay',function(){
				playerControl._boolUpdateTimeDisplay = true;
				playerControl.onChanging_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
				expect(playerControl._boolUpdateTimeDisplay).toEqual(true);
			});
			
			it('shouldnt  call $.lblSongTime.setContent',function(){
				spyOn(playerControl.$.lblSongTime,'setContent');
				playerControl.onChanging_sliderSongTime();
				expect(playerControl.$.lblSongTime.setContent).not.toHaveBeenCalled();
			});

			it('shouldnt  call Utilities.formatTime',function(){
				spyOn(Utilities,'formatTime');
				playerControl.onChanging_sliderSongTime();
				expect(Utilities.formatTime).not.toHaveBeenCalled();
			});
			it('shouldnt  call doSetPlaybackTime',function(){
				spyOn(playerControl,'doSetPlaybackTime');
				playerControl.onChanging_sliderSongTime(null,{"rowIndex":0,"dispatchTarget":null});
				expect(playerControl.doSetPlaybackTime).not.toHaveBeenCalled();
			});
		})
		
	});
	describe('test (setShuffleButton)', function(){
		it("should call this.$.btnShuffle.addRemoveClass true", function(){
			spyOn(playerControl.$.btnShuffle, "addRemoveClass");
			playerControl.setShuffleButton(true);
			expect(playerControl.$.btnShuffle.addRemoveClass).toHaveBeenCalledWith("on", true);
		});
		it("should call this.$.btnShuffle.addRemoveClass false", function(){
			spyOn(playerControl.$.btnShuffle, "addRemoveClass");
			playerControl.setShuffleButton(false);
			expect(playerControl.$.btnShuffle.addRemoveClass).toHaveBeenCalledWith("on", false);
		});
	});
	describe('test (setRepeatButton)', function(){
		it("should call this.$.btnRepeat.setClassName 1", function(){
			spyOn(playerControl.$.btnRepeat, "setClassName");
			playerControl.setRepeatButton("HI");
			expect(playerControl.$.btnRepeat.setClassName).toHaveBeenCalledWith("toggleMode repeat "+ "HI");
		});
		it("should call this.$.btnRepeat.setClassName 2", function(){
			spyOn(playerControl.$.btnRepeat, "setClassName");
			playerControl.setRepeatButton("HIasdf");
			expect(playerControl.$.btnRepeat.setClassName).toHaveBeenCalledWith("toggleMode repeat "+ "HIasdf");
		});
	});
	describe('test (onGetVolume)', function(){
		it("shoulod call this.$.sliderVolume.render", function(){
			spyOn(playerControl.$.sliderVolume, "render");
			playerControl._boolUpdateVolumeSlider = true;
			playerControl.onGetVolume(4);
			expect(playerControl.$.sliderVolume.render).toHaveBeenCalled();
		});
		it("shoulod call this.$.sliderVolume.setPositionImmediate", function(){
			spyOn(playerControl.$.sliderVolume, "setPositionImmediate");
			playerControl._boolUpdateVolumeSlider = true;
			playerControl.onGetVolume(4);
			expect(playerControl.$.sliderVolume.setPositionImmediate).toHaveBeenCalledWith(4);
		});
		it("shoulodnt call this.$.sliderVolume.setPositionImmediate", function(){
			spyOn(playerControl.$.sliderVolume, "setPositionImmediate");
			playerControl._boolUpdateVolumeSlider = false;
			playerControl.onGetVolume(4);
			expect(playerControl.$.sliderVolume.setPositionImmediate).not.toHaveBeenCalled();
		});
		it("shoulodnt call this.$.sliderVolume.render", function(){
			
			spyOn(playerControl.$.sliderVolume, "render");
			playerControl._boolUpdateVolumeSlider = false;
			playerControl.onGetVolume(4);
			expect(playerControl.$.sliderVolume.render).not.toHaveBeenCalled();
		});
	});
	describe('test (onChange_sliderVolume)', function(){
		it('should call doSetVolume',function(){
			spyOn(playerControl,'doSetVolume');
			playerControl.onChange_sliderVolume(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl.doSetVolume).toHaveBeenCalledWith({"rowIndex":0,"dispatchTarget":null});
		});
		it('should change _boolUpdateVolumeSlider',function(){
			playerControl._boolUpdateVolumeSlider = false;
			playerControl.onChange_sliderVolume(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl._boolUpdateVolumeSlider).toBe(true);
		});
	});
	describe('test (onChanging_sliderVolume)', function(){
		it('should change _boolUpdateVolumeSlider',function(){
			playerControl._boolUpdateVolumeSlider = true;
			playerControl.onChanging_sliderVolume(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl._boolUpdateVolumeSlider).toEqual(false);
		});
		it('should call doSetVolume',function(){
			spyOn(playerControl,'doSetVolume');
			playerControl.onChanging_sliderVolume(null,{"rowIndex":0,"dispatchTarget":null});
			expect(playerControl.doSetVolume).toHaveBeenCalledWith({"rowIndex":0,"dispatchTarget":null});
		});
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(playerControl,'_constructor');
			playerControl.base();
			expect(playerControl._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(playerControl,'constructed');
			playerControl.base();
			expect(playerControl.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(playerControl,'_constructor');
			playerControl.ctor();
			expect(playerControl._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(playerControl,'constructed');
			playerControl.ctor();
			expect(playerControl.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doClickPrev)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playerControl,'dispatchIndirectly');
			playerControl.doClickPrev();
			expect(playerControl.dispatchIndirectly).toHaveBeenCalledWith("onClickPrev",{});
		});
	});
	describe('test (doClickPlayPause)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playerControl,'dispatchIndirectly');
			playerControl.doClickPlayPause();
			expect(playerControl.dispatchIndirectly).toHaveBeenCalledWith("onClickPlayPause",{});
		});
	});
	describe('test (doClickNext)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playerControl,'dispatchIndirectly');
			playerControl.doClickNext();
			expect(playerControl.dispatchIndirectly).toHaveBeenCalledWith("onClickNext",{});
		});
	});
	describe('test (doShuffleClick)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playerControl,'dispatchIndirectly');
			playerControl.doShuffleClick();
			expect(playerControl.dispatchIndirectly).toHaveBeenCalledWith("onShuffleClick",{});
		});
	});
	describe('test (doRepeatClick)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playerControl,'dispatchIndirectly');
			playerControl.doRepeatClick();
			expect(playerControl.dispatchIndirectly).toHaveBeenCalledWith("onRepeatClick",{});
		});
	});
	describe('test (doSetVolume)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playerControl,'dispatchIndirectly');
			playerControl.doSetVolume();
			expect(playerControl.dispatchIndirectly).toHaveBeenCalledWith("onSetVolume",{});
		});
	});
	describe('test (doRequestVolume)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playerControl,'dispatchIndirectly');
			playerControl.doRequestVolume();
			expect(playerControl.dispatchIndirectly).toHaveBeenCalledWith("onRequestVolume",{});
		});
	});
	describe('test (doSetPlaybackTime)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playerControl,'dispatchIndirectly');
			playerControl.doSetPlaybackTime();
			expect(playerControl.dispatchIndirectly).toHaveBeenCalledWith("onSetPlaybackTime",{});
		});
	});
	describe('test (doClickFullScreen)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(playerControl,'dispatchIndirectly');
			playerControl.doClickFullScreen();
			expect(playerControl.dispatchIndirectly).toHaveBeenCalledWith("onClickFullScreen",{});
		});
	});
	describe('test (getPlayEnabled)', function(){
		it('should call getProperty',function(){
			spyOn(playerControl,'getProperty');
			playerControl.getPlayEnabled();
			expect(playerControl.getProperty).toHaveBeenCalledWith("playEnabled");
		});
	});
	describe('test (setPlayEnabled)', function(){
		it('should call _setProperty',function(){
			spyOn(playerControl,'_setProperty');
			playerControl.setPlayEnabled();
			expect(playerControl._setProperty).toHaveBeenCalledWith("playEnabled",undefined,"playEnabledChanged");
		});
	});
	describe('test (getPrevNextEnabled)', function(){
		it('should call getProperty',function(){
			spyOn(playerControl,'getProperty');
			playerControl.getPrevNextEnabled();
			expect(playerControl.getProperty).toHaveBeenCalledWith("prevNextEnabled");
		});
	});
	describe('test (setPrevNextEnabled)', function(){
		it('should call _setProperty',function(){
			spyOn(playerControl,'_setProperty');
			playerControl.setPrevNextEnabled();
			expect(playerControl._setProperty).toHaveBeenCalledWith("prevNextEnabled",undefined,"prevNextEnabledChanged");
		});
	});
	describe('test (resizeHandler)', function(){
//		it('should call inherited',function(){
//			spyOn(playerControl,'inherited');
//			playerControl.resizeHandler();
//			expect(playerControl.inherited).toHaveBeenCalledWith({});
//		});
//		it('should call fadeIn',function(){
//			spyOn(playerControl,'fadeIn');
//			playerControl.resizeHandler();
//			expect(playerControl.fadeIn).toHaveBeenCalled();
//		});
	});
	describe('test (fadeIn)', function(){
		it('should call removeClass',function(){
			spyOn(playerControl,'removeClass');
			playerControl.fadeIn();
			expect(playerControl.removeClass).toHaveBeenCalledWith("enyo-toolbar-fade-in");
		});
		it('should call addClass',function(){
			spyOn(playerControl,'addClass');
			playerControl.fadeIn();
			expect(playerControl.addClass).toHaveBeenCalledWith("enyo-toolbar-snap-out");
		});
	});
	describe('test (_fadeIn)', function(){
		it('should call addClass',function(){
			spyOn(playerControl,'addClass');
			playerControl._fadeIn();
			expect(playerControl.addClass).toHaveBeenCalledWith("enyo-toolbar-fade-in");
		});
	});
	describe('test (layoutKind)', function(){
		it('should call _constructor',function(){
			spyOn(playerControl,'_constructor');
			playerControl.layoutKind();
			expect(playerControl._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(playerControl,'constructed');
			playerControl.layoutKind();
			expect(playerControl.constructed).toHaveBeenCalled();
		});
	});
	describe('test (destroy)', function(){
		it('should call unregisterEvents',function(){
			spyOn(playerControl,'unregisterEvents');
			playerControl.destroy();
			expect(playerControl.unregisterEvents).toHaveBeenCalled();
		});
		it('should call destroyControls',function(){
			spyOn(playerControl,'destroyControls');
			playerControl.destroy();
			expect(playerControl.destroyControls).toHaveBeenCalled();
		});
		it('should call inherited',function(){
			spyOn(playerControl,'inherited');
			playerControl.destroy();
			expect(playerControl.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(playerControl,'inherited');
			playerControl.importProps();
			expect(playerControl.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
//		it('should call createChrome',function(){
//			spyOn(playerControl,'createChrome');
//			playerControl.initComponents();
//			expect(playerControl.createChrome).toHaveBeenCalledWith(undefined);
//		});
		it('should call inherited',function(){
			spyOn(playerControl,'inherited');
			playerControl.initComponents();
			expect(playerControl.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(playerControl,'inherited');
			playerControl.createComponents();
			expect(playerControl.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(playerControl,'discoverControlParent');
			playerControl.createComponents();
			expect(playerControl.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(playerControl,'createComponents');
			playerControl.createChrome();
			expect(playerControl.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});