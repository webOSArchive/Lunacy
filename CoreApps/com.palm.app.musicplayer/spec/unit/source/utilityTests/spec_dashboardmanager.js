/*globals specOut, enyo, $L, event, Utilities, window, LibraryTools */


//var creator = function(){
//				return enyo.create({kind: "kindDashboardManager"});
//			};
//var objName = "kindDashboardManager";
//
//var before = function (obj, callbacks) {
//	
//        
//};
//var params = {
//	"updateControlDashboardInfo" : [[{boolForced: true, strTrackTitle: "hi"},true,true],
//	                                [{boolForced: true, strTrackTitle: "hi"},true,false],
//	                                [{boolForced: true, strTrackTitle: "hi"},false,true],
//	                                [{boolForced: true, strTrackTitle: "hi"},false,false],
//	                                [{boolForced: false, strTrackTitle: "hi"},true,true],
//	                                [{boolForced: false, strTrackTitle: "hi"},true,false],
//	                                [{boolForced: false, strTrackTitle: "hi"},false,true],
//	                                [{boolForced: false, strTrackTitle: "hi"},false,false]],
//	"setPlayPause"				: [[true],[false]]
//};
//
//specOut(creator, objName, before, params);


describe('kindDashboardManager tests', function(){
	var creator = function (){
				return enyo.create({kind: "kindDashboardManager"});
			};
	var kindDashboardManager;
	beforeEach(function(){
		kindDashboardManager = creator();
		var before = function (obj, callbacks) {
	
        
};
		var callback = function(one, fun){return fun;};
		before(kindDashboardManager,callback);
	});
	it('should correctly set up object', function(){
		expect(kindDashboardManager._componentNameMap).toEqual({});
		expect(kindDashboardManager.$).toEqual({});
		expect(kindDashboardManager.kind).toEqual("kindDashboardManager");
		expect(kindDashboardManager.id).toEqual("");
		expect(kindDashboardManager.boolAudioPlayingCache).toEqual(false);
		expect(kindDashboardManager.kindName).toEqual("kindDashboardManager");
		expect(kindDashboardManager.kindComponents).toEqual([]);
		expect(kindDashboardManager.published).toEqual({"owner":null,"name":""});
		expect(kindDashboardManager.defaultKind).toEqual("Component");
		expect(kindDashboardManager.wantsEvents).toEqual(true);
		expect(kindDashboardManager.owner).toEqual(null);
		expect(kindDashboardManager.name).toEqual("");
	});
	describe('test (openControlDashboard)', function(){
		beforeEach(function(){
			enyo.windows.openDashboard = function(){return "";};
		});
		
		describe("with objTrackInfoCache", function(){
			
			beforeEach(function(){
				kindDashboardManager.objTrackInfoCache = true;
				
			});
			
			it("should change dashControls", function(){
				kindDashboardManager.dashControls = 'asdf';
				kindDashboardManager.openControlDashboard();
				expect(kindDashboardManager.dashControls).not.toBe('asdf');
			});
			it("should call enyo.windows.openDashboard", function(){
				spyOn(enyo.windows, "openDashboard");
				kindDashboardManager.dashControls = 'asdf';
				kindDashboardManager.openControlDashboard();
				expect(enyo.windows.openDashboard).toHaveBeenCalled();
			});
		});
		describe("without objTrackInfoCache", function(){
			
			beforeEach(function(){
				kindDashboardManager.objTrackInfoCache = undefined;
			});
			
			it("should change dashControls", function(){
				kindDashboardManager.dashControls = 'asdf';
				kindDashboardManager.openControlDashboard();
				expect(kindDashboardManager.dashControls).toBe('asdf');
			});
			it("should call enyo.windows.openDashboard", function(){
				spyOn(enyo.windows, "openDashboard");
				kindDashboardManager.dashControls = 'asdf';
				kindDashboardManager.openControlDashboard();
				expect(enyo.windows.openDashboard).not.toHaveBeenCalled();
			});
		});
		
	});
	describe('test (closeControlDashboard)', function(){
		describe('when dashControls is defined', function(){
			var called = false;
			beforeEach(function(){
				kindDashboardManager.dashControls = {};
				called = false;
				kindDashboardManager.dashControls.close = function(){
					called = true;
				};
			});
			it("should call close", function(){
				kindDashboardManager.closeControlDashboard();
				expect(called).toBe(true);
			});
			it("should set it to null", function(){
				kindDashboardManager.closeControlDashboard();
				expect(kindDashboardManager.dashControls).toBe(null);
			});
			
		});
		
		
	});
	describe('test (updateControlDashboardInfo)', function(){
		var objTrackInfo;
		beforeEach(function(){
			spyOn(kindDashboardManager, "applyDashParams");
			enyo.windows.addBannerMessage = function(){};
		});
		
		
		
		it("should work with undefines", function(){
			kindDashboardManager.updateControlDashboardInfo({"strTrackTitle":"hi"},undefined,true);
			
		});
		
		it("should work with undefines", function(){
			kindDashboardManager.updateControlDashboardInfo({"strTrackTitle":"hi"},false,true);
		});
		
		describe('testing (updateControlDashboardInfo) with params [{"boolForced":true,"strTrackTitle":"hi"},true,true]', function(){
			beforeEach(function(){
				objTrackInfo = {"boolForced":true,"strTrackTitle":"hi"};
			});
			
			it('should change objTrackInfoCache',function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,true,true);
				expect(kindDashboardManager.objTrackInfoCache).toEqual(objTrackInfo);
			});
			it("should call applyDashParams", function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,true,true);
				expect(kindDashboardManager.applyDashParams).toHaveBeenCalledWith({objTrackInfo: objTrackInfo});
			});
			it("shouldnot call addBannerMessage", function(){
				spyOn(enyo.windows, "addBannerMessage");
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,true,true);
				expect(enyo.windows.addBannerMessage).not.toHaveBeenCalled();
			});
			
		});
	
		describe('testing (updateControlDashboardInfo) with params [{"boolForced":true,"strTrackTitle":"hi"},true,false]', function(){
			beforeEach(function(){
				objTrackInfo = {"boolForced":true,"strTrackTitle":"hi"};
			});
			
			it('should change objTrackInfoCache',function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,true,false);
				expect(kindDashboardManager.objTrackInfoCache).toEqual(objTrackInfo);
			});
			it("should call applyDashParams", function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,true,false);
				expect(kindDashboardManager.applyDashParams).toHaveBeenCalledWith({objTrackInfo: objTrackInfo});
			});
			it("shouldnot call addBannerMessage", function(){
				spyOn(enyo.windows, "addBannerMessage");
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,true,false);
				expect(enyo.windows.addBannerMessage).not.toHaveBeenCalled();
			});
			
		});
		describe('testing (updateControlDashboardInfo) with params [{"boolForced":true,"strTrackTitle":"hi"},false,true]', function(){
			beforeEach(function(){
				objTrackInfo = {"boolForced":true,"strTrackTitle":"hi"};
			});
			
			it('should change objTrackInfoCache',function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,false,true);
				expect(kindDashboardManager.objTrackInfoCache).toEqual(objTrackInfo);
			});
			it("should call applyDashParams", function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,true,true);
				expect(kindDashboardManager.applyDashParams).toHaveBeenCalledWith({objTrackInfo: objTrackInfo});
			});
			it("shouldnot call addBannerMessage", function(){
				spyOn(enyo.windows, "addBannerMessage");
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,false,true);
				expect(enyo.windows.addBannerMessage).not.toHaveBeenCalled();
			});
		});
		describe('testing (updateControlDashboardInfo) with params [{"boolForced":true,"strTrackTitle":"hi"},false,false]', function(){
			beforeEach(function(){
				objTrackInfo = {"boolForced":true,"strTrackTitle":"hi"};
			});
			it('should change objTrackInfoCache',function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,false,false);
				expect(kindDashboardManager.objTrackInfoCache).toEqual(objTrackInfo);
			});
			it("should call applyDashParams", function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,false,false);
				expect(kindDashboardManager.applyDashParams).toHaveBeenCalledWith({objTrackInfo: objTrackInfo});
			});
			it("shouldnot call addBannerMessage", function(){
				spyOn(enyo.windows, "addBannerMessage");
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,false,false);
				expect(enyo.windows.addBannerMessage).not.toHaveBeenCalled();
			});
		});
		describe('testing (updateControlDashboardInfo) with params [{"boolForced":false,"strTrackTitle":"hi"},true,true]', function(){
			beforeEach(function(){
				objTrackInfo = {"boolForced":false,"strTrackTitle":"hi"};
			});
			it('should change objTrackInfoCache',function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,true,true);
				expect(kindDashboardManager.objTrackInfoCache).toEqual(objTrackInfo);
			});
			it("should call applyDashParams", function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,true,true);
				expect(kindDashboardManager.applyDashParams).toHaveBeenCalledWith({objTrackInfo: objTrackInfo});
			});
			it("shouldnot call addBannerMessage", function(){
				spyOn(enyo.windows, "addBannerMessage");
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,true,true);
				expect(enyo.windows.addBannerMessage).not.toHaveBeenCalled();
			});
		});
		describe('testing (updateControlDashboardInfo) with params [{"boolForced":false,"strTrackTitle":"hi"},true,false]', function(){
			beforeEach(function(){
				objTrackInfo = {"boolForced":false,"strTrackTitle":"hi"};
			});
			it('should change objTrackInfoCache',function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,true,false);
				expect(kindDashboardManager.objTrackInfoCache).toEqual(objTrackInfo);
			});
			it("should call applyDashParams", function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,true,false);
				expect(kindDashboardManager.applyDashParams).toHaveBeenCalledWith({objTrackInfo: objTrackInfo});
			});
			it("shouldnot call addBannerMessage", function(){
				spyOn(enyo.windows, "addBannerMessage");
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,true,false);
				expect(enyo.windows.addBannerMessage).not.toHaveBeenCalled();
			});
		});
		describe('testing (updateControlDashboardInfo) with params [{"boolForced":false,"strTrackTitle":"hi"},false,true]', function(){
			beforeEach(function(){
				objTrackInfo = {"boolForced":false,"strTrackTitle":"hi"};
			});
			it('should change objTrackInfoCache',function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,false,true);
				expect(kindDashboardManager.objTrackInfoCache).toEqual(objTrackInfo);
			});
			it('should create a tempalte',function(){
				// CAN't TEST THIS DUE TO JASMINE FALLING SHORT
//				var myTemplate = enyo.g11n.Template($L("Playing #{trackTitle} by #{trackArtist}"));
//				spyOn(enyo.g11n, "Template").andReturn(myTemplate);
//				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,false,true);
//				expect(enyo.g11n.Template).toHaveBeenCalled();
			});
			it("should call applyDashParams", function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,false,true);
				expect(kindDashboardManager.applyDashParams).toHaveBeenCalledWith({objTrackInfo: objTrackInfo});
			});
			it("should call addBannerMessage", function(){
				spyOn(enyo.windows, "addBannerMessage");
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,false,true);
				expect(enyo.windows.addBannerMessage).toHaveBeenCalled();
			});
		
		});
		describe('testing (updateControlDashboardInfo) with params [{"boolForced":false,"strTrackTitle":"hi"},false,false]', function(){
			beforeEach(function(){
				objTrackInfo = {"boolForced":false,"strTrackTitle":"hi"};
			});
			it('should change objTrackInfoCache',function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,false,false);
				expect(kindDashboardManager.objTrackInfoCache).toEqual(objTrackInfo);
			});
			it("should call applyDashParams", function(){
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,false,false);
				expect(kindDashboardManager.applyDashParams).toHaveBeenCalledWith({objTrackInfo: objTrackInfo});
			});
			it("shouldnot call addBannerMessage", function(){
				spyOn(enyo.windows, "addBannerMessage");
				kindDashboardManager.updateControlDashboardInfo(objTrackInfo,false,false);
				expect(enyo.windows.addBannerMessage).not.toHaveBeenCalled();
			});
		});
	});
	describe('test (setPlayPause)', function(){
		describe('testing (setPlayPause) with params [true]', function(){
			it('should change boolAudioPlayingCache true',function(){
				kindDashboardManager.boolAudioPlayingCache = false;
				kindDashboardManager.setPlayPause(true);
				expect(kindDashboardManager.boolAudioPlayingCache).toEqual(true);
			});
			it('should change boolAudioPlayingCache false',function(){
				kindDashboardManager.boolAudioPlayingCache = true;
				kindDashboardManager.setPlayPause(false);
				expect(kindDashboardManager.boolAudioPlayingCache).toEqual(false);
			});
			
			it('should applyDashParams true',function(){
				spyOn(kindDashboardManager, "applyDashParams");
				kindDashboardManager.boolAudioPlayingCache = false;
				kindDashboardManager.setPlayPause(true);
				expect(kindDashboardManager.applyDashParams).toHaveBeenCalledWith({boolAudioPlaying: true});
			});
			it('should applyDashParams false',function(){
				spyOn(kindDashboardManager, "applyDashParams");
				kindDashboardManager.boolAudioPlayingCache = true;
				kindDashboardManager.setPlayPause(false);
				expect(kindDashboardManager.applyDashParams).toHaveBeenCalledWith({boolAudioPlaying: false});
			});
			
		});
	});
	
	describe('test (applyDashparams)',function(){
		var param = {"my":"param"};
		beforeEach(function(){
			param = {"my":"param"};
			enyo.windows.fetchWindow = function(){};
			enyo.windows.setWindowParams = function(){};
		});
		describe('when dashcontrols is set', function(){
			beforeEach(function(){
				kindDashboardManager.dashControls = true;
			});
			it('should call  enyo.windows.fetchWindow', function(){
				spyOn(enyo.windows, "fetchWindow").andReturn(true);
				spyOn(enyo.windows, "setWindowParams");
				kindDashboardManager.applyDashParams(param);
				expect(enyo.windows.fetchWindow).toHaveBeenCalledWith("dash");
			});
			it('should call  enyo.windows.setWindowParams true', function(){
				spyOn(enyo.windows, "fetchWindow").andReturn(true);
				spyOn(enyo.windows, "setWindowParams");
				kindDashboardManager.applyDashParams(param);
				expect(enyo.windows.setWindowParams).toHaveBeenCalledWith(true, param);
			
			});
			it('should call  enyo.windows.setWindowParams obj', function(){
				spyOn(enyo.windows, "fetchWindow").andReturn({"try two":"two"});
				spyOn(enyo.windows, "setWindowParams");
				kindDashboardManager.applyDashParams(param);
				expect(enyo.windows.setWindowParams).toHaveBeenCalledWith({"try two":"two"}, param);
			
			});
			it('shouldn\'t call  enyo.windows.setWindowParams false', function(){
				spyOn(enyo.windows, "fetchWindow").andReturn({"try two":"two"});
				spyOn(enyo.windows, "setWindowParams");
				kindDashboardManager.applyDashParams(param);
				expect(enyo.windows.setWindowParams).toHaveBeenCalledWith({"try two":"two"}, param);
			
			});
			
		});
		describe('when dashcontrols is false', function(){
			beforeEach(function(){
				kindDashboardManager.dashControls = undefined;
			});
			it('should not call  enyo.windows.fetchWindow', function(){
				spyOn(enyo.windows, "fetchWindow").andReturn(true);
				spyOn(enyo.windows, "setWindowParams");
				kindDashboardManager.applyDashParams(param);
				expect(enyo.windows.fetchWindow).not.toHaveBeenCalled();
			});
			it('should not call  enyo.windows.setWindowParams true', function(){
				spyOn(enyo.windows, "fetchWindow").andReturn(true);
				spyOn(enyo.windows, "setWindowParams");
				kindDashboardManager.applyDashParams(param);
				expect(enyo.windows.setWindowParams).not.toHaveBeenCalled();
			
			});
			it('should call  enyo.windows.setWindowParams obj', function(){
				spyOn(enyo.windows, "fetchWindow").andReturn({"try two":"two"});
				spyOn(enyo.windows, "setWindowParams");
				kindDashboardManager.applyDashParams(param);
				expect(enyo.windows.setWindowParams).not.toHaveBeenCalled();
			
			});
			it('shouldn\'t call  enyo.windows.setWindowParams false', function(){
				spyOn(enyo.windows, "fetchWindow").andReturn({"try two":"two"});
				spyOn(enyo.windows, "setWindowParams");
				kindDashboardManager.applyDashParams(param);
				expect(enyo.windows.setWindowParams).not.toHaveBeenCalled();
			
			});
			
		});
		
	});
	
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(kindDashboardManager,'_constructor');
			kindDashboardManager.base();
			expect(kindDashboardManager._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(kindDashboardManager,'constructed');
			kindDashboardManager.base();
			expect(kindDashboardManager.constructed).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(kindDashboardManager,'_constructor');
			kindDashboardManager.ctor();
			expect(kindDashboardManager._constructor).toHaveBeenCalled();
		});
		it('should call constructed',function(){
			spyOn(kindDashboardManager,'constructed');
			kindDashboardManager.ctor();
			expect(kindDashboardManager.constructed).toHaveBeenCalled();
		});
	});
	describe('test (toString)', function(){
		describe('test (constructed)', function(){
			it('should call create',function(){
				spyOn(kindDashboardManager,'create');
				kindDashboardManager.constructed();
				expect(kindDashboardManager.create).toHaveBeenCalledWith(undefined);
			});
			it('should call ready',function(){
				spyOn(kindDashboardManager,'ready');
				kindDashboardManager.constructed();
				expect(kindDashboardManager.ready).toHaveBeenCalled();
			});
		});
	});
	describe('test (create)', function(){
		it('should call importProps',function(){
			spyOn(kindDashboardManager,'importProps');
			kindDashboardManager.create();
			expect(kindDashboardManager.importProps).toHaveBeenCalledWith(undefined);
		});
		it('should call ownerChanged',function(){
			spyOn(kindDashboardManager,'ownerChanged');
			kindDashboardManager.create();
			expect(kindDashboardManager.ownerChanged).toHaveBeenCalled();
		});
		it('should call initComponents',function(){
			spyOn(kindDashboardManager,'initComponents');
			kindDashboardManager.create();
			expect(kindDashboardManager.initComponents).toHaveBeenCalled();
		});
	});
	describe('test (initComponents)', function(){
		it('should call createComponents',function(){
			spyOn(kindDashboardManager,'createComponents');
			kindDashboardManager.initComponents();
			expect(kindDashboardManager.createComponents).toHaveBeenCalledWith([]);
		});
		it('should call createContainedComponents',function(){
			spyOn(kindDashboardManager,'createContainedComponents');
			kindDashboardManager.initComponents();
			expect(kindDashboardManager.createContainedComponents).toHaveBeenCalledWith(undefined);
		});
	});
	describe('test (ready)', function(){
	});
	describe('test (destroy)', function(){
		it('should change destroyed',function(){
			kindDashboardManager.destroy();
			expect(kindDashboardManager.destroyed).toEqual(true);
		});
		it('should call destroyComponents',function(){
			spyOn(kindDashboardManager,'destroyComponents');
			kindDashboardManager.destroy();
			expect(kindDashboardManager.destroyComponents).toHaveBeenCalled();
		});
		it('should call setOwner',function(){
			spyOn(kindDashboardManager,'setOwner');
			kindDashboardManager.destroy();
			expect(kindDashboardManager.setOwner).toHaveBeenCalledWith(null);
		});
	});
	describe('test (destroyComponents)', function(){
		it('should call getComponents',function(){
			spyOn(kindDashboardManager,'getComponents');
			kindDashboardManager.destroyComponents();
			expect(kindDashboardManager.getComponents).toHaveBeenCalled();
		});
	});
	describe('test (importProps)', function(){
	});
	describe('test (getId)', function(){
	});
	describe('test (makeId)', function(){
	});
	describe('test (ownerChanged)', function(){
		it('should change id',function(){
			kindDashboardManager.ownerChanged();
			expect(kindDashboardManager.id).toEqual('');
		});
		it('should call makeId',function(){
			spyOn(kindDashboardManager,'makeId');
			kindDashboardManager.ownerChanged();
			expect(kindDashboardManager.makeId).toHaveBeenCalled();
		});
	});
});