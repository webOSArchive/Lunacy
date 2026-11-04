/*globals enyoGenra: true, draggableItem:true, enyoSong:true, enyoArtist:true, enyoAlbum:true, enyo, $L, event, Utilities, window, describe, it, expect, spyOn, beforeEach, afterEach, getJson, jasmine, event:true */

//var creator = function(){
//				return new navplaylists();
//			}
//var objName = "nplaylists";
//
//var before = function (obj, callbacks) {
//		obj.startWatch=function(){};
//		obj.$.popNewPlaylist.openAtCenter = function(){};
//		obj.$.txtPlaylistName = {};
//		obj.$.txtPlaylistName.forceFocus = function(){};
//		obj.$.txtPlaylistName.forceBlur = function(){};
//		obj.$.txtPlaylistName.getValue = function(){};
//		obj.$.txtPlaylistName.setValue = function(){};
//		enyo.keyboard.hide = function(){};
//		//obj.arPlaylists = 
//	}
//var event = [[null, {rowIndex:0, dragInfo: {}}]];
//var params = {
//		goToPlaylist : [[{strPlaylistID : "pid"}]],
//		dragOver : [[null, event]],
//		dragOut : [[null, event]],
//		dragDrop : [[null, event]]
//};
//specOut(creator, objName, before, params);

