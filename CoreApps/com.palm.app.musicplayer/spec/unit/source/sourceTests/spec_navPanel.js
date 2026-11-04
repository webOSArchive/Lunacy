/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return new kindNavPanel();
//			}
//var objName = "navPanel";
//
//var before = function (obj, callbacks) {
//		//obj.$.navPlaylists;
//		obj.$.navPlaylists.ShowPopNewPlaylist = function(){};
//	}
//var event = [[null, {rowIndex:0, dragInfo: {}}]];
//var params = {
//};
//specOut(creator, objName, before, params);


describe('navPanel tests', function(){
	var creator = function (){
				return new kindNavPanel();
			};
	var navPanel;
	beforeEach(function(){
		navPanel = creator();
		var before = function (obj, callbacks) {
		//obj.$.navPlaylists;
		obj.$.navPlaylists.ShowPopNewPlaylist = function(){};
	};
		var callback = function(one, fun){return fun;};
		before(navPanel,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof navPanel.controls).toBe('object');
		expect(typeof navPanel.children).toBe('object');
		expect(navPanel._componentNameMap).toEqual({"control":13,"toolbar":2});
		expect(typeof navPanel.$).toBe('object');
		expect(navPanel.domStyles).toEqual({});
		expect(navPanel.domAttributes).toEqual({"id":"kindNavPanel"});
		expect(typeof navPanel.owner).toBe('object');
		expect(navPanel.name).toEqual("kindNavPanel");
		expect(navPanel.id).toEqual("kindNavPanel");
		expect(navPanel.controlParent).toEqual(undefined);
		expect(typeof navPanel.layout).toBe('object');
		expect(typeof navPanel.objCurrNavItem).toBe('object');
		expect(navPanel.kind).toEqual("VFlexBox");
		//expect(navPanel.events).toEqual({"onClick_NowPlaying":"","onClick_ListView":"","onResetDetailView":"","onClick_ExhibitionMode":"","onRequestPlaylists_NavPanel":"","onClickPlaylist_NavPanel":"","onCreatePlaylist_NavPanel":"","onDeletePlaylist_NavPanel":""});
		expect(navPanel.strNowPlayingType).toEqual("");
		expect(navPanel.strNowPlayingText).toEqual("");
		expect(navPanel.strNowPlayingArtist).toEqual("");
		expect(navPanel.arNowPlayingThumbs).toEqual([]);
		expect(navPanel.strPlaylistKind).toEqual("");
		expect(navPanel.strPlaylistID).toEqual("");
		expect(navPanel.kindName).toEqual("kindNavPanel");
		//expect(navPanel.kindComponents).toEqual([{"name":"navNowPlaying","className":"playing enyo-toolbar-light","kind":"Drawer","open":true,"onOpenAnimationComplete":"onOpenAnimationComplete","components":[{"kind":"Control","className":"content","layoutKind":"HFlexLayout","pack":"start","align":"start","onclick":"onclick_NowPlaying","components":[{"kind":"Control","name":"ctrlDefaultThumb","showing":false,"className":"cover","layoutKind":"VFlexLayout","align":"center","pack":"center","components":[{"className":"img","name":"imgContainer","layoutKind":"VFlexLayout","align":"center","pack":"center","components":[{"name":"imgAlbumArt","kind":"Image"}]}]},{"kind":"Control","className":"info","flex":1,"components":[{"name":"lblNowPlayingText","content":"","className":"label"},{"name":"lblNowPlayingSubText","content":"","className":"title"}]},{"kind":"Control","className":"shadow"}]}]},{"name":"navLibrary","kind":"DividerDrawer","className":"library","caption":"Library","animate":false,"onOpenChanged":"onOpenAnimationComplete","components":[{"name":"libSongs","kind":"Item","layoutKind":"HFlexLayout","pack":"start","align":"center","navstring":"Songs","className":"songs active","onclick":"onClickLibrary","components":[{"className":"icon"},{"content":"Songs","className":"name","flex":1},{"kind":"Spinner","showing":false,"name":"songSpinner"}]},{"name":"libArtists","kind":"Item","layoutKind":"HFlexLayout","pack":"start","align":"center","navstring":"Artists","className":"artists","onclick":"onClickLibrary","components":[{"className":"icon"},{"content":"Artists","className":"name","flex":1}]},{"name":"libAlbums","kind":"Item","layoutKind":"HFlexLayout","pack":"start","align":"center","navstring":"Albums","className":"albums","onclick":"onClickLibrary","components":[{"className":"icon"},{"content":"Albums","className":"name","flex":1}]},{"name":"libGenres","kind":"Item","layoutKind":"HFlexLayout","pack":"start","align":"center","navstring":"Genres","className":"genres","onclick":"onClickLibrary","components":[{"className":"icon"},{"content":"Genres","className":"name","flex":1}]}]},{"name":"navPlaylistDrawer","kind":"DividerDrawer","caption":"Playlists","flex":1,"animate":false,"onOpenChanged":"onOpenAnimationComplete","components":[{"name":"navPlaylists","kind":"navplaylists","flex":1,"onRequestPlaylists":"doRequestPlaylists_NavPanel","onClickPlaylist":"onClickPlaylist","onCreatePlaylist":"doCreatePlaylist_NavPanel","onDeletePlaylist":"doDeletePlaylist_NavPanel"}]},{"kind":"Toolbar","pack":"center","className":"enyo-toolbar-light","components":[{"name":"btnNewPlaylist","kind":"IconButton","label":"New Playlist","className":"enyo-button-light","onclick":"onclick_btnNewPlaylist"}]},{"name":"navExhibitionTest","kind":"HFlexBox","tapHighlight":false,"height":"40px","className":"navMainOff","showing":false,"components":[{"content":"Exhibition","className":"navMainLbl","onclick":"doClick_ExhibitionMode"}]}]);
		expect(navPanel.onClick_NowPlaying).toEqual("");
		expect(navPanel.onClick_ListView).toEqual("");
		expect(navPanel.onResetDetailView).toEqual("");
		expect(navPanel.onClick_ExhibitionMode).toEqual("");
		expect(navPanel.onRequestPlaylists_NavPanel).toEqual("");
		expect(navPanel.onClickPlaylist_NavPanel).toEqual("");
		expect(navPanel.onCreatePlaylist_NavPanel).toEqual("");
		expect(navPanel.onDeletePlaylist_NavPanel).toEqual("");
		expect(navPanel.published).toEqual({"layoutKind":""});
		expect(navPanel.controlParentName).toEqual("client");
		expect(navPanel.defaultKind).toEqual("Control");
		expect(navPanel.onclick).toEqual("");
		expect(navPanel.onmousedown).toEqual("");
		expect(navPanel.onmouseup).toEqual("");
		expect(navPanel.style).toEqual("");
		expect(navPanel.className).toEqual("");
		expect(navPanel.container).toEqual(null);
		expect(navPanel.parent).toEqual(null);
		expect(navPanel.generated).toEqual(false);
		expect(navPanel.allowHtml).toEqual(false);
		expect(navPanel.content).toEqual("");
		expect(navPanel.nodeTag).toEqual("div");
		expect(navPanel.node).toEqual(null);
		expect(navPanel.showing).toEqual(true);
		expect(navPanel.prepend).toEqual(false);
		expect(navPanel.wantsEvents).toEqual(true);
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(navPanel,'inherited');
			navPanel.create();
			expect(navPanel.inherited).toHaveBeenCalledWith({});
		});
		it('should set objCurrNavItem',function(){
			navPanel.objCurrNavItem = null;
			navPanel.create();
			expect(navPanel.objCurrNavItem).toBe(navPanel.$.libSongs);
		});
		
	});
	describe('test (rendered)', function(){
		it('should call inherited',function(){
			spyOn(navPanel,'inherited');
			navPanel.rendered();
			expect(navPanel.inherited).toHaveBeenCalledWith({});
		});
		it('should call onOpenAnimationComplete',function(){
			spyOn(navPanel,'onOpenAnimationComplete');
			navPanel.rendered();
			expect(navPanel.onOpenAnimationComplete).toHaveBeenCalled();
		});
		it('should call renderList',function(){
			spyOn(navPanel.$.navPlaylists,'renderList');
			navPanel.rendered();
			expect(navPanel.$.navPlaylists.renderList).toHaveBeenCalled();
		});
	
	});
	describe('test (onclick_btnNewPlaylist)', function(){
		it('should call renderList',function(){
			spyOn(navPanel.$.navPlaylists,'ShowPopNewPlaylist');
			navPanel.onclick_btnNewPlaylist();
			expect(navPanel.$.navPlaylists.ShowPopNewPlaylist).toHaveBeenCalled();
		});
	});
	describe('test (onClickLibrary)', function(){
		beforeEach(function(){
			spyOn(navPanel,'changeSelectedLibrary');
		});
		it('should call changeSelectedLibrary',function(){
			navPanel.onClickLibrary({navstring: "asdf"});
			expect(navPanel.changeSelectedLibrary).toHaveBeenCalledWith({navstring: "asdf"});
		});
		it('should call doClick_ListView',function(){
			spyOn(enyo,'asyncMethod');
			navPanel.onClickLibrary({navstring: "asdf"});
			expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doClick_ListView");
			expect(enyo.asyncMethod.mostRecentCall.args[2]).toBe("asdf");
		});
	});
	describe('test (changeSelectedLibrary)', function(){
	
		describe("when objCurrNavItem is the same", function(){
			var objParam;
			beforeEach(function(){
				objParam = {removeClass: function(){}, addClass: function(){}};
				navPanel.objCurrNavItem = objParam
			});
			describe("when reset is false", function(){
				var reset = false;
				
				it("shouldnt call clearSelection", function(){
					spyOn(navPanel.$.navPlaylists,"clearSelection");
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(navPanel.$.navPlaylists.clearSelection).not.toHaveBeenCalled();
				});
				it("should set objCurrNavItem to objParam", function(){
					navPanel.objCurrNavItem = false;
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(navPanel.objCurrNavItem).toBe(objParam);
				});
				it("shouldnt call removeClass", function(){
					spyOn(navPanel.objCurrNavItem, "removeClass");
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(navPanel.objCurrNavItem.removeClass).not.toHaveBeenCalled();
				});
				
				it("shouldnt call doResetDetailView", function(){
					spyOn(enyo,'asyncMethod');
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(enyo.asyncMethod).not.toHaveBeenCalled();
				});
				
			});
			describe("when reset is true", function(){
				var reset =true;
				it("should call doResetDetailView", function(){
					spyOn(enyo,'asyncMethod');
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doResetDetailView");
				});
				it("shouldnt call clearSelection", function(){
					spyOn(navPanel.$.navPlaylists,"clearSelection");
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(navPanel.$.navPlaylists.clearSelection).not.toHaveBeenCalled();
				});
				it("should set objCurrNavItem to objParam", function(){
					navPanel.objCurrNavItem = false;
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(navPanel.objCurrNavItem).toBe(objParam);
				});
				it("shouldnt call removeClass", function(){
					spyOn(navPanel.objCurrNavItem, "removeClass");
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(navPanel.objCurrNavItem.removeClass).not.toHaveBeenCalled();
				});
			});
			
		});
		
		describe("when objCurrNavItem is different", function(){
			var objParam;
			beforeEach(function(){
				objParam = {removeClass: function(){}, addClass: function(){}};
				navPanel.objCurrNavItem = null;
			});
			it("should call removeClass", function(){
				navPanel.objCurrNavItem = {removeClass: function(){}};
				spyOn(navPanel.objCurrNavItem, "removeClass");
				var temp = navPanel.objCurrNavItem;
				navPanel.changeSelectedLibrary(objParam, true);
				expect(temp.removeClass).toHaveBeenCalledWith("active");
			});
			
			describe("when reset is false", function(){
				var reset = false;
				it("shouldnt call doResetDetailView", function(){
					spyOn(enyo,'asyncMethod');
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(enyo.asyncMethod).not.toHaveBeenCalled();
				});
				it("should call clearSelection", function(){
					spyOn(navPanel.$.navPlaylists,"clearSelection");
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(navPanel.$.navPlaylists.clearSelection).toHaveBeenCalled();
				});
				it("should set objCurrNavItem to objParam", function(){
					navPanel.objCurrNavItem = false;
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(navPanel.objCurrNavItem).toBe(objParam);
				});
				
			});
			describe("when reset is true", function(){
				var reset =true;
				it("shouldnt call doResetDetailView", function(){
					spyOn(enyo,'asyncMethod');
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(enyo.asyncMethod).not.toHaveBeenCalled();
				});
				it("should call clearSelection", function(){
					spyOn(navPanel.$.navPlaylists,"clearSelection");
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(navPanel.$.navPlaylists.clearSelection).toHaveBeenCalled();
				});
				it("should set objCurrNavItem to objParam", function(){
					navPanel.objCurrNavItem = false;
					navPanel.changeSelectedLibrary(objParam, reset);
					expect(navPanel.objCurrNavItem).toBe(objParam);
				});
			});
		});
	});
	describe('test (onClickPlaylist)', function(){
		it('should call changeSelectedPlaylist',function(){
			spyOn(navPanel,'changeSelectedPlaylist');
			navPanel.onClickPlaylist(null, 2);
			expect(navPanel.changeSelectedPlaylist).toHaveBeenCalledWith(2);
		});
	});
	describe('test (changeSelectedPlaylist)', function(){
		var objClickPlaylist;
		beforeEach(function(){
			objClickPlaylist = {strPlaylistID: "asdf"};
		});
		describe("objCurrnavItem exists", function(){
			beforeEach(function(){
				navPanel.objCurrNavItem = {removeClass: function(){}};
			});
			it("should remove class", function(){
				var temp = spyOn(navPanel.objCurrNavItem, "removeClass");
				navPanel.changeSelectedPlaylist(objClickPlaylist);
				expect(temp).toHaveBeenCalledWith("active");
			});
			it("should call doResetDetailView", function(){
				spyOn(enyo,'asyncMethod');
				navPanel.changeSelectedPlaylist(objClickPlaylist);
				expect(enyo.asyncMethod).toHaveBeenCalled();
				expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doClickPlaylist_NavPanel");
				expect(enyo.asyncMethod.mostRecentCall.args[2]).toBe(objClickPlaylist);
			});
			it("shoudl call this.$.navPlaylists.setStrSelectedPlaylistID", function(){
				spyOn(navPanel.$.navPlaylists, "setStrSelectedPlaylistID");
				navPanel.changeSelectedPlaylist(objClickPlaylist);
				expect(navPanel.$.navPlaylists.setStrSelectedPlaylistID).toHaveBeenCalledWith("asdf");
			});
			it("should unset objCurrNavItem", function(){
				navPanel.objCurrNavItem = {removeClass: function(){}};
				navPanel.changeSelectedPlaylist(objClickPlaylist);
				expect(navPanel.objCurrNavItem).toBe(undefined);
			});
			
		});
		describe("objCurrnavItem doesn't exists", function(){
			beforeEach(function(){
				navPanel.objCurrNavItem = false;
			});
			it('should call changeSelectedPlaylist',function(){
				spyOn(navPanel,'changeSelectedPlaylist');
				navPanel.onClickPlaylist(null, 2);
				expect(navPanel.changeSelectedPlaylist).toHaveBeenCalledWith(2);
			});
			it("should unset objCurrNavItem", function(){
				navPanel.objCurrNavItem = {removeClass: function(){}};
				navPanel.changeSelectedPlaylist(objClickPlaylist);
				expect(navPanel.objCurrNavItem).toBe(undefined);
			});
			it("shoudl call this.$.navPlaylists.setStrSelectedPlaylistID", function(){
				spyOn(navPanel.$.navPlaylists, "setStrSelectedPlaylistID");
				navPanel.changeSelectedPlaylist(objClickPlaylist);
				expect(navPanel.$.navPlaylists.setStrSelectedPlaylistID).toHaveBeenCalledWith("asdf");
			});
			it("should call doResetDetailView", function(){
				spyOn(enyo,'asyncMethod');
				navPanel.changeSelectedPlaylist(objClickPlaylist);
				expect(enyo.asyncMethod).toHaveBeenCalled();
				expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doClickPlaylist_NavPanel");
				expect(enyo.asyncMethod.mostRecentCall.args[2]).toBe(objClickPlaylist);
			});
		
		});
		
	});
	describe('test (editPlaylist)', function(){
		it('should call changeSelectedPlaylist',function(){
			spyOn(navPanel.$.navPlaylists, "setStrEditPlaylistID");
			navPanel.editPlaylist(5);
			expect(navPanel.$.navPlaylists.setStrEditPlaylistID).toHaveBeenCalledWith(5);
		});
	});
