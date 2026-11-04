/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return new KindAlbumArtItem();
//			}
//var objName = "albumArtItem";
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


describe('albumArtItem tests', function(){
	var creator = function (){
				return new KindAlbumArtItem();
			};
	var albumArtItem;
	beforeEach(function(){
		albumArtItem = creator();
		var before = function (obj, callbacks) {
		obj.startWatch=function(){};
	};
		var callback = function(one, fun){return fun;};
		before(albumArtItem,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof albumArtItem.controls).toBe('object');
		expect(typeof albumArtItem.children).toBe('object');
		expect(albumArtItem._componentNameMap).toEqual({});
		expect(typeof albumArtItem.$).toBe('object');
		expect(albumArtItem.domStyles).toEqual({});
		expect(albumArtItem.domAttributes).toEqual({"className":"item focused","id":"kindAlbumArtItem"});
		expect(typeof albumArtItem.owner).toBe('object');
		expect(albumArtItem.name).toEqual("kindAlbumArtItem");
		expect(albumArtItem.id).toEqual("kindAlbumArtItem");
		expect(albumArtItem.controlParent).toEqual(undefined);
		expect(typeof albumArtItem.layout).toBe('object');
		expect(albumArtItem.kind).toEqual("Control");
		expect(albumArtItem.layoutKind).toEqual("VFlexLayout");
		expect(albumArtItem.className).toEqual("item focused");
		expect(albumArtItem.events).toEqual({"onItemClick":""});
		expect(albumArtItem.published).toEqual({"intTrackOrigIndex":-99,"intTrackIndex":-98,"strArtistName":"","strSongTitle":"","strAlbumName":"","strAlbumArtFile":"","intCurrTrackIndex":-100,"boolAudioPlaying":true,"boolShow":true,"boolVertical":true});
		expect(albumArtItem.kindName).toEqual("KindAlbumArtItem");
		expect(albumArtItem.kindComponents).toEqual([{"name":"itemHeader","className":"info","kind":"Control","pack":"end","showing":true,"components":[{"name":"lblSongTitle","content":"","className":"album"},{"name":"lblArtistName","content":"","className":"artist"},{"name":"imgMusicNote","kind":"Image","src":"images/Music_Note_white.png","className":"indicator"},{"name":"lblAlbumName","content":"","className":"title"}]},{"name":"albumArtContainer","className":"cover","onclick":"onclick_vfbItem","components":[{"name":"imgAlbumArt","kind":"Control","className":"img"},{"name":"imgAlbumArtIcon","kind":"Image","src":"images/play_albumartitem.png","className":"albumarticon","showing":false}]}]);
		expect(albumArtItem.onItemClick).toEqual("");
		expect(albumArtItem.intTrackOrigIndex).toEqual(-99);
		expect(albumArtItem.intTrackIndex).toEqual(-98);
		expect(albumArtItem.strArtistName).toEqual("");
		expect(albumArtItem.strSongTitle).toEqual("");
		expect(albumArtItem.strAlbumName).toEqual("");
		expect(albumArtItem.strAlbumArtFile).toEqual("");
		expect(albumArtItem.intCurrTrackIndex).toEqual(-100);
		expect(albumArtItem.boolAudioPlaying).toEqual(true);
		expect(albumArtItem.boolShow).toEqual(true);
		expect(albumArtItem.boolVertical).toEqual(true);
		expect(albumArtItem.controlParentName).toEqual("client");
		expect(albumArtItem.defaultKind).toEqual("Control");
		expect(albumArtItem.onclick).toEqual("");
		expect(albumArtItem.onmousedown).toEqual("");
		expect(albumArtItem.onmouseup).toEqual("");
		expect(albumArtItem.style).toEqual("");
		expect(albumArtItem.container).toEqual(null);
		expect(albumArtItem.parent).toEqual(null);
		expect(albumArtItem.generated).toEqual(false);
		expect(albumArtItem.allowHtml).toEqual(false);
		expect(albumArtItem.content).toEqual("");
		expect(albumArtItem.nodeTag).toEqual("div");
		expect(albumArtItem.node).toEqual(null);
		expect(albumArtItem.showing).toEqual(true);
		expect(albumArtItem.prepend).toEqual(false);
		expect(albumArtItem.wantsEvents).toEqual(true);
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(albumArtItem,'inherited');
			albumArtItem.create();
			expect(albumArtItem.inherited).toHaveBeenCalledWith({});
		});
		it('should call checkIfCurrentTrack',function(){
			spyOn(albumArtItem,'checkIfCurrentTrack');
			albumArtItem.create();
			expect(albumArtItem.checkIfCurrentTrack).toHaveBeenCalled();
		});
	
	});
	
	describe("test (setupObj)", function(){
		it("should call this.$.lblSongTitle.setContent", function(){
			spyOn(albumArtItem.$.lblSongTitle,'setContent');
			albumArtItem.strSongTitle = "WOO";
			albumArtItem.setupObj();
			expect(albumArtItem.$.lblSongTitle.setContent).toHaveBeenCalledWith("WOO");
		});
		it("should call this.$.lblArtistName.setContent", function(){
			spyOn(albumArtItem.$.lblArtistName,'setContent');
			albumArtItem.strArtistName = "WOO";
			albumArtItem.setupObj();
			expect(albumArtItem.$.lblArtistName.setContent).toHaveBeenCalledWith("WOO");
		});
		it("should call this.$.lblSongTitle.setContent", function(){
			spyOn(albumArtItem.$.lblAlbumName,'setContent');
			albumArtItem.strAlbumName = "WOO";
			albumArtItem.setupObj();
			expect(albumArtItem.$.lblAlbumName.setContent).toHaveBeenCalledWith("WOO");
		});
		
		describe("when boolshow is true", function(){
			beforeEach(function(){
				albumArtItem.boolShow = true;
			});
			it("shouldn't hide albumArtContainer", function(){
				spyOn(albumArtItem.$.albumArtContainer, "hide");
				albumArtItem.setupObj();
				expect(albumArtItem.$.albumArtContainer.hide).not.toHaveBeenCalled();
			});
			describe("strAlbumArtFile is empty", function(){
				beforeEach(function(){
					albumArtItem.strAlbumArtFile = "";
				});
				it("should hide imgAlbumArt", function(){
					spyOn(albumArtItem.$.imgAlbumArt, "hide");
					albumArtItem.setupObj();
					expect(albumArtItem.$.imgAlbumArt.hide).toHaveBeenCalled();
				});
				
			});
			describe("strAlbumArtFile is not empty",function(){
				beforeEach(function(){
					albumArtItem.strAlbumArtFile = "file";
					spyOn(Utilities, "getTrackImage").andReturn("asdf");
					window.PalmSystem = true;
				});
				it("shouldn't hide imgAlbumArt", function(){
					spyOn(albumArtItem.$.imgAlbumArt, "hide");
					albumArtItem.setupObj();
					expect(albumArtItem.$.imgAlbumArt.hide).not.toHaveBeenCalled();
				});
				
				it("should if palmSystem is false hide imgAlbumArt", function(){
					window.PalmSystem = false;
					spyOn(albumArtItem.$.imgAlbumArt, "hide");
					albumArtItem.setupObj();
					expect(albumArtItem.$.imgAlbumArt.hide).toHaveBeenCalled();
				});
				it("should set this.$.imgAlbumArt.setStyle", function(){
					spyOn(albumArtItem.$.imgAlbumArt,"setStyle");
					albumArtItem.setupObj();
					expect(albumArtItem.$.imgAlbumArt.setStyle).toHaveBeenCalled();
				});
				it("should call Utilities.getTrackImage", function(){
					albumArtItem.setupObj();
					expect(Utilities.getTrackImage).toHaveBeenCalled();
				});
				
			});
			
			
			
		});
		describe("when boolshow is false", function(){
			beforeEach(function(){
				albumArtItem.boolShow = false;
			});
			
			it("should hide albumArtContainer", function(){
				spyOn(albumArtItem.$.albumArtContainer, "hide");
				albumArtItem.setupObj();
				expect(albumArtItem.$.albumArtContainer.hide).toHaveBeenCalled();
			});
			
			describe("strAlbumArtFile is empty", function(){
				beforeEach(function(){
					albumArtItem.strAlbumArtFile = "";
				});
				it("should hide imgAlbumArt", function(){
					spyOn(albumArtItem.$.imgAlbumArt, "hide");
					albumArtItem.setupObj();
					expect(albumArtItem.$.imgAlbumArt.hide).not.toHaveBeenCalled();
				});
				
			});
			describe("strAlbumArtFile is not empty",function(){
				beforeEach(function(){
					albumArtItem.strAlbumArtFile = "file";
					spyOn(Utilities, "getTrackImage").andReturn("asdf");
					window.PalmSystem = true;
				});
							
				it("should if palmSystem is false hide imgAlbumArt", function(){
					window.PalmSystem = false;
					spyOn(albumArtItem.$.imgAlbumArt, "hide");
					albumArtItem.setupObj();
					expect(albumArtItem.$.imgAlbumArt.hide).not.toHaveBeenCalled();
				});
				it("should set this.$.imgAlbumArt.setStyle", function(){
					spyOn(albumArtItem.$.imgAlbumArt,"setStyle");
					albumArtItem.setupObj();
					expect(albumArtItem.$.imgAlbumArt.setStyle).not.toHaveBeenCalled();
				});
				it("should call Utilities.getTrackImage", function(){
					albumArtItem.setupObj();
					expect(Utilities.getTrackImage).not.toHaveBeenCalled();
				});
				
			});
			
		});
		
	});
	describe('test (onImgError)', function(){
		it("should exist", function(){
			albumArtItem.onImgError();
		});
	});
	describe('test (checkIfCurrentTrack)', function(){
		it("should set boolAudioPlaying true", function(){
			albumArtItem.checkIfCurrentTrack(true, 1)
			expect(albumArtItem.boolAudioPlaying).toEqual(true);
		});
		it("should set boolAudioPlaying false", function(){
			albumArtItem.checkIfCurrentTrack(false, 1)
			expect(albumArtItem.boolAudioPlaying).toEqual(false);
		});
		it("should leave alone if undefined", function(){
			var temp = albumArtItem.boolAudioPlaying;
			albumArtItem.checkIfCurrentTrack(undefined, 1)
			expect(albumArtItem.boolAudioPlaying).toEqual(temp);
		});
		it("should set intCurrTrackIndex 1", function(){
			albumArtItem.checkIfCurrentTrack(true, 1)
			expect(albumArtItem.intCurrTrackIndex).toEqual(1);
		});
		it("should set intCurrTrackIndex 2", function(){
			albumArtItem.checkIfCurrentTrack(false, 2)
			expect(albumArtItem.intCurrTrackIndex).toEqual(2);
		});
		it("should leave intCurrTrackIndex alone if undefined", function(){
			var temp = albumArtItem.intCurrTrackIndex;
			albumArtItem.checkIfCurrentTrack(true, undefined)
			expect(albumArtItem.intCurrTrackIndex).toEqual(temp);
		});
		it("should call setShowing false", function(){
			spyOn(albumArtItem.$.imgMusicNote,"setShowing");
			albumArtItem.checkIfCurrentTrack(true, 12)
			expect(albumArtItem.$.imgMusicNote.setShowing).toHaveBeenCalledWith(false);
		});
		it("should call setShowing true", function(){
			albumArtItem.intTrackIndex = 12;
			spyOn(albumArtItem.$.imgMusicNote,"setShowing");
			albumArtItem.checkIfCurrentTrack(true, 12)
			expect(albumArtItem.$.imgMusicNote.setShowing).toHaveBeenCalledWith(true);
		});
	});
	describe('test (setPlayPauseIcon)', function(){
		it("if boolAudioPlaying is true sets to pause", function(){
			albumArtItem.boolAudioPlaying = true;
			spyOn(albumArtItem.$.imgAlbumArtIcon,"setSrc");
			albumArtItem.setPlayPauseIcon()
			expect(albumArtItem.$.imgAlbumArtIcon.setSrc).toHaveBeenCalledWith("images/pause_albumartitem.png");
		});
		it("if boolAudioPlaying is false sets to play", function(){
			albumArtItem.boolAudioPlaying = false;
			spyOn(albumArtItem.$.imgAlbumArtIcon,"setSrc");
			albumArtItem.setPlayPauseIcon()
			expect(albumArtItem.$.imgAlbumArtIcon.setSrc).toHaveBeenCalledWith("images/play_albumartitem.png");
		});
		
	});
	describe('test (displayAsCurrent)', function(){
		it('should call addRemoveClass',function(){
			spyOn(albumArtItem,'addRemoveClass');
			albumArtItem.displayAsCurrent();
			expect(albumArtItem.addRemoveClass).toHaveBeenCalledWith("focused",true);
		});
	});
	describe('test (displayAsPrev)', function(){
		it('should call addRemoveClass',function(){
			spyOn(albumArtItem,'addRemoveClass');
			albumArtItem.displayAsPrev();
			expect(albumArtItem.addRemoveClass).toHaveBeenCalledWith("focused",false);
		});
	});
	describe('test (displayAsNext)', function(){
		it('should call addRemoveClass',function(){
			spyOn(albumArtItem,'addRemoveClass');
			albumArtItem.displayAsNext();
			expect(albumArtItem.addRemoveClass).toHaveBeenCalledWith("focused",false);
		});
	});
	describe('test (onclick_vfbItem)', function(){
		it('should flip boolAudioPlaying to false',function(){
			albumArtItem.boolAudioPlaying = true;
			albumArtItem.onclick_vfbItem(null,{"rowIndex":0,"dispatchTarget":null});
			expect(albumArtItem.boolAudioPlaying).toEqual(false);
		});
		it('should flip boolAudioPlaying to true',function(){
			albumArtItem.boolAudioPlaying = false;
			albumArtItem.onclick_vfbItem(null,{"rowIndex":0,"dispatchTarget":null});
			expect(albumArtItem.boolAudioPlaying).toEqual(true);
		});
		it('should call setPlayPauseIcon',function(){
			spyOn(albumArtItem,'setPlayPauseIcon');
			albumArtItem.onclick_vfbItem(null,{"rowIndex":0,"dispatchTarget":null});
			expect(albumArtItem.setPlayPauseIcon).toHaveBeenCalled();
		});
		it('should call doItemClick',function(){
			spyOn(albumArtItem,'doItemClick');
			albumArtItem.onclick_vfbItem(null,{"rowIndex":0,"dispatchTarget":null});
			expect(albumArtItem.doItemClick).toHaveBeenCalledWith(-99);
		});
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(albumArtItem,'_constructor');
			albumArtItem.base();
			expect(albumArtItem._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(albumArtItem,'constructed');
			albumArtItem.base();
			expect(albumArtItem.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(albumArtItem,'_constructor');
			albumArtItem.ctor();
			expect(albumArtItem._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(albumArtItem,'constructed');
			albumArtItem.ctor();
			expect(albumArtItem.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doItemClick)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(albumArtItem,'dispatchIndirectly');
			albumArtItem.doItemClick();
			expect(albumArtItem.dispatchIndirectly).toHaveBeenCalledWith("onItemClick",{});
		});
	});
	describe('test (getIntTrackOrigIndex)', function(){
		it('should call getProperty',function(){
			spyOn(albumArtItem,'getProperty');
			albumArtItem.getIntTrackOrigIndex();
			expect(albumArtItem.getProperty).toHaveBeenCalledWith("intTrackOrigIndex");
		});
	});
	describe('test (setIntTrackOrigIndex)', function(){
		it('should call _setProperty',function(){
			spyOn(albumArtItem,'_setProperty');
			albumArtItem.setIntTrackOrigIndex();
			expect(albumArtItem._setProperty).toHaveBeenCalledWith("intTrackOrigIndex",undefined,"intTrackOrigIndexChanged");
		});
	});
	describe('test (getIntTrackIndex)', function(){
		it('should call getProperty',function(){
			spyOn(albumArtItem,'getProperty');
			albumArtItem.getIntTrackIndex();
			expect(albumArtItem.getProperty).toHaveBeenCalledWith("intTrackIndex");
		});
	});
	describe('test (setIntTrackIndex)', function(){
		it('should call _setProperty',function(){
			spyOn(albumArtItem,'_setProperty');
			albumArtItem.setIntTrackIndex();
			expect(albumArtItem._setProperty).toHaveBeenCalledWith("intTrackIndex",undefined,"intTrackIndexChanged");
		});
	});
	describe('test (getStrArtistName)', function(){
		it('should call getProperty',function(){
			spyOn(albumArtItem,'getProperty');
			albumArtItem.getStrArtistName();
			expect(albumArtItem.getProperty).toHaveBeenCalledWith("strArtistName");
		});
	});
	describe('test (setStrArtistName)', function(){
	});
	describe('test (getStrSongTitle)', function(){
		it('should call getProperty',function(){
			spyOn(albumArtItem,'getProperty');
			albumArtItem.getStrSongTitle();
			expect(albumArtItem.getProperty).toHaveBeenCalledWith("strSongTitle");
		});
	});
	describe('test (setStrSongTitle)', function(){
	});
	describe('test (getStrAlbumName)', function(){
		it('should call getProperty',function(){
			spyOn(albumArtItem,'getProperty');
			albumArtItem.getStrAlbumName();
			expect(albumArtItem.getProperty).toHaveBeenCalledWith("strAlbumName");
		});
	});
	describe('test (setStrAlbumName)', function(){
	});
	describe('test (getStrAlbumArtFile)', function(){
		it('should call getProperty',function(){
			spyOn(albumArtItem,'getProperty');
			albumArtItem.getStrAlbumArtFile();
			expect(albumArtItem.getProperty).toHaveBeenCalledWith("strAlbumArtFile");
		});
	});
	describe('test (setStrAlbumArtFile)', function(){
	});
	describe('test (getIntCurrTrackIndex)', function(){
		it('should call getProperty',function(){
			spyOn(albumArtItem,'getProperty');
			albumArtItem.getIntCurrTrackIndex();
			expect(albumArtItem.getProperty).toHaveBeenCalledWith("intCurrTrackIndex");
		});
	});
	describe('test (setIntCurrTrackIndex)', function(){
		it('should call _setProperty',function(){
			spyOn(albumArtItem,'_setProperty');
			albumArtItem.setIntCurrTrackIndex();
			expect(albumArtItem._setProperty).toHaveBeenCalledWith("intCurrTrackIndex",undefined,"intCurrTrackIndexChanged");
		});
	});
	describe('test (getBoolAudioPlaying)', function(){
		it('should call getProperty',function(){
			spyOn(albumArtItem,'getProperty');
			albumArtItem.getBoolAudioPlaying();
			expect(albumArtItem.getProperty).toHaveBeenCalledWith("boolAudioPlaying");
		});
	});
	describe('test (setBoolAudioPlaying)', function(){
		it('should call _setProperty',function(){
			spyOn(albumArtItem,'_setProperty');
			albumArtItem.setBoolAudioPlaying();
			expect(albumArtItem._setProperty).toHaveBeenCalledWith("boolAudioPlaying",undefined,"boolAudioPlayingChanged");
		});
	});
	describe('test (getBoolShow)', function(){
		it('should call getProperty',function(){
			spyOn(albumArtItem,'getProperty');
			albumArtItem.getBoolShow();
			expect(albumArtItem.getProperty).toHaveBeenCalledWith("boolShow");
		});
	});
	describe('test (setBoolShow)', function(){
		it('should call _setProperty',function(){
			spyOn(albumArtItem,'_setProperty');
			albumArtItem.setBoolShow();
			expect(albumArtItem._setProperty).toHaveBeenCalledWith("boolShow",undefined,"boolShowChanged");
		});
	});
	describe('test (getBoolVertical)', function(){
		it('should call getProperty',function(){
			spyOn(albumArtItem,'getProperty');
			albumArtItem.getBoolVertical();
			expect(albumArtItem.getProperty).toHaveBeenCalledWith("boolVertical");
		});
	});
	describe('test (setBoolVertical)', function(){
		it('should call _setProperty',function(){
			spyOn(albumArtItem,'_setProperty');
			albumArtItem.setBoolVertical();
			expect(albumArtItem._setProperty).toHaveBeenCalledWith("boolVertical",undefined,"boolVerticalChanged");
		});
	});
	describe('test (destroy)', function(){
		it('should call unregisterEvents',function(){
			spyOn(albumArtItem,'unregisterEvents');
			albumArtItem.destroy();
			expect(albumArtItem.unregisterEvents).toHaveBeenCalled();
		});
		it('should call destroyControls',function(){
			spyOn(albumArtItem,'destroyControls');
			albumArtItem.destroy();
			expect(albumArtItem.destroyControls).toHaveBeenCalled();
		});
		it('should call inherited',function(){
			spyOn(albumArtItem,'inherited');
			albumArtItem.destroy();
			expect(albumArtItem.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(albumArtItem,'inherited');
			albumArtItem.importProps();
			expect(albumArtItem.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
		it('should call createChrome',function(){
			spyOn(albumArtItem,'createChrome');
			albumArtItem.initComponents();
			expect(albumArtItem.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(albumArtItem,'inherited');
			albumArtItem.initComponents();
			expect(albumArtItem.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(albumArtItem,'inherited');
			albumArtItem.createComponents();
			expect(albumArtItem.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(albumArtItem,'discoverControlParent');
			albumArtItem.createComponents();
			expect(albumArtItem.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(albumArtItem,'createComponents');
			albumArtItem.createChrome();
			expect(albumArtItem.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});