describe('nplaylists tests', function(){
	var creator = function (){
				return new navplaylists();
			};
	var nplaylists;
	beforeEach(function(){
		nplaylists = creator();
		var before = function (obj, callbacks) {
		obj.startWatch=function(){};
		obj.$.popNewPlaylist.openAtCenter = function(){};
		obj.$.txtPlaylistName = {};
		obj.$.txtPlaylistName.forceFocus = function(){};
		obj.$.txtPlaylistName.forceBlur = function(){};
		obj.$.txtPlaylistName.getValue = function(){};
		obj.$.txtPlaylistName.setValue = function(){};
		enyo.keyboard.hide = function(){};
		enyo.keyboard.setManualMode = function(){};
		obj.arPlaylists = getJson("mock_playlistArr");
		//obj.arPlaylists = 
	};
		var callback = function(one, fun){return fun;};
		before(nplaylists,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof nplaylists.controls).toBe('object');
		expect(typeof nplaylists.children).toBe('object');
		expect(nplaylists._componentNameMap).toEqual({});
		expect(typeof nplaylists.$).toBe('object');
		expect(nplaylists.domStyles).toEqual({});
		expect(nplaylists.domAttributes).toEqual({"id":"navplaylists"});
		expect(typeof nplaylists.owner).toBe('object');
		expect(nplaylists.name).toEqual("navplaylists");
		expect(nplaylists.id).toEqual("navplaylists");
		expect(nplaylists.controlParent).toEqual(undefined);
		expect(nplaylists.kind).toEqual("Control");
		expect(nplaylists.flex).toEqual(1);
		expect(nplaylists.events).toEqual({"onRequestPlaylists":"","onClickPlaylist":"","onCreatePlaylist":"","onDeletePlaylist":""});
		expect(nplaylists.published).toEqual({"strEditPlaylistID":"","strSelectedPlaylistID":"", strSelectedPlaylistFile: ""});
		expect(nplaylists.intJumpRowOffset).toEqual(1);
		expect(nplaylists.kindName).toEqual("navplaylists");
		expect(nplaylists.kindComponents).toEqual([{"name":"txtResults"},{"name":"listPlaylists","kind":"VirtualList","onSetupRow":"listSetupRow","flex":1,"onclick":"onClick_listPlaylists","components":[{"name":"itemPlaylist","kind":"SwipeableItem","onConfirm":"onclick_btnDelete","className":"playlist","layoutKind":"HFlexLayout","pack":"justify","align":"center","confirmCaption":"Delete","ondragover":"dragOver","ondrop":"dragDrop","ondragout":"dragOut","components":[{"name":"iconPlaylist","className":"icon"},{"name":"playlistName","content":"My Playlist","className":"name","flex":1}]}]},{"name":"popNewPlaylist","kind":"ModalDialog","onOpen":"onOpen_PopNewPlaylist","onClose":"onClose_PopNewPlaylist","caption":"New Playlist","showKeyboardWhenOpening":true,"components":[{"name":"txtPlaylistName","kind":"Input","hint":"Type Playlist Name","autocorrect":false,"spellcheck":false,"style":"margin:13px 0;","onkeypress":"onkeypress_txtPlaylistName"},{"kind":"Control","layoutKind":"HFlexLayout","components":[{"kind":"Button","flex":1,"caption":"Cancel","onclick":"onclick_Cancel"},{"kind":"Button","flex":1,"caption":"Create","onclick":"onclick_Create"}]}]}]);
		expect(nplaylists.onRequestPlaylists).toEqual("");
		expect(nplaylists.onClickPlaylist).toEqual("");
		expect(nplaylists.onCreatePlaylist).toEqual("");
		expect(nplaylists.onDeletePlaylist).toEqual("");
		expect(nplaylists.strEditPlaylistID).toEqual("");
		expect(nplaylists.strSelectedPlaylistID).toEqual("");
		expect(nplaylists.controlParentName).toEqual("client");
		expect(nplaylists.defaultKind).toEqual("Control");
		expect(nplaylists.onclick).toEqual("");
		expect(nplaylists.onmousedown).toEqual("");
		expect(nplaylists.onmouseup).toEqual("");
		expect(nplaylists.layoutKind).toEqual("");
		expect(nplaylists.style).toEqual("");
		expect(nplaylists.className).toEqual("");
		expect(nplaylists.container).toEqual(null);
		expect(nplaylists.parent).toEqual(null);
		expect(nplaylists.generated).toEqual(false);
		expect(nplaylists.allowHtml).toEqual(false);
		expect(nplaylists.content).toEqual("");
		expect(nplaylists.nodeTag).toEqual("div");
		expect(nplaylists.node).toEqual(null);
		expect(nplaylists.showing).toEqual(true);
		expect(nplaylists.prepend).toEqual(false);
		expect(nplaylists.wantsEvents).toEqual(true);
	});
	describe('test (startWatch)', function(){
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(nplaylists,'inherited');
			nplaylists.create();
			expect(nplaylists.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (ready)', function(){
	});
	describe('test (renderList)', function(){
		it('should change arPlaylists',function(){
			nplaylists.arPlaylists = null;
			nplaylists.renderList();
			expect(nplaylists.arPlaylists).toEqual([]);
		});
		it('should change strCurrentRequestID',function(){
			nplaylists.strCurrentRequestID = null;
			nplaylists.renderList();
			expect(nplaylists.strCurrentRequestID).not.toEqual(null);
		});
		it('should call getPlaylists',function(){
			spyOn(nplaylists,'getPlaylists');
			nplaylists.renderList();
			expect(nplaylists.getPlaylists).toHaveBeenCalledWith("com.palm.music.staticplaylists:1");
		});
		it('should call getPlaylists',function(){
			spyOn(nplaylists,'getPlaylists');
			nplaylists.renderList();
			expect(nplaylists.getPlaylists).toHaveBeenCalledWith("com.palm.media.playlist.file:1");
		});
	});
	describe('test (refreshList)', function(){
		it('should call getPlaylists',function(){
			spyOn(nplaylists.$.listPlaylists,'refresh');
			nplaylists.refreshList();
			expect(nplaylists.$.listPlaylists.refresh).toHaveBeenCalled();
		});
	});
	describe('test (scrollerScroll)', function(){
		beforeEach(function(){
			spyOn(nplaylists.$.listPlaylists.$.scroller, "adjustBottom");
		})
		it("should call getPageSize()", function(){
			spyOn(nplaylists.$.listPlaylists, "getPageSize");
			nplaylists.scrollerScroll(1);
			expect(nplaylists.$.listPlaylists.getPageSize).toHaveBeenCalled();
		});
		it("should call adjustTop()", function(){
			spyOn(nplaylists.$.listPlaylists.$.scroller, "adjustTop");
			nplaylists.scrollerScroll(2);
			expect(nplaylists.$.listPlaylists.$.scroller.adjustTop).toHaveBeenCalledWith(2);
		});
		it("should call adjustBottom()", function(){
			
			spyOn(nplaylists.$.listPlaylists, "getPageSize").andReturn(1);
			nplaylists.scrollerScroll(2);
			expect(nplaylists.$.listPlaylists.$.scroller.adjustBottom).toHaveBeenCalledWith(3);
		});
		it("should set this.$.listPlaylists.$.scroller.top", function(){
			nplaylists.$.listPlaylists.$.scroller.top = null;
			nplaylists.scrollerScroll(2);
			expect(nplaylists.$.listPlaylists.$.scroller.top).toBe(2);
		});
		it("should set this.$.listPlaylists.$.scroller.bottom", function(){
			nplaylists.$.listPlaylists.$.scroller.bottom = null;
			spyOn(nplaylists.$.listPlaylists, "getPageSize").andReturn(1);
			nplaylists.scrollerScroll(2);
			expect(nplaylists.$.listPlaylists.$.scroller.bottom).toBe(3);
		});
		
		
		
	});
	describe('test (getPlaylists)', function(){
		it('should call doRequestPlaylists',function(){
			spyOn(nplaylists,'doRequestPlaylists');
			nplaylists.getPlaylists();
			expect(nplaylists.doRequestPlaylists).toHaveBeenCalled();
		});
	});
	describe('test (gotPlaylists)', function(){
		
		it("should set arPlaylists", function(){
			var playlists = getJson("mock_playlistArr");
			nplaylists.strCurrentRequestID = 1;
			nplaylists.arPlaylists = [];
			nplaylists.gotPlaylists({results: playlists}, {objGetPlaylistsRequest : {strCurrentRequestID:1}})
			expect(nplaylists.arPlaylists).not.toBe([]);
		});
		it("shouldnt set arPlaylists", function(){
			var playlists = getJson("mock_playlistArr");
			nplaylists.strCurrentRequestID = 2;
			nplaylists.arPlaylists = null;
			nplaylists.gotPlaylists({results: playlists}, {objGetPlaylistsRequest : {strCurrentRequestID:1}})
			expect(nplaylists.arPlaylists).toBe(null);
		});
		
		it("should resized", function(){
			spyOn(nplaylists.$.listPlaylists, "resized");
			var playlists = getJson("mock_playlistArr");
			nplaylists.gotPlaylists({results: playlists}, {objGetPlaylistsRequest : {strCurrentRequestID:1}})
			expect(nplaylists.$.listPlaylists.resized).toHaveBeenCalled();
		});
		it("should punt", function(){
			spyOn(nplaylists.$.listPlaylists, "punt");
			var playlists = getJson("mock_playlistArr");
			nplaylists.gotPlaylists({results: playlists}, {objGetPlaylistsRequest : {strCurrentRequestID:1}})
			expect(nplaylists.$.listPlaylists.punt).toHaveBeenCalled();
		});
		
		//should set intjumprowoffset to one before a new playlist or a playlist that is gone to via api
		describe("when strEditPlaylistID is empty", function(){
			var playlists;
			beforeEach(function(){
				playlists = getJson("mock_playlistArr");
				nplaylists.strEditPlaylistID = "";
			});
			it("should change this.intJumpRowOffset", function(){
				nplaylists.intJumpRowOffset = null;
				nplaylists.gotPlaylists({results: playlists}, {objGetPlaylistsRequest : {strCurrentRequestID:1}})
				expect(nplaylists.intJumpRowOffset).toBe(null);
			});
		});
		
		
	});
	describe('test (listSetupRow)', function(){
		it("should call addRemoveClass with enyo-first", function(){
			spyOn(nplaylists.$.itemPlaylist,"addRemoveClass");
			var ret = nplaylists.listSetupRow(null, 0);
			expect(nplaylists.$.itemPlaylist.addRemoveClass).toHaveBeenCalledWith("enyo-first", true);
			expect(ret).toBe(true);
		});
		it("should call addRemoveClass with enyo-first false", function(){
			spyOn(nplaylists.$.itemPlaylist,"addRemoveClass");
			var ret = nplaylists.listSetupRow(null, 1);
			expect(nplaylists.$.itemPlaylist.addRemoveClass).toHaveBeenCalledWith("enyo-first", false);
			expect(ret).toBe(true);
		});
		
	
		it("should call addRemoveClass with active", function(){
			nplaylists.strSelectedPlaylistID = nplaylists.arPlaylists[1]._id
			spyOn(nplaylists.$.itemPlaylist,"addRemoveClass");
			var ret = nplaylists.listSetupRow(null, 1);
			expect(nplaylists.$.itemPlaylist.addRemoveClass).toHaveBeenCalledWith("active", true);
			expect(ret).toBe(true);
		});
		it("should call addRemoveClass with active last", function(){
			nplaylists.strSelectedPlaylistID = "";
			spyOn(nplaylists.$.itemPlaylist,"addRemoveClass");
			var ret = nplaylists.listSetupRow(null, 1);
			expect(nplaylists.$.itemPlaylist.addRemoveClass).toHaveBeenCalledWith("active", false);
			expect(ret).toBe(true);
		});
		
		it("should call addRemoveClass with readonly", function(){
			nplaylists.arPlaylists[1]._kind = "com.palm.media.playlist.file:1";
			spyOn(nplaylists.$.itemPlaylist,"addRemoveClass");
			var ret = nplaylists.listSetupRow(null, 1);
			expect(nplaylists.$.itemPlaylist.addRemoveClass).toHaveBeenCalledWith("readonly", true);
			expect(ret).toBe(true);
		});
		it("should call addRemoveClass with readonly false", function(){
			nplaylists.arPlaylists[1]._kind = "";
			spyOn(nplaylists.$.itemPlaylist,"addRemoveClass");
			var ret = nplaylists.listSetupRow(null, 1);
			expect(nplaylists.$.itemPlaylist.addRemoveClass).toHaveBeenCalledWith("readonly", false);
			expect(ret).toBe(true);
		});
		
		it("should call addRemoveClass with editing", function(){
			nplaylists.strEditPlaylistID = nplaylists.arPlaylists[1]._id;
			spyOn(nplaylists.$.itemPlaylist,"addRemoveClass");
			var ret = nplaylists.listSetupRow(null, 1);
			expect(nplaylists.$.itemPlaylist.addRemoveClass).toHaveBeenCalledWith("editing", true);
			expect(ret).toBe(true);
		});
		it("should call addRemoveClass with editing false", function(){
			nplaylists.strEditPlaylistID = nplaylists.arPlaylists[0]._id;
			spyOn(nplaylists.$.itemPlaylist,"addRemoveClass");
			var ret = nplaylists.listSetupRow(null, 1);
			expect(nplaylists.$.itemPlaylist.addRemoveClass).toHaveBeenCalledWith("editing", false);
			expect(ret).toBe(true);
		});
		
		it("should call setContent", function(){
			nplaylists.arPlaylists[1]._kind = "com.palm.music.staticplaylists:1";
			spyOn(nplaylists.$.playlistName,"setContent");
			var ret = nplaylists.listSetupRow(null, 1);
			expect(nplaylists.$.playlistName.setContent).toHaveBeenCalledWith(nplaylists.arPlaylists[1].name);
			expect(ret).toBe(true);
		});
		it("should call setContent", function(){
			nplaylists.arPlaylists[1]._kind = "com.palm.media.playlist.file:1";
			spyOn(nplaylists.$.playlistName,"setContent");
			var ret = nplaylists.listSetupRow(null, 1);
			expect(nplaylists.$.playlistName.setContent).toHaveBeenCalledWith(nplaylists.arPlaylists[1].title);
			expect(ret).toBe(true);
		});
		
		it("should ret false if problem", function(){
			var ret = nplaylists.listSetupRow(null, 1000);
			expect(ret).toBe(false);
		});
		
	});
	describe('test (clearSelection)', function(){
		it('should call setStrSelectedPlaylistID',function(){
			spyOn(nplaylists,'setStrSelectedPlaylistID');
			nplaylists.clearSelection();
			expect(nplaylists.setStrSelectedPlaylistID).toHaveBeenCalledWith("");
		});
		it('should call setStrEditPlaylistID',function(){
			spyOn(nplaylists,'setStrEditPlaylistID');
			nplaylists.clearSelection();
			expect(nplaylists.setStrEditPlaylistID).toHaveBeenCalledWith("");
		});
	});
	describe('test (onClick_listPlaylists)', function(){
		it("should leave strEditPlaylistID if editing", function(){
			nplaylists.strEditPlaylistID = nplaylists.arPlaylists[1]._id;
			nplaylists.onClick_listPlaylists(null, 1);
			expect(nplaylists.strEditPlaylistID).not.toBe("");
		});
		it("should not call goToPlaylist if editing", function(){
			spyOn(nplaylists, "goToPlaylist")
			nplaylists.strEditPlaylistID = nplaylists.arPlaylists[1]._id;
			nplaylists.onClick_listPlaylists(null, 1);
			expect(nplaylists.goToPlaylist).not.toHaveBeenCalled();
		});
		
		it("should change strEditPlaylistID if not editing", function(){
			nplaylists.strEditPlaylistID = "asdf";
			nplaylists.onClick_listPlaylists(null, 1);
			expect(nplaylists.strEditPlaylistID).not.toBe("");
		});
		it("should call goToPlaylist if not editing", function(){
			spyOn(nplaylists, "goToPlaylist")
			nplaylists.strEditPlaylistID = "asdf";
			nplaylists.onClick_listPlaylists(null, 1);
			expect(nplaylists.goToPlaylist).not.toHaveBeenCalled();
		});
		
	});
	describe('test (goToPlaylist)', function(){
		describe('testing (goToPlaylist) with params [{"strPlaylistID":"pid"}]', function(){
			it('should call setStrSelectedPlaylistID',function(){
				spyOn(nplaylists,'setStrSelectedPlaylistID');
				nplaylists.goToPlaylist({"strPlaylistID":"pid"});
				expect(nplaylists.setStrSelectedPlaylistID).toHaveBeenCalledWith("pid");
			});
			it('should call refreshList',function(){
				spyOn(nplaylists,'refreshList');
				nplaylists.goToPlaylist({"strPlaylistID":"pid"});
				expect(nplaylists.refreshList).toHaveBeenCalled();
			});
			it('should call refreshList defined ',function(){
				spyOn(nplaylists,'refreshList');
				nplaylists.goToPlaylist({"strPlaylistID":"pid"}, false);
				expect(nplaylists.refreshList).toHaveBeenCalled();
			});
			it('shouldnt call refreshList defined ',function(){
				spyOn(nplaylists,'refreshList');
				nplaylists.goToPlaylist({"strPlaylistID":"pid"}, true);
				expect(nplaylists.refreshList).not.toHaveBeenCalled();
			});
			it('should call renderlist defined ',function(){
				spyOn(nplaylists,'renderList');
				nplaylists.goToPlaylist({"strPlaylistID":"pid"}, true);
				expect(nplaylists.renderList).toHaveBeenCalled();
			});
			it('shouldnt call renderlist defined ',function(){
				spyOn(nplaylists,'renderList');
				nplaylists.goToPlaylist({"strPlaylistID":"pid"}, false);
				expect(nplaylists.renderList).not.toHaveBeenCalled();
			});
			it('should call doClickPlaylist',function(){
				spyOn(nplaylists,'doClickPlaylist');
				nplaylists.goToPlaylist({"strPlaylistID":"pid"});
				expect(nplaylists.doClickPlaylist).toHaveBeenCalledWith({"strPlaylistID":"pid"});
			});
		});
	});
	describe('test (strEditPlaylistIDChanged)', function(){
		it('should call refreshList',function(){
			spyOn(nplaylists,'refreshList');
			nplaylists.strEditPlaylistIDChanged();
			expect(nplaylists.refreshList).toHaveBeenCalled();
		});
	});
	describe('test (ShowPopNewPlaylist)', function(){
		it('should call his.$.popNewPlaylist.openAtCenter',function(){
			spyOn(nplaylists.$.popNewPlaylist,'openAtCenter');
			nplaylists.ShowPopNewPlaylist();
			expect(nplaylists.$.popNewPlaylist.openAtCenter).toHaveBeenCalled();
		});
	});
	describe('test (onOpen_PopNewPlaylist)', function(){
		it('should call his.$.txtPlaylistNameforceFocus',function(){
			spyOn(nplaylists.$.txtPlaylistName,'forceFocus');
			nplaylists.onOpen_PopNewPlaylist();
			expect(nplaylists.$.txtPlaylistName.forceFocus).toHaveBeenCalled();
		});
		//For some reason it's set to true??? causing user not to reopen keyboard (not reproducable in simple app)
		it('it should set manual mode to false', function(){
			spyOn(enyo.keyboard,'setManualMode');
			nplaylists.onOpen_PopNewPlaylist();
			expect(enyo.keyboard.setManualMode).toHaveBeenCalledWith(false);
			
		});
	});
	describe('test (onClose_PopNewPlaylist)', function(){
		it('should call his.$.popNewPlaylist.forceBlur',function(){
			spyOn(nplaylists.$.txtPlaylistName,'forceBlur');
			nplaylists.onClose_PopNewPlaylist();
			expect(nplaylists.$.txtPlaylistName.forceBlur).toHaveBeenCalled();
		});
		it('should call his.$.keyboard.hide',function(){
			spyOn(enyo.keyboard,'hide');
			nplaylists.onClose_PopNewPlaylist();
			expect(enyo.keyboard.hide).toHaveBeenCalled();
		});
		//must set back or keyboard won't come up anymore
		it('should call his.$.keyboard.setManualMode',function(){
			spyOn(enyo.keyboard,'setManualMode');
			nplaylists.onClose_PopNewPlaylist();
			expect(enyo.keyboard.setManualMode).toHaveBeenCalledWith(false);
		});
		
		
	});
	describe('test (onkeypress_txtPlaylistName)', function(){
		it('should call onclick_Create',function(){
			spyOn(nplaylists,'onclick_Create');
			nplaylists.onkeypress_txtPlaylistName(null, {keyCode: 13});
			expect(nplaylists.onclick_Create).toHaveBeenCalled();
		});
		it('shouldnt call onclick_Create',function(){
			spyOn(nplaylists,'onclick_Create');
			nplaylists.onkeypress_txtPlaylistName(null, {keyCode: "13"});
			expect(nplaylists.onclick_Create).not.toHaveBeenCalled();
		});
	});
	describe('test (onclick_Create)', function(){
		describe("when txtPlaylistName is empty", function(){
			beforeEach(function(){
				nplaylists.$.txtPlaylistName.getValue = function(){return  " "};
			});
			it("shoudl call Utilities.fastTrim", function(){
				spyOn(Utilities,"fastTrim");
				nplaylists.onclick_Create(null,{"rowIndex":0,"dispatchTarget":null});
				expect(Utilities.fastTrim).toHaveBeenCalledWith(" ");
			});
			
			it("shouldnt setvalue of txtPlaylistName", function(){
				spyOn(nplaylists.$.txtPlaylistName, "setValue");
				nplaylists.onclick_Create(null,{"rowIndex":0,"dispatchTarget":null});
				expect(nplaylists.$.txtPlaylistName.setValue).not.toHaveBeenCalled();
			});
			
			it("shouldnt close popup", function(){
				spyOn(nplaylists.$.popNewPlaylist, "close");
				nplaylists.onclick_Create(null,{"rowIndex":0,"dispatchTarget":null});
				expect(nplaylists.$.popNewPlaylist.close).not.toHaveBeenCalled();
			});
			
			it('shouldnt call doCreatePlaylist',function(){
				spyOn(nplaylists,'doCreatePlaylist');
				nplaylists.onclick_Create(null,{"rowIndex":0,"dispatchTarget":null});
				expect(nplaylists.doCreatePlaylist).not.toHaveBeenCalled();
			});
		});
		describe("when txtPlaylistName is not empty", function(){
			beforeEach(function(){
				nplaylists.$.txtPlaylistName.getValue = function(){return  "asdf"};
			});
			it("shoudl call Utilities.fastTrim", function(){
				spyOn(Utilities,"fastTrim");
				nplaylists.onclick_Create(null,{"rowIndex":0,"dispatchTarget":null});
				expect(Utilities.fastTrim).toHaveBeenCalledWith("asdf");
			});
			it("should setvalue of txtPlaylistName", function(){
				spyOn(nplaylists.$.txtPlaylistName, "setValue");
				nplaylists.onclick_Create(null,{"rowIndex":0,"dispatchTarget":null});
				expect(nplaylists.$.txtPlaylistName.setValue).toHaveBeenCalledWith("");
			});
			it("should close popup", function(){
				spyOn(nplaylists.$.popNewPlaylist, "close");
				nplaylists.onclick_Create(null,{"rowIndex":0,"dispatchTarget":null});
				expect(nplaylists.$.popNewPlaylist.close).toHaveBeenCalled();
			});
					
			
			it('should call doCreatePlaylist',function(){
				spyOn(nplaylists,'doCreatePlaylist');
				nplaylists.onclick_Create(null,{"rowIndex":0,"dispatchTarget":null});
				expect(nplaylists.doCreatePlaylist).toHaveBeenCalled();
			});
		});		
		
		
		
		
		
	});
	describe('test (onSuccess_GoToPlaylist)', function(){
		it('should set _strNewPlaylistID',function(){
			nplaylists._strNewPlaylistID = false;
			nplaylists.onSuccess_GoToPlaylist(null,5);
			expect(nplaylists._strNewPlaylistID).toBe(5);
		});
		it('should call renderList',function(){
			spyOn(nplaylists,'renderList');
			nplaylists.onSuccess_GoToPlaylist(null,5);
			expect(nplaylists.renderList).toHaveBeenCalled();
		});
		
		//should stay in current view to enable 
		//		it('should call goToPlaylist',function(){
//			spyOn(nplaylists,'goToPlaylist');
//			nplaylists.onSuccess_GoToPlaylist(null,{"rowIndex":0,"dispatchTarget":null});
//			expect(nplaylists.goToPlaylist).toHaveBeenCalledWith({"strPlaylistName":null,"strPlaylistID":{"rowIndex":0,"dispatchTarget":null},"strPlaylistKind":"com.palm.music.staticplaylists:1"},true);
//		});
	});
	describe('test (onclick_Cancel)', function(){
		it("should setvalue of txtPlaylistName", function(){
			spyOn(nplaylists.$.txtPlaylistName, "setValue");
			nplaylists.onclick_Cancel(null,{"rowIndex":0,"dispatchTarget":null});
			expect(nplaylists.$.txtPlaylistName.setValue).toHaveBeenCalledWith("");
		});
		it("should close popup", function(){
			spyOn(nplaylists.$.popNewPlaylist, "close");
			nplaylists.onclick_Cancel(null,{"rowIndex":0,"dispatchTarget":null});
			expect(nplaylists.$.popNewPlaylist.close).toHaveBeenCalled();
		});
	});
	describe('test (onclick_btnDelete)', function(){
		describe("with proper index", function(){
			it("should clearSlecection()", function(){
				spyOn(nplaylists, "clearSelection");
				nplaylists.onclick_btnDelete(null,1);
				expect(nplaylists.clearSelection).toHaveBeenCalled();
			});
			it("should clearSlecection()", function(){
				spyOn(nplaylists, "doDeletePlaylist");
				nplaylists.onclick_btnDelete(null,1);
				expect(nplaylists.doDeletePlaylist).toHaveBeenCalled();
			});
		});
		describe("with improper index", function(){
			it("shouldnt clearSlecection()", function(){
				spyOn(nplaylists, "clearSelection");
				nplaylists.onclick_btnDelete(null,-1);
				expect(nplaylists.clearSelection).not.toHaveBeenCalled();
			});
			it("shouldnt clearSlecection()", function(){
				spyOn(nplaylists, "doDeletePlaylist");
				nplaylists.onclick_btnDelete(null,-1);
				expect(nplaylists.doDeletePlaylist).not.toHaveBeenCalled();
			});
		});
		
		
		
	});
	describe('test (dragOver)', function(){
		beforeEach(function(){
			nplaylists.intJumpRowOffset = 1
		});
		describe('testing (dragOver) with params [null,[[null,{"rowIndex":0,"dragInfo":{}}]]]', function(){
			it("should call addclass editing", function(){
				var inSender = {addClass : function(){}};
				nplaylists.arPlaylists[2]._kind = "asdf";
				spyOn(inSender, "addClass");
				nplaylists.dragOver(inSender, {rowIndex: 2, dragInfo: {}})
				expect(inSender.addClass).toHaveBeenCalledWith('editing');
			});
			it("should set 	event.dragInfo.boolShowIcon", function(){
				nplaylists.arPlaylists[2]._kind = "coasdf";
				var inSender = {addClass : function(){}};
				var event = {rowIndex: 2, dragInfo: {boolShowIcon: false}};
				spyOn(inSender, "addClass");
				nplaylists.dragOver(inSender, event)
				expect(event.dragInfo.boolShowIcon).toBe(true);
			});
			it("should call addclass editing wrong kind", function(){
				var inSender = {addClass : function(){}};
				nplaylists.arPlaylists[2]._kind = "com.palm.media.playlist.file:1";
				spyOn(inSender, "addClass");
				nplaylists.dragOver(inSender, {rowIndex: 2, dragInfo: {}})
				expect(inSender.addClass).not.toHaveBeenCalled();
			});
			it("should set 	event.dragInfo.boolShowIcon", function(){
				nplaylists.arPlaylists[2]._kind = "com.palm.media.playlist.file:1";
				var inSender = {addClass : function(){}};
				var event = {rowIndex: 2, dragInfo: {boolShowIcon: true}};
				spyOn(inSender, "addClass");
				nplaylists.dragOver(inSender, event)
				expect(event.dragInfo.boolShowIcon).toBe(false);
			});
			it("when drag info isn't there it shouldn't do anything", function(){
				nplaylists.arPlaylists[2]._kind = "com.palm.media.playlist.file:1";
				var inSender = {addClass : function(){}};
				var event = {rowIndex: 1};
				spyOn(inSender, "addClass");
				nplaylists.dragOver(inSender, event)
				expect(inSender.addClass).not.toHaveBeenCalled();
			});
			
		
		});
	});
	describe('test (dragOut)', function(){
		describe('testing (dragOut) with params [null,[[null,{"rowIndex":0,"dragInfo":{}}]]]', function(){
			it("should removeclass", function(){
				var inSender = {removeClass : function(){}};
				var event = {rowIndex: 1, dragInfo: {boolShowIcon: true}};
				spyOn(inSender, "removeClass");
				nplaylists.dragOut(inSender, event)
				expect(inSender.removeClass).toHaveBeenCalled();
			});
			it("should event.dragInfo.boolShowIcon", function(){
				var inSender = {removeClass : function(){}};
				var event = {rowIndex: 1, dragInfo: {boolShowIcon: true}};
				spyOn(inSender, "removeClass");
				nplaylists.dragOut(inSender, event)
				expect(event.dragInfo.boolShowIcon).toBe(false);
			});
			
			it("should removeclass", function(){
				var inSender = {removeClass : function(){}};
				var event = {rowIndex: 1};
				spyOn(inSender, "removeClass");
				nplaylists.dragOut(inSender, event)
				expect(inSender.removeClass).not.toHaveBeenCalled();
			});
			
		
		
		
		});
	});
	describe('test (dragDrop)', function(){
		describe('testing (dragDrop) with params [null,[[null,{"rowIndex":0,"dragInfo":{}}]]]', function(){
			beforeEach(function(){
				nplaylists.intJumpRowOffset = 1;
			});
			it("should removeclass", function(){
				var inSender = {removeClass : function(){}};
				var event = {rowIndex: 1, dragInfo: {boolShowIcon: true}};
				spyOn(inSender, "removeClass");
				nplaylists.dragDrop(inSender, event)
				expect(inSender.removeClass).toHaveBeenCalled();
			});
			
			it("should set strPlaylistName", function(){
				var inSender = {removeClass : function(){}};
				nplaylists.arPlaylists[2].name = "fff";
				nplaylists.arPlaylists[2]._kind = "asdf";
				var event = {rowIndex: 2, dragInfo: {boolShowIcon: true, strPlaylistName: "asdf"}};
				spyOn(inSender, "removeClass");
				nplaylists.dragDrop(inSender, event)
				expect(event.dragInfo.strPlaylistName).toBe(nplaylists.arPlaylists[2].name);
			});
			
			it("should set strPlaylistID", function(){
				var inSender = {removeClass : function(){}};
				nplaylists.arPlaylists[2]._id = "bbb";
				nplaylists.arPlaylists[2]._kind = "asdf";
				var event = {rowIndex: 2, dragInfo: {boolShowIcon: true, strPlaylistID: "asdf"}};
				spyOn(inSender, "removeClass");
				nplaylists.dragDrop(inSender, event)
				expect(event.dragInfo.strPlaylistID).toBe(nplaylists.arPlaylists[2]._id);
			});
			
			
			it("shouldnt set strPlaylistName", function(){
				var inSender = {removeClass : function(){}};
				nplaylists.arPlaylists[2].name = "fff";
				nplaylists.arPlaylists[2]._kind = "com.palm.media.playlist.file:1";
				var event = {rowIndex: 2, dragInfo: {boolShowIcon: true, strPlaylistName: "asdf"}};
				spyOn(inSender, "removeClass");
				nplaylists.dragDrop(inSender, event)
				expect(event.dragInfo.strPlaylistName).not.toBe(nplaylists.arPlaylists[2].name);
			});
			
			it("shouldnt set strPlaylistID", function(){
				var inSender = {removeClass : function(){}};
				nplaylists.arPlaylists[2]._id = "bbb";
				nplaylists.arPlaylists[2]._kind = "com.palm.media.playlist.file:1";
				var event = {rowIndex: 2, dragInfo: {boolShowIcon: true, strPlaylistID: "asdf"}};
				spyOn(inSender, "removeClass");
				nplaylists.dragDrop(inSender, event)
				expect(event.dragInfo.strPlaylistID).not.toBe(nplaylists.arPlaylists[2]._id);
			});
			
			
			it("shouldnt removeclass", function(){
				var inSender = {removeClass : function(){}};
				var event = {rowIndex: 2};
				spyOn(inSender, "removeClass");
				nplaylists.dragDrop(inSender, event)
				expect(inSender.removeClass).not.toHaveBeenCalled();
			});
		
		});
	});
	describe('test (resize)', function(){
		it('should call resized',function(){
			spyOn(nplaylists,'resized');
			nplaylists.resize();
			expect(nplaylists.resized).toHaveBeenCalled();
		});
		it('should call listPlaylists',function(){
			spyOn(nplaylists.$.listPlaylists,'applyStyle');
			nplaylists.resize("12");
			expect(nplaylists.$.listPlaylists.applyStyle).toHaveBeenCalledWith("height", "12px");
		});
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(nplaylists,'_constructor');
			nplaylists.base();
			expect(nplaylists._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(nplaylists,'constructed');
			nplaylists.base();
			expect(nplaylists.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(nplaylists,'_constructor');
			nplaylists.ctor();
			expect(nplaylists._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(nplaylists,'constructed');
			nplaylists.ctor();
			expect(nplaylists.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doRequestPlaylists)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(nplaylists,'dispatchIndirectly');
			nplaylists.doRequestPlaylists();
			expect(nplaylists.dispatchIndirectly).toHaveBeenCalledWith("onRequestPlaylists",{});
		});
	});
	describe('test (doClickPlaylist)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(nplaylists,'dispatchIndirectly');
			nplaylists.doClickPlaylist();
			expect(nplaylists.dispatchIndirectly).toHaveBeenCalledWith("onClickPlaylist",{});
		});
	});
	describe('test (doCreatePlaylist)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(nplaylists,'dispatchIndirectly');
			nplaylists.doCreatePlaylist();
			expect(nplaylists.dispatchIndirectly).toHaveBeenCalledWith("onCreatePlaylist",{});
		});
	});
	describe('test (doDeletePlaylist)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(nplaylists,'dispatchIndirectly');
			nplaylists.doDeletePlaylist();
			expect(nplaylists.dispatchIndirectly).toHaveBeenCalledWith("onDeletePlaylist",{});
		});
	});
	describe('test (getStrEditPlaylistID)', function(){
		it('should call getProperty',function(){
			spyOn(nplaylists,'getProperty');
			nplaylists.getStrEditPlaylistID();
			expect(nplaylists.getProperty).toHaveBeenCalledWith("strEditPlaylistID");
		});
	});
	describe('test (setStrEditPlaylistID)', function(){
	});
	describe('test (getStrSelectedPlaylistID)', function(){
		it('should call getProperty',function(){
			spyOn(nplaylists,'getProperty');
			nplaylists.getStrSelectedPlaylistID();
			expect(nplaylists.getProperty).toHaveBeenCalledWith("strSelectedPlaylistID");
		});
	});
	describe('test (setStrSelectedPlaylistID)', function(){
	});
	describe('test (destroy)', function(){
		
		it('should call inherited',function(){
			spyOn(nplaylists,'inherited');
			nplaylists.destroy();
			expect(nplaylists.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(nplaylists,'inherited');
			nplaylists.importProps();
			expect(nplaylists.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
		it('should call createChrome',function(){
			spyOn(nplaylists,'createChrome');
			nplaylists.initComponents();
			expect(nplaylists.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(nplaylists,'inherited');
			nplaylists.initComponents();
			expect(nplaylists.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(nplaylists,'inherited');
			nplaylists.createComponents();
			expect(nplaylists.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(nplaylists,'discoverControlParent');
			nplaylists.createComponents();
			expect(nplaylists.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(nplaylists,'createComponents');
			nplaylists.createChrome();
			expect(nplaylists.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});