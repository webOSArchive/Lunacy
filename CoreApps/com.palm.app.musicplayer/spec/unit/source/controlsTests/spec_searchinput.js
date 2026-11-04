/*globals specOut, enyo, $L, event, Utilities, window, LibraryTools */


//var creator = function(){
//				return enyo.create({kind: "SearchControl"});
//			};
//var objName = "SearchControl";
//
//var before = function (obj, callbacks) {
//	
//        
//};
//var params = {
//		 
//};
//
//specOut(creator, objName, before, params);


describe('SearchControl tests', function(){
	var creator = function (){
				return enyo.create({kind: "SearchControl"});
			};
	var SearchControl;
	beforeEach(function(){
		SearchControl = creator();
		var before = function (obj, callbacks) {
	
        
};
		var callback = function(one, fun){return fun;};
		before(SearchControl,callback);
	});
	it('should correctly set up object', function(){
		expect(typeof SearchControl.controls).toBe('object');
		expect(typeof SearchControl.children).toBe('object');
		expect(SearchControl._componentNameMap).toEqual({});
		expect(typeof SearchControl.$).toBe('object');
		expect(SearchControl.domStyles).toEqual({});
		expect(SearchControl.domAttributes).toEqual({"className":"search","id":"searchControl"});
		expect(SearchControl.kind).toEqual("SearchControl");
		expect(typeof SearchControl.owner).toBe('object');
		expect(SearchControl.name).toEqual("searchControl");
		expect(SearchControl.id).toEqual("searchControl");
		expect(SearchControl.controlParent).toEqual(undefined);
		expect(SearchControl.className).toEqual("search");
		expect(SearchControl.published).toEqual({"TypeDownSearch":false});
		expect(SearchControl.events).toEqual({"onSearch":"","onClose":""});
		expect(SearchControl.boolClearSearch).toEqual(false);
		expect(SearchControl.kindName).toEqual("SearchControl");
		expect(SearchControl.kindComponents).toEqual([{"align":"center","name":"txtSearch","kind":"RoundedInput","hint":"","autocorrect":false,"spellcheck":false,"changeOnInput":true,"disabled":false,"keypressInputDelay":0.5,"onchange":"onChangeSearch","components":[{"name":"imgSearchClose","kind":"Control","className":"close","onclick":"onClickClose"}]}]);
		expect(SearchControl.onSearch).toEqual("");
		expect(SearchControl.onClose).toEqual("");
		expect(SearchControl.TypeDownSearch).toEqual(false);
		expect(SearchControl.controlParentName).toEqual("client");
		expect(SearchControl.defaultKind).toEqual("Control");
		expect(SearchControl.onclick).toEqual("");
		expect(SearchControl.onmousedown).toEqual("");
		expect(SearchControl.onmouseup).toEqual("");
		expect(SearchControl.layoutKind).toEqual("");
		expect(SearchControl.style).toEqual("");
		expect(SearchControl.container).toEqual(null);
		expect(SearchControl.parent).toEqual(null);
		expect(SearchControl.generated).toEqual(false);
		expect(SearchControl.allowHtml).toEqual(false);
		expect(SearchControl.content).toEqual("");
		expect(SearchControl.nodeTag).toEqual("div");
		expect(SearchControl.node).toEqual(null);
		expect(SearchControl.showing).toEqual(true);
		expect(SearchControl.prepend).toEqual(false);
		expect(SearchControl.wantsEvents).toEqual(true);
	});
	describe('test (clearSearchInput)', function(){
		it('should call doSearch',function(){
			spyOn(SearchControl,'doSearch');
			SearchControl.clearSearchInput();
			expect(SearchControl.doSearch).toHaveBeenCalledWith("");
		});
		it('should call doSearch not',function(){
			spyOn(SearchControl,'doSearch');
			SearchControl.clearSearchInput(false);
			expect(SearchControl.doSearch).not.toHaveBeenCalledWith("");
		});
		it('should call SearchControl.$.txtSearch.setValue', function(){
			spyOn(SearchControl.$.txtSearch,'setValue');
			SearchControl.clearSearchInput();
			expect(SearchControl.$.txtSearch.setValue).toHaveBeenCalledWith("");
		});
		it('should call SearchControl.$.txtSearch.setValue still', function(){
			spyOn(SearchControl.$.txtSearch,'setValue');
			SearchControl.clearSearchInput(false);
			expect(SearchControl.$.txtSearch.setValue).toHaveBeenCalledWith("");
		});
	});
	describe('test (focusSearch)', function(){
		
		it('should call SearchControl.$.txtSearch.setDisabled', function(){
			spyOn(SearchControl.$.txtSearch,'setDisabled');
			SearchControl.focusSearch();
			expect(SearchControl.$.txtSearch.setDisabled).toHaveBeenCalledWith(false);
		});
		it('should call SearchControl.$.txtSearch.forceFocus', function(){
			spyOn(SearchControl.$.txtSearch,'forceFocus');
			SearchControl.focusSearch();
			expect(SearchControl.$.txtSearch.forceFocus).toHaveBeenCalledWith();
		});
		
		
		
	});
	describe('test (runSearch)', function(){
		it('should call doSearch',function(){
			spyOn(SearchControl,'doSearch');
			SearchControl.runSearch();
			expect(SearchControl.doSearch).toHaveBeenCalled();
		});
	});
	describe('test (onClickSearch)', function(){
	});
	describe('test (onClickClose)', function(){
		it('should call doClose',function(){
			spyOn(SearchControl,'doClose');
			SearchControl.onClickClose(null,{"rowIndex":0,"dispatchTarget":null});
			expect(SearchControl.doClose).toHaveBeenCalled();
		});
		it('should call clearSearchInput',function(){
			spyOn(SearchControl,'clearSearchInput');
			SearchControl.onClickClose(null,{"rowIndex":0,"dispatchTarget":null});
			expect(SearchControl.clearSearchInput).toHaveBeenCalledWith(true);
		});
		it('should call doSearch',function(){
			spyOn(SearchControl,'doSearch');
			SearchControl.onClickClose(null,{"rowIndex":0,"dispatchTarget":null});
			expect(SearchControl.doSearch).toHaveBeenCalledWith("");
		});
		
		it('should call SearchControl.$.txtSearch.forceBlur', function(){
			spyOn(SearchControl.$.txtSearch,'forceBlur');
			SearchControl.onClickClose();
			expect(SearchControl.$.txtSearch.forceBlur).toHaveBeenCalled();
		});
		it('should call SearchControl.$.txtSearch.setDisabled', function(){
			spyOn(SearchControl.$.txtSearch,'setDisabled');
			SearchControl.onClickClose();
			expect(SearchControl.$.txtSearch.setDisabled).toHaveBeenCalledWith(true);
		});
		it('should call SearchControl.$.txtSearch.setValue', function(){
			spyOn(SearchControl.$.txtSearch,'setValue');
			SearchControl.onClickClose();
			expect(SearchControl.$.txtSearch.setValue).toHaveBeenCalledWith("");
		});
		
		
		
	});
	describe('test (onChangeSearch)', function(){
		it('should call runSearch if typedownsearch', function(){
			SearchControl.TypeDownSearch = true;
			spyOn(SearchControl,'runSearch')
			SearchControl.onChangeSearch();
			expect(SearchControl.runSearch).toHaveBeenCalled();
		});
		it('should call runSearch if typedownsearch not', function(){
			SearchControl.TypeDownSearch = false;
			spyOn(SearchControl,'runSearch')
			SearchControl.onChangeSearch();
			expect(SearchControl.runSearch).not.toHaveBeenCalled();
		});	
	
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(SearchControl,'_constructor');
			SearchControl.base();
			expect(SearchControl._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(SearchControl,'constructed');
			SearchControl.base();
			expect(SearchControl.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(SearchControl,'_constructor');
			SearchControl.ctor();
			expect(SearchControl._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(SearchControl,'constructed');
			SearchControl.ctor();
			expect(SearchControl.constructed).toHaveBeenCalled();
		});
	});
	describe('test (doSearch)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(SearchControl,'dispatchIndirectly');
			SearchControl.doSearch();
			expect(SearchControl.dispatchIndirectly).toHaveBeenCalledWith("onSearch",{});
		});
	});
	describe('test (doClose)', function(){
		it('should call dispatchIndirectly',function(){
			spyOn(SearchControl,'dispatchIndirectly');
			SearchControl.doClose();
			expect(SearchControl.dispatchIndirectly).toHaveBeenCalledWith("onClose",{});
		});
	});
	describe('test (getTypeDownSearch)', function(){
		it('should call getProperty',function(){
			spyOn(SearchControl,'getProperty');
			SearchControl.getTypeDownSearch();
			expect(SearchControl.getProperty).toHaveBeenCalledWith("TypeDownSearch");
		});
	});
	describe('test (setTypeDownSearch)', function(){
		it('should call _setProperty',function(){
			spyOn(SearchControl,'_setProperty');
			SearchControl.setTypeDownSearch();
			expect(SearchControl._setProperty).toHaveBeenCalledWith("TypeDownSearch",undefined,"TypeDownSearchChanged");
		});
	});
	describe('test (create)', function(){
		it('should call inherited',function(){
			spyOn(SearchControl,'inherited');
			SearchControl.create();
			expect(SearchControl.inherited).toHaveBeenCalledWith({});
		});
		it('should call registerEvents',function(){
			spyOn(SearchControl,'registerEvents');
			SearchControl.create();
			expect(SearchControl.registerEvents).toHaveBeenCalled();
		});
		it('should call layoutKindChanged',function(){
			spyOn(SearchControl,'layoutKindChanged');
			SearchControl.create();
			expect(SearchControl.layoutKindChanged).toHaveBeenCalled();
		});
	});
	describe('test (destroy)', function(){
		it('should call unregisterEvents',function(){
			spyOn(SearchControl,'unregisterEvents');
			SearchControl.destroy();
			expect(SearchControl.unregisterEvents).toHaveBeenCalled();
		});
		it('should call destroyControls',function(){
			spyOn(SearchControl,'destroyControls');
			SearchControl.destroy();
			expect(SearchControl.destroyControls).toHaveBeenCalled();
		});
		it('should call inherited',function(){
			spyOn(SearchControl,'inherited');
			SearchControl.destroy();
			expect(SearchControl.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (importProps)', function(){
		it('should call inherited',function(){
			spyOn(SearchControl,'inherited');
			SearchControl.importProps();
			expect(SearchControl.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (registerEvents)', function(){
	});
	describe('test (unregisterEvents)', function(){
	});
	describe('test (initComponents)', function(){
		it('should call createChrome',function(){
			spyOn(SearchControl,'createChrome');
			SearchControl.initComponents();
			expect(SearchControl.createChrome).toHaveBeenCalledWith(undefined);
		});
		it('should call inherited',function(){
			spyOn(SearchControl,'inherited');
			SearchControl.initComponents();
			expect(SearchControl.inherited).toHaveBeenCalledWith({});
		});
	});
	describe('test (discoverControlParent)', function(){
	});
	describe('test (createComponents)', function(){
		it('should call inherited',function(){
			spyOn(SearchControl,'inherited');
			SearchControl.createComponents();
			expect(SearchControl.inherited).toHaveBeenCalledWith({});
		});
		it('should call discoverControlParent',function(){
			spyOn(SearchControl,'discoverControlParent');
			SearchControl.createComponents();
			expect(SearchControl.discoverControlParent).toHaveBeenCalled();
		});
	});
	describe('test (createChrome)', function(){
		it('should call createComponents',function(){
			spyOn(SearchControl,'createComponents');
			SearchControl.createChrome();
			expect(SearchControl.createComponents).toHaveBeenCalledWith(undefined,{"isChrome":true});
		});
	});
});