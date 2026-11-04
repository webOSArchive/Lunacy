/*globals specOut, enyo, $L, event, Utilities, window, LibraryTools */

//var creator = function(){
//				return enyo.create({kind: "ctrlListViewHeader"});
//			};
//var objName = "ctrlListViewHeader";
//
//var before = function (obj, callbacks) {
//	
//	   
//};
//var event = [[null, {rowIndex:0, dispatchTarget: null}]];
//var params = {
//		
//};
//
//specOut(creator, objName, before, params);

describe('ctrlListViewHeader tests', function(){
	var creator = function (){
				return enyo.create({kind: "ctrlListViewHeader"});
			};
	var ctrlListViewHeader 
	beforeEach(function(){
		ctrlListViewHeader = creator();
		var before = function (obj, callbacks) {
	
	   
};
		var callback = function(one, fun){return fun;};
		before(ctrlListViewHeader,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof ctrlListViewHeader.controls).toBe('object');
		expect(typeof ctrlListViewHeader.children).toBe('object');
		//expect(ctrlListViewHeader._componentNameMap).toEqual({"control":2});
		expect(typeof ctrlListViewHeader.$).toBe('object');
		//expect(ctrlListViewHeader.domStyles).toEqual({"height":"54px"});
		expect(typeof ctrlListViewHeader.owner).toBe('object');
		expect(typeof ctrlListViewHeader.controlParent).toBe('undefined');
		expect(typeof ctrlListViewHeader.layout).toBe('object');
		expect(ctrlListViewHeader.pack).toEqual("justify");
		expect(ctrlListViewHeader.layoutKind).toEqual("HFlexLayout");
		//expect(ctrlListViewHeader.height).toEqual("54px");
		expect(ctrlListViewHeader.boolShowSearch).toEqual(false);
		expect(ctrlListViewHeader.onSearch_Header).toEqual("");
		expect(ctrlListViewHeader.onEditMode).toEqual("");
		expect(ctrlListViewHeader.onSetMultiSelect).toEqual("");
		expect(ctrlListViewHeader.strHeaderTitle).toEqual("Songs");
		expect(ctrlListViewHeader.intHeaderCount).toEqual(-1);
		expect(ctrlListViewHeader.boolShowDragHandle).toEqual(false);
		expect(ctrlListViewHeader.boolShowEditPlaylist).toEqual(false);
		expect(ctrlListViewHeader.boolShowMultiSelect).toEqual(false);
		expect(ctrlListViewHeader.boolShowSearchBtn).toEqual(true);
		expect(ctrlListViewHeader.align).toEqual("center");
		expect(ctrlListViewHeader.defaultKind).toEqual("ToolButton");
		expect(ctrlListViewHeader.controlParentName).toEqual("client");
		expect(ctrlListViewHeader.onclick).toEqual("");
		expect(ctrlListViewHeader.onmousedown).toEqual("");
		expect(ctrlListViewHeader.onmouseup).toEqual("");
		expect(ctrlListViewHeader.style).toEqual("");
		expect(ctrlListViewHeader.container).toEqual(null);
		expect(ctrlListViewHeader.parent).toEqual(null);
		expect(ctrlListViewHeader.generated).toEqual(false);
		expect(ctrlListViewHeader.allowHtml).toEqual(false);
		expect(ctrlListViewHeader.content).toEqual("");
		expect(ctrlListViewHeader.nodeTag).toEqual("div");
		expect(ctrlListViewHeader.node).toEqual(null);
		expect(ctrlListViewHeader.showing).toEqual(true);
		expect(ctrlListViewHeader.prepend).toEqual(false);
		expect(ctrlListViewHeader.wantsEvents).toEqual(true);
	});
	describe('test (drawHeader)', function(){
	});
	describe('test (strHeaderTitleChanged)', function(){
		it('should call drawHeader',function(){
			spyOn(ctrlListViewHeader,'drawHeader');
			ctrlListViewHeader.strHeaderTitleChanged();
			expect(ctrlListViewHeader.drawHeader).toHaveBeenCalled();
		});
	});
	describe('test (intHeaderCountChanged)', function(){
		it('should call drawHeader',function(){
			spyOn(ctrlListViewHeader,'drawHeader');
			ctrlListViewHeader.intHeaderCountChanged();
			expect(ctrlListViewHeader.drawHeader).toHaveBeenCalled();
		});
	});
	describe('test (boolShowDragHandleChanged)', function(){
		it('should call drawHeader',function(){
			spyOn(ctrlListViewHeader,'drawHeader');
			ctrlListViewHeader.boolShowDragHandleChanged();
			expect(ctrlListViewHeader.drawHeader).toHaveBeenCalled();
		});
	});
	describe('test (boolShowEditPlaylistChanged)', function(){
		it('should call drawHeader',function(){
			spyOn(ctrlListViewHeader,'drawHeader');
			ctrlListViewHeader.boolShowEditPlaylistChanged();
			expect(ctrlListViewHeader.drawHeader).toHaveBeenCalled();
		});
	});
	describe('test (boolShowMultiSelectChanged)', function(){
		it('should call drawHeader',function(){
			spyOn(ctrlListViewHeader,'drawHeader');
			ctrlListViewHeader.boolShowMultiSelectChanged();
			expect(ctrlListViewHeader.drawHeader).toHaveBeenCalled();
		});
	});
	describe('test (boolShowSearchBtnChanged)', function(){
		it('should call drawHeader',function(){
			spyOn(ctrlListViewHeader,'drawHeader');
			ctrlListViewHeader.boolShowSearchBtnChanged();
			expect(ctrlListViewHeader.drawHeader).toHaveBeenCalled();
		});
	});
	describe('test (clearSearchInput)', function(){
		beforeEach(function(){
			ctrlListViewHeader.$.SearchInput = {};
			ctrlListViewHeader.$.SearchInput.clearSearchInput = function(){};
			ctrlListViewHeader.onClose_SearchInput = function(){};
		});
		
		it('should call clearSearchInput true',function(){
			spyOn(ctrlListViewHeader.$.SearchInput,'clearSearchInput');
			ctrlListViewHeader.clearSearchInput(true);
			expect(ctrlListViewHeader.$.SearchInput.clearSearchInput).toHaveBeenCalledWith(true);
		});
		it('should call clearSearchInput false',function(){
			spyOn(ctrlListViewHeader.$.SearchInput,'clearSearchInput');
			ctrlListViewHeader.clearSearchInput(false);
			expect(ctrlListViewHeader.$.SearchInput.clearSearchInput).toHaveBeenCalledWith(false);
		});
		it('should call onClose_SearchInput',function(){
			spyOn(ctrlListViewHeader,'onClose_SearchInput');
			ctrlListViewHeader.clearSearchInput();
			expect(ctrlListViewHeader.onClose_SearchInput).toHaveBeenCalled();
		});
	});
	describe('test (onClick_btnGetSearch)', function(){
		beforeEach(function(){
			ctrlListViewHeader.$.SearchInput = {};
			ctrlListViewHeader.$.buttonContainer = {};
			ctrlListViewHeader.$.SearchInput.show = function(){};
			ctrlListViewHeader.$.buttonContainer.hide = function(){};
			ctrlListViewHeader.$.SearchInput.focusSearch = function(){};
		});
		it('should call ctrlListViewHeader.$.SearchInput.show',function(){
			spyOn(ctrlListViewHeader.$.SearchInput,'show');
			ctrlListViewHeader.onClick_btnGetSearch();
			expect(ctrlListViewHeader.$.SearchInput.show).toHaveBeenCalled();
		});
		it('should call ctrlListViewHeader.$.buttonContainer.hide',function(){
			spyOn(ctrlListViewHeader.$.buttonContainer,'hide');
			ctrlListViewHeader.onClick_btnGetSearch();
			expect(ctrlListViewHeader.$.buttonContainer.hide).toHaveBeenCalled();
		});
		it('should call ctrlListViewHeader.$.SearchInput.focusSearch',function(){
			spyOn(ctrlListViewHeader.$.SearchInput,'focusSearch');
			ctrlListViewHeader.onClick_btnGetSearch();
			expect(ctrlListViewHeader.$.SearchInput.focusSearch).toHaveBeenCalled();
		});
		it('should change boolShowSearch',function(){
			ctrlListViewHeader.onClick_btnGetSearch(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ctrlListViewHeader.boolShowSearch).toEqual(true);
		});
	});
	describe('test (onClose_SearchInput)', function(){
		it('should change boolShowSearch',function(){
			ctrlListViewHeader.onClose_SearchInput(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ctrlListViewHeader.boolShowSearch).toEqual(false);
		});
	});
	describe('test (onClick_btnEditPlaylist)', function(){
		it('should call doEditMode',function(){
			spyOn(ctrlListViewHeader,'doEditMode');
			ctrlListViewHeader.onClick_btnEditPlaylist(null,{"rowIndex":0,"dispatchTarget":null});
			expect(ctrlListViewHeader.doEditMode).toHaveBeenCalled();
		});
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(ctrlListViewHeader,'_constructor');
			ctrlListViewHeader.base();
			expect(ctrlListViewHeader._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(ctrlListViewHeader,'constructed');
			ctrlListViewHeader.base();
			expect(ctrlListViewHeader.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(ctrlListViewHeader,'_constructor');
			ctrlListViewHeader.ctor();
			expect(ctrlListViewHeader._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(ctrlListViewHeader,'constructed');
			ctrlListViewHeader.ctor();
			expect(ctrlListViewHeader.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doSearch_Header)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ctrlListViewHeader,'dispatchIndirectly');
			ctrlListViewHeader.doSearch_Header();
			expect(ctrlListViewHeader.dispatchIndirectly).toHaveBeenCalledWith("onSearch_Header",{});
		});
	});
	describe('test (doEditMode)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ctrlListViewHeader,'dispatchIndirectly');
			ctrlListViewHeader.doEditMode();
			expect(ctrlListViewHeader.dispatchIndirectly).toHaveBeenCalledWith("onEditMode",{});
		});
	});
	describe('test (doSetMultiSelect)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(ctrlListViewHeader,'dispatchIndirectly');
			ctrlListViewHeader.doSetMultiSelect();
			expect(ctrlListViewHeader.dispatchIndirectly).toHaveBeenCalledWith("onSetMultiSelect",{});
		});
	});
	describe('test (getStrHeaderTitle)', function(){
		it('should call getProperty',function(){
			spyOn(ctrlListViewHeader,'getProperty');
			ctrlListViewHeader.getStrHeaderTitle();
			expect(ctrlListViewHeader.getProperty).toHaveBeenCalledWith("strHeaderTitle");
		});
	});
	describe('test (setStrHeaderTitle)', function(){
	});
	describe('test (getIntHeaderCount)', function(){
		it('should call getProperty',function(){
			spyOn(ctrlListViewHeader,'getProperty');
			ctrlListViewHeader.getIntHeaderCount();
			expect(ctrlListViewHeader.getProperty).toHaveBeenCalledWith("intHeaderCount");
		});
	});
	describe('test (setIntHeaderCount)', function(){
		it('should call _setProperty',function(){
			spyOn(ctrlListViewHeader,'_setProperty');
			ctrlListViewHeader.setIntHeaderCount();
			expect(ctrlListViewHeader._setProperty).toHaveBeenCalledWith("intHeaderCount",undefined,"intHeaderCountChanged");
		});
	});
	describe('test (getBoolShowDragHandle)', function(){
		it('should call getProperty',function(){
			spyOn(ctrlListViewHeader,'getProperty');
			ctrlListViewHeader.getBoolShowDragHandle();
			expect(ctrlListViewHeader.getProperty).toHaveBeenCalledWith("boolShowDragHandle");
		});
	});
	describe('test (setBoolShowDragHandle)', function(){
		it('should call _setProperty',function(){
			spyOn(ctrlListViewHeader,'_setProperty');
			ctrlListViewHeader.setBoolShowDragHandle();
			expect(ctrlListViewHeader._setProperty).toHaveBeenCalledWith("boolShowDragHandle",undefined,"boolShowDragHandleChanged");
		});
	});
	describe('test (getBoolShowEditPlaylist)', function(){
		it('should call getProperty',function(){
			spyOn(ctrlListViewHeader,'getProperty');
			ctrlListViewHeader.getBoolShowEditPlaylist();
			expect(ctrlListViewHeader.getProperty).toHaveBeenCalledWith("boolShowEditPlaylist");
		});
	});
	describe('test (setBoolShowEditPlaylist)', function(){
		it('should call _setProperty',function(){
			spyOn(ctrlListViewHeader,'_setProperty');
			ctrlListViewHeader.setBoolShowEditPlaylist();
			expect(ctrlListViewHeader._setProperty).toHaveBeenCalledWith("boolShowEditPlaylist",undefined,"boolShowEditPlaylistChanged");
		});
	});
	describe('test (getBoolShowMultiSelect)', function(){
		it('should call getProperty',function(){
			spyOn(ctrlListViewHeader,'getProperty');
			ctrlListViewHeader.getBoolShowMultiSelect();
			expect(ctrlListViewHeader.getProperty).toHaveBeenCalledWith("boolShowMultiSelect");
		});
	});
	describe('test (setBoolShowMultiSelect)', function(){
		it('should call _setProperty',function(){
			spyOn(ctrlListViewHeader,'_setProperty');
			ctrlListViewHeader.setBoolShowMultiSelect();
			expect(ctrlListViewHeader._setProperty).toHaveBeenCalledWith("boolShowMultiSelect",undefined,"boolShowMultiSelectChanged");
		});
	});
	describe('test (getBoolShowSearchBtn)', function(){
		it('should call getProperty',function(){
			spyOn(ctrlListViewHeader,'getProperty');
			ctrlListViewHeader.getBoolShowSearchBtn();
			expect(ctrlListViewHeader.getProperty).toHaveBeenCalledWith("boolShowSearchBtn");
		});
	});
	describe('test (setBoolShowSearchBtn)', function(){
		it('should call _setProperty',function(){
			spyOn(ctrlListViewHeader,'_setProperty');
			ctrlListViewHeader.setBoolShowSearchBtn();
			expect(ctrlListViewHeader._setProperty).toHaveBeenCalledWith("boolShowSearchBtn",undefined,"boolShowSearchBtnChanged");
		});
	});
	
	describe('test (fadeIn)', function(){
		it('should call addClass',function(){
			spyOn(ctrlListViewHeader,'addClass');
			ctrlListViewHeader.fadeIn();
			expect(ctrlListViewHeader.addClass).toHaveBeenCalledWith("enyo-toolbar-snap-out");
		});
	});
	describe('test (_fadeIn)', function(){
		it('should call addClass',function(){
			spyOn(ctrlListViewHeader,'addClass');
			ctrlListViewHeader._fadeIn();
			expect(ctrlListViewHeader.addClass).toHaveBeenCalledWith("enyo-toolbar-fade-in");
		});
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(ctrlListViewHeader,'inherited');
			ctrlListViewHeader.create();
			expect(ctrlListViewHeader.inherited).toHaveBeenCalledWith({});
		});
		it('should call registerEvents',function(){
			spyOn(ctrlListViewHeader,'registerEvents');
			ctrlListViewHeader.create();
			expect(ctrlListViewHeader.registerEvents).toHaveBeenCalled();
		});
		it('should call layoutKindChanged',function(){
			spyOn(ctrlListViewHeader,'layoutKindChanged');
			ctrlListViewHeader.create();
			expect(ctrlListViewHeader.layoutKindChanged).toHaveBeenCalled();
		});
	});
	describe('test (destroy)', function(){
		it('should call unregisterEvents',function(){
			spyOn(ctrlListViewHeader,'unregisterEvents');
			ctrlListViewHeader.destroy();
			expect(ctrlListViewHeader.unregisterEvents).toHaveBeenCalled();
		});
		it('should call destroyControls',function(){
			spyOn(ctrlListViewHeader,'destroyControls');
			ctrlListViewHeader.destroy();
			expect(ctrlListViewHeader.destroyControls).toHaveBeenCalled();
		});
		it('should call inherited',function(){
			spyOn(ctrlListViewHeader,'inherited');
			ctrlListViewHeader.destroy();
			expect(ctrlListViewHeader.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(ctrlListViewHeader,'inherited');
			ctrlListViewHeader.importProps();
			expect(ctrlListViewHeader.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(ctrlListViewHeader,'inherited');
			ctrlListViewHeader.createComponents();
			expect(ctrlListViewHeader.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(ctrlListViewHeader,'discoverControlParent');
			ctrlListViewHeader.createComponents();
			expect(ctrlListViewHeader.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(ctrlListViewHeader,'createComponents');
			ctrlListViewHeader.createChrome();
			expect(ctrlListViewHeader.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});