//taking out because not used anywhere
//	describe('test (showNowPlaying)', function(){
//		it('should call changeSelectedPlaylist',function(){
//			spyOn(navPanel.$.navNowPlaying, "setOpen");
//			navPanel.showNowPlaying();
//			expect(navPanel.$.navNowPlaying.setOpen).toHaveBeenCalledWith(true);
//		});
//	});
	describe('test (setNowPlayingMode)', function(){
		var objSetPlaybackList;
		beforeEach(function(){
			spyOn(Utilities, "getTrackImage").andReturn(""); 
			objSetPlaybackList = {strListType: "1", strListText: "2", strListSubText: "2.5", strArtist: "3", arThumbnails: [{data : true}]};
			navPanel.$.imgAlbumArt = {setSrc : function(){}};
		});
		
		it('should set strNowPlayingType',function(){
			navPanel.strNowPlayingType = null;
			navPanel.setNowPlayingMode(objSetPlaybackList);
			expect(navPanel.strNowPlayingType).toBe("1");
		});
		it('should set strNowPlayingText',function(){
			navPanel.strNowPlayingText = null;
			navPanel.setNowPlayingMode(objSetPlaybackList);
			expect(navPanel.strNowPlayingText).toBe("2");
		});
		it('should set strNowPlayingArtist',function(){
			navPanel.strNowPlayingArtist = null;
			navPanel.setNowPlayingMode(objSetPlaybackList);
			expect(navPanel.strNowPlayingArtist).toBe("3");
		});
		it('should set arNowPlayingThumbs',function(){
			navPanel.arNowPlayingThumbs = null;
			navPanel.setNowPlayingMode(objSetPlaybackList);
			expect(navPanel.arNowPlayingThumbs).toBe(objSetPlaybackList.arThumbnails);
		});
		describe("when strListType is Playlist", function(){
			beforeEach(function(){
				objSetPlaybackList.strListType = "Playlist";
				objSetPlaybackList.strPlaylistKind = "5";
				objSetPlaybackList.strPlaylistID = "6";
			});
			it('should set strPlaylistKind',function(){
				navPanel.strPlaylistKind = null;
				navPanel.setNowPlayingMode(objSetPlaybackList);
				expect(navPanel.strPlaylistKind).toBe("5");
			});
			it('should set strPlaylistID',function(){
				navPanel.strPlaylistID = null;
				navPanel.setNowPlayingMode(objSetPlaybackList);
				expect(navPanel.strPlaylistID).toBe("6");
			});
			
			
		});
		describe("when strListType isnt Playlist", function(){
			beforeEach(function(){
				objSetPlaybackList.strListType = "asdf";
			});
			it('shouldnt set strPlaylistKind',function(){
				navPanel.strPlaylistKind = null;
				navPanel.setNowPlayingMode(objSetPlaybackList);
				expect(navPanel.strPlaylistKind).toBe("");
			});
			it('shouldnt set strPlaylistID',function(){
				navPanel.strPlaylistID = null;
				navPanel.setNowPlayingMode(objSetPlaybackList);
				expect(navPanel.strPlaylistID).toBe("");
			});
		});
		
		it("should set nowplaying text", function(){
			spyOn(navPanel.$.lblNowPlayingText, "setContent");
			navPanel.setNowPlayingMode(objSetPlaybackList);
			expect(navPanel.$.lblNowPlayingText.setContent).toHaveBeenCalledWith("2");
		});
		it("should set nowplaying text", function(){
			spyOn(navPanel.$.lblNowPlayingSubText, "setContent");
			navPanel.setNowPlayingMode(objSetPlaybackList);
			expect(navPanel.$.lblNowPlayingSubText.setContent).toHaveBeenCalledWith("2.5");
		});
		it("should call ctrlDefaultThumb show", function(){
			spyOn(navPanel.$.ctrlDefaultThumb, "show");
			navPanel.setNowPlayingMode(objSetPlaybackList);
			expect(navPanel.$.ctrlDefaultThumb.show).toHaveBeenCalled();
		});
		it("should call Utilities getTrackImage", function(){
			navPanel.setNowPlayingMode(objSetPlaybackList);
			expect(Utilities.getTrackImage).toHaveBeenCalledWith(true, 54);
		});
		
		it("should call imgContainer hide", function(){
			spyOn(navPanel.$.imgContainer, "hide");
			navPanel.setNowPlayingMode(objSetPlaybackList);
			expect(navPanel.$.imgContainer.hide).toHaveBeenCalled();
		});
		it("shouldnt call imgContainer show", function(){
			spyOn(navPanel.$.imgContainer, "show");
			navPanel.setNowPlayingMode(objSetPlaybackList);
			expect(navPanel.$.imgContainer.show).not.toHaveBeenCalled();
		});
		it("shouldnt call imgAlbumArt setSrc", function(){
			spyOn(navPanel.$.imgAlbumArt, "setSrc");
			navPanel.setNowPlayingMode(objSetPlaybackList);
			expect(navPanel.$.imgAlbumArt.setSrc).not.toHaveBeenCalled();
		});
		describe("when utilities returns a strThumURl not ''", function(){
			beforeEach(function(){
				Utilities.getTrackImage.andReturn("blah");
				
			});
			it("shouldnt call imgContainer hide", function(){
				spyOn(navPanel.$.imgContainer, "hide");
				navPanel.setNowPlayingMode(objSetPlaybackList);
				expect(navPanel.$.imgContainer.hide).not.toHaveBeenCalled();
			});
			it("should call imgContainer show", function(){
				spyOn(navPanel.$.imgContainer, "show");
				navPanel.setNowPlayingMode(objSetPlaybackList);
				expect(navPanel.$.imgContainer.show).toHaveBeenCalled();
			});
			it("should call imgAlbumArt setSrc", function(){
				spyOn(navPanel.$.imgAlbumArt, "setSrc");
				navPanel.setNowPlayingMode(objSetPlaybackList);
				expect(navPanel.$.imgAlbumArt.setSrc).toHaveBeenCalledWith("blah");
			});
		});
		
	});
	describe('test (updateNowPlaying)', function(){
		describe("whene strNowPlayingType is a song", function(){
			var objSetPlaybackList;
			beforeEach(function(){
				navPanel.strNowPlayingType = "Song";
				objSetPlaybackList = {strTrackImage: "asdfimg", strListType: "1", strTrackTitle: "2", strTrackArtist: "2.5", strArtist: "3", arThumbnails: [{data : true}]};
				
			});
			it("should set nowplaying text", function(){
				spyOn(navPanel.$.lblNowPlayingText, "setContent");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(navPanel.$.lblNowPlayingText.setContent).toHaveBeenCalledWith("2");
			});
			it("should set lblNowPlayingSubText text", function(){
				spyOn(navPanel.$.lblNowPlayingSubText, "setContent");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(navPanel.$.lblNowPlayingSubText.setContent).toHaveBeenCalledWith("2.5");
			});
			it("should show ctrlDefaultThumb text", function(){
				spyOn(navPanel.$.ctrlDefaultThumb, "show");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(navPanel.$.ctrlDefaultThumb.show).toHaveBeenCalled();
			});
			
			it("should call Utilities getTrackImage", function(){
				spyOn(navPanel.$.imgAlbumArt, "setSrc");
				spyOn(Utilities, "getTrackImage");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(Utilities.getTrackImage).toHaveBeenCalledWith("asdfimg", 56);
			});
			it("should call imgAlbumArt setSrc", function(){
				spyOn(navPanel.$.imgAlbumArt, "setSrc");
				spyOn(Utilities, "getTrackImage").andReturn("asdf");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(navPanel.$.imgAlbumArt.setSrc).toHaveBeenCalledWith("asdf");
			});
			it("should show imgContainer text", function(){
				//BUG FIX HERE, WASN't SHOWING NEW ART
				spyOn(navPanel.$.imgContainer, "show");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(navPanel.$.imgContainer.show).toHaveBeenCalled();
			});
			it("should hide imgContainer text", function(){
				objSetPlaybackList.strTrackImage = "";
				spyOn(navPanel.$.imgContainer, "hide");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(navPanel.$.imgContainer.hide).toHaveBeenCalled();
			});
			
			it("shouldnt call imgAlbumArt setSrc", function(){
				objSetPlaybackList.strTrackImage = "";
				spyOn(navPanel.$.imgAlbumArt, "setSrc");
				spyOn(Utilities, "getTrackImage").andReturn("asdf");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(navPanel.$.imgAlbumArt.setSrc).not.toHaveBeenCalled();
			});
			
			
		});
		describe("whene strNowPlayingType isnt a song", function(){
			var objSetPlaybackList;
			beforeEach(function(){
				objSetPlaybackList = {strTrackImage: "asdfimg", strListType: "1", strTrackTitle: "2", strTrackArtist: "2.5", strArtist: "3", arThumbnails: [{data : true}]};
				this.strNowPlayingType = "asdf";
			});
			it("shouldnt set nowplaying text", function(){
				spyOn(navPanel.$.lblNowPlayingText, "setContent");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(navPanel.$.lblNowPlayingText.setContent).not.toHaveBeenCalled();
			});
			it("shouldnt set lblNowPlayingSubText text", function(){
				spyOn(navPanel.$.lblNowPlayingSubText, "setContent");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(navPanel.$.lblNowPlayingSubText.setContent).not.toHaveBeenCalled();
			});
			it("shouldnt show ctrlDefaultThumb text", function(){
				spyOn(navPanel.$.ctrlDefaultThumb, "show");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(navPanel.$.ctrlDefaultThumb.show).not.toHaveBeenCalled();
			});
			
			it("shouldnt call Utilities getTrackImage", function(){
				spyOn(Utilities, "getTrackImage");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(Utilities.getTrackImage).not.toHaveBeenCalled();
			});
			it("shouldnt call imgAlbumArt setSrc", function(){
				spyOn(navPanel.$.imgAlbumArt, "setSrc");
				spyOn(Utilities, "getTrackImage").andReturn("asdf");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(navPanel.$.imgAlbumArt.setSrc).not.toHaveBeenCalled();
			});
			it("shouldnt show imgContainer text", function(){
				//BUG FIX HERE, WASN't SHOWING NEW ART
				spyOn(navPanel.$.imgContainer, "show");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(navPanel.$.imgContainer.show).not.toHaveBeenCalled();
			});
			it("shouldnt hide imgAlbumArt text", function(){
				objSetPlaybackList.strTrackImage = "";
				spyOn(navPanel.$.imgAlbumArt, "hide");
				navPanel.updateNowPlaying(objSetPlaybackList);
				expect(navPanel.$.imgAlbumArt.hide).not.toHaveBeenCalled();
			});
		});
	});
	
	describe('test (onclick_NowPlaying)', function(){
		describe("when strNowPlayingType is song", function(){
			beforeEach(function(){
				navPanel.strNowPlayingType = "Song";
			});
			it("should call changeSelected library", function(){
				spyOn(navPanel, "changeSelectedLibrary");
				navPanel.onclick_NowPlaying(null, null);
				expect(navPanel.changeSelectedLibrary).toHaveBeenCalledWith(navPanel.$.libSongs, false);
			});
			it('should call doClick_ListView',function(){
				spyOn(enyo,'asyncMethod');
				navPanel.onclick_NowPlaying(null, null);
				expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doClick_ListView");
				expect(enyo.asyncMethod.mostRecentCall.args[2]).toBe("Songs");
			});
//			it('should call doClick_NowPlaying',function(){
//				spyOn(enyo,'asyncMethod');
//				navPanel.onclick_NowPlaying(null, null);
//				expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doClick_NowPlaying");
//			});
			
		});
		describe("when strNowPlayingType is Artist", function(){
			beforeEach(function(){
				navPanel.strNowPlayingType = "Artist";
			});
			it("should call changeSelected library", function(){
				spyOn(navPanel, "changeSelectedLibrary");
				navPanel.onclick_NowPlaying(null, null);
				expect(navPanel.changeSelectedLibrary).toHaveBeenCalledWith(navPanel.$.libArtists, false);
			});
			it('should call doClick_ListView',function(){
				spyOn(enyo,'asyncMethod');
				navPanel.onclick_NowPlaying(null, null);
				expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doClick_NowPlaying");
			});
//			it('should call doClick_NowPlaying',function(){
//				spyOn(enyo,'asyncMethod');
//				navPanel.onclick_NowPlaying(null, null);
//				expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doClick_NowPlaying");
//			});
			
		});
		describe("when strNowPlayingType is Albums", function(){
			beforeEach(function(){
				navPanel.strNowPlayingType = "Album";
			});
			it("should call changeSelected library", function(){
				spyOn(navPanel, "changeSelectedLibrary");
				navPanel.onclick_NowPlaying(null, null);
				expect(navPanel.changeSelectedLibrary).toHaveBeenCalledWith(navPanel.$.libAlbums, false);
			});
			it('should call doClick_ListView',function(){
				spyOn(enyo,'asyncMethod');
				navPanel.onclick_NowPlaying(null, null);
				expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doClick_NowPlaying");
			});
//			it('should call doClick_NowPlaying',function(){
//				spyOn(enyo,'asyncMethod');
//				navPanel.onclick_NowPlaying(null, null);
//				expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doClick_NowPlaying");
//			});
			
		});
			describe("when strNowPlayingType is Genre", function(){
			beforeEach(function(){
				navPanel.strNowPlayingType = "Genre";
			});
			it("should call changeSelected library", function(){
				spyOn(navPanel, "changeSelectedLibrary");
				navPanel.onclick_NowPlaying(null, null);
				expect(navPanel.changeSelectedLibrary).toHaveBeenCalledWith(navPanel.$.libGenres, false);
			});
			it('should call doClick_ListView',function(){
				spyOn(enyo,'asyncMethod');
				navPanel.onclick_NowPlaying(null, null);
				expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doClick_NowPlaying");
			});
//			it('should call doClick_NowPlaying',function(){
//				spyOn(enyo,'asyncMethod');
//				navPanel.onclick_NowPlaying(null, null);
//				expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doClick_NowPlaying");
//			});
			
		});
		describe("when strNowPlayingType is Playlist", function(){
			beforeEach(function(){
				navPanel.strNowPlayingType = "Playlist";
			});
			it("shouldnt call changeSelected library", function(){
				spyOn(navPanel, "changeSelectedLibrary");
				navPanel.onclick_NowPlaying(null, null);
				expect(navPanel.changeSelectedLibrary).not.toHaveBeenCalled();
			});
			it("should call changeSelected changeSelectedPlaylist", function(){
				spyOn(navPanel, "changeSelectedPlaylist");
				navPanel.onclick_NowPlaying(null, null);
				expect(navPanel.changeSelectedPlaylist).toHaveBeenCalled();
			});
			it("should call navPlaylists refreshlist", function(){
				spyOn(navPanel.$.navPlaylists, "refreshList");
				navPanel.onclick_NowPlaying(null, null);
				expect(navPanel.$.navPlaylists.refreshList).toHaveBeenCalled();
			});
			it('shouldnt call doClick_ListView',function(){
				spyOn(navPanel.$.navPlaylists, "refreshList");
				spyOn(navPanel,'changeSelectedPlaylist');
				spyOn(enyo,'asyncMethod');
				navPanel.onclick_NowPlaying(null, null);
				expect(enyo.asyncMethod).not.toHaveBeenCalled();
			});
//			it('should call doClick_NowPlaying',function(){
//				spyOn(enyo,'asyncMethod');
//				navPanel.onclick_NowPlaying(null, null);
//				expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doClick_NowPlaying");
//			});
			
		});
		describe("when strNowPlayingType is nothing", function(){
			beforeEach(function(){
				navPanel.strNowPlayingType = "nothing";
			});
			it("should call changeSelected library", function(){
				spyOn(navPanel, "changeSelectedLibrary");
				navPanel.onclick_NowPlaying(null, null);
				expect(navPanel.changeSelectedLibrary).not.toHaveBeenCalled();
			});
		
			it('shouldnt call doClick_ListView',function(){
				spyOn(enyo,'asyncMethod');
				navPanel.onclick_NowPlaying(null, null);
				expect(enyo.asyncMethod).not.toHaveBeenCalled();
			});
//			it('should call doClick_NowPlaying',function(){
//				spyOn(enyo,'asyncMethod');
//				navPanel.onclick_NowPlaying(null, null);
//				expect(enyo.asyncMethod.mostRecentCall.args[1]).toBe("doClick_NowPlaying");
//			});
			
		});
	});
	
	describe('test (playlistDeleted)', function(){
		it('should clear strPlaylistID',function(){
			navPanel.strPlaylistID = "1";
			navPanel.playlistDeleted({strPlaylistID : "1"});
			expect(navPanel.strPlaylistID).toBe(undefined);
		});
		it('shouldnt clear strPlaylistID',function(){
			navPanel.strPlaylistID = 1;
			navPanel.playlistDeleted({strPlaylistID : "1"});
			expect(navPanel.strPlaylistID).toBe(1);
		});
	});
	describe('test (onOpenAnimationComplete)', function(){
		it('should call resizeContent',function(){
			spyOn(navPanel, "resizeContent");
			navPanel.onOpenAnimationComplete({strPlaylistID : "1"});
			expect(navPanel.resizeContent).toHaveBeenCalled();
		});
		
	});
	describe('test (resizeContent)', function(){
		it('should call resized',function(){
			spyOn(navPanel, "resized");
			navPanel.resizeContent();
			expect(navPanel.resized).toHaveBeenCalled();
		});
		it("should call getBounds", function(){
			spyOn(navPanel.$.navPlaylistDrawer, "getBounds").andReturn({height: 4, width:2});
			navPanel.resizeContent();
			expect(navPanel.$.navPlaylistDrawer.getBounds).toHaveBeenCalled();
		})
		it("should call getBounds on caption", function(){
			spyOn(navPanel.$.navPlaylistDrawer.$.caption, "getBounds").andReturn({height: 4, width:2});
			navPanel.resizeContent();
			expect(navPanel.$.navPlaylistDrawer.$.caption.getBounds).toHaveBeenCalled();
		})
		it('should call this.$.navPlaylists resized',function(){
			spyOn(navPanel.$.navPlaylistDrawer, "getBounds").andReturn({height: 10, width:2});
			spyOn(navPanel.$.navPlaylistDrawer.$.caption, "getBounds").andReturn({height: 4, width:2});
			
			spyOn(navPanel.$.navPlaylists, "resize");
			navPanel.resizeContent({strPlaylistID : "1"});
			expect(navPanel.$.navPlaylists.resize).toHaveBeenCalledWith(6);
		});
		
	});
	describe('test (refreshPlaylists)', function(){
		it("should call renderList()", function(){
			spyOn(navPanel.$.navPlaylists, "renderList");
			navPanel.refreshPlaylists();
			expect(navPanel.$.navPlaylists.renderList).toHaveBeenCalled();
		});
	});
	describe('test (setIndexingStatus)', function(){
		it("should call renderList()", function(){
			spyOn(navPanel.$.songSpinner, "setShowing");
			navPanel.setIndexingStatus(true);
			expect(navPanel.$.songSpinner.setShowing).toHaveBeenCalledWith(true);
		});
		it("should call renderList() false", function(){
			spyOn(navPanel.$.songSpinner, "setShowing");
			navPanel.setIndexingStatus(false);
			expect(navPanel.$.songSpinner.setShowing).toHaveBeenCalledWith(false);
		});
	});
	
	
});