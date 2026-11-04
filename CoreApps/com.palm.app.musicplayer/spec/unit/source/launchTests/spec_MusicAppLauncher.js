/*globals specOut, enyo, $L, event, Utilities, window, LibraryTools */

//var creator = function(){
//				return enyo.create({kind: "MusicAppLauncher"});
//			};
//var objName = "MusicAppLauncher";
//
//var before = function (obj, callbacks) {
//	
//	   
//};
//var event = [[null, {rowIndex:0, dispatchTarget: null}]];
//var params = {
//		"applicationRelaunchHandler": [[{}]],
//		"activateApp": [[{windowName: "asdf", path: "sdfg"}]]
//		
//};
//
//specOut(creator, objName, before, params);


describe('MusicAppLauncher tests', function(){
	var creator = function (){
				return enyo.create({kind: "MusicAppLauncher"});
			};
	var MusicAppLauncher;
	beforeEach(function(){
		MusicAppLauncher = creator();
		var before = function (obj, callbacks) {
		
	   
};
		
		var callback = function(one, fun){return fun;};
		before(MusicAppLauncher,callback);
		enyo.windows = {getWindows: function(){}, activate: function(){}, getRootWindow: function(){}};
		enyo.fetchAppRootPath = function(){};
	
	});
	it('should correctly set up object', function(){
		expect(MusicAppLauncher.kind).toEqual("enyo.Object");
		expect(MusicAppLauncher.faces).toEqual({"musicplayer":{"windowName":"com.palm.app.musicplayer","path":"main.html","state":"unknown"}});
		expect(MusicAppLauncher.kindName).toEqual("MusicAppLauncher");
	});
	describe('test (startup)', function(){
		beforeEach(function(){
			MusicAppLauncher.activateApp = function(){};
			MusicAppLauncher.appSelect = function(){};
		});
		it('should change startParams',function(){
			MusicAppLauncher.startup();
			expect(MusicAppLauncher.startParams).toEqual({});
		});
		it('should call appSelect',function(){
			spyOn(MusicAppLauncher,'appSelect');
			MusicAppLauncher.startup();
			expect(MusicAppLauncher.appSelect).toHaveBeenCalled();
		});
		it('should call activateApp',function(){
			spyOn(MusicAppLauncher,'activateApp');
			MusicAppLauncher.startup();
			expect(MusicAppLauncher.activateApp).toHaveBeenCalled();
		});
	});
	describe('test (applicationRelaunchHandler)', function(){
		beforeEach(function(){
			MusicAppLauncher.activateApp = function(){};
			MusicAppLauncher.appSelect = function(){};
		});
	describe('testing (applicationRelaunchHandler) with params [{}]', function(){
		it('should change startParams',function(){
			MusicAppLauncher.applicationRelaunchHandler({});
			expect(MusicAppLauncher.startParams).toEqual({});
		});
		it('should call appSelect',function(){
			spyOn(MusicAppLauncher,'appSelect');
			MusicAppLauncher.applicationRelaunchHandler({});
			expect(MusicAppLauncher.appSelect).toHaveBeenCalled();
		});
		it('should call activateApp',function(){
			spyOn(MusicAppLauncher,'activateApp');
			MusicAppLauncher.applicationRelaunchHandler({});
			expect(MusicAppLauncher.activateApp).toHaveBeenCalled();
		});
	});
	});
	describe('test (appSelect)', function(){
		it("should return this.faces.musicplayer", function(){
			expect(MusicAppLauncher.appSelect()).toBe(MusicAppLauncher.faces.musicplayer);
		});
		
	});
	describe('test (activateApp)', function(){
		beforeEach(function(){
			MusicAppLauncher.appSelect = function(){};
		});
		describe("should return if needed to", function(){
			it("should call getWindows", function(){
				spyOn(enyo.windows, 'getWindows');
				MusicAppLauncher.activateApp();
				expect(enyo.windows.getWindows).not.toHaveBeenCalled();
			});
			it("should call fetchAppRootPath", function(){
				spyOn(enyo, 'fetchAppRootPath');
				MusicAppLauncher.activateApp();
				expect(enyo.fetchAppRootPath).not.toHaveBeenCalled();
			});
			it("should activate properly", function(){
				spyOn(enyo.windows, 'activate');
				MusicAppLauncher.activateApp();
				expect(enyo.windows.activate).not.toHaveBeenCalled();
			});
		});
		
		it("should call getWindows", function(){
			spyOn(enyo.windows, 'getWindows');
			MusicAppLauncher.activateApp({"windowName":"com.palm.app.musicplayer","path":"sdfg"});
			expect(enyo.windows.getWindows).toHaveBeenCalled();
		});
		it("should call fetchAppRootPath", function(){
			spyOn(enyo, 'fetchAppRootPath');
			MusicAppLauncher.activateApp({"windowName":"com.palm.app.musicplayer","path":"sdfg"});
			expect(enyo.fetchAppRootPath).toHaveBeenCalled();
		});
		it("should activate properly", function(){
			spyOn(enyo.windows, 'activate');
			MusicAppLauncher.activateApp({"windowName":"com.palm.app.musicplayer","path":"sdfg"});
			expect(enyo.windows.activate).toHaveBeenCalledWith("sdfg", "com.palm.app.musicplayer", MusicAppLauncher.startparams);
		});
		
		
	});
	describe('test (verboseAppContext)', function(){
		it("should work if no launch params and not in app", function(){
			expect(MusicAppLauncher.verboseAppContext()).toBe("no PalmSystem");
		});
		it("should work if no launch params", function(){
			window.PalmSystem = true;
			expect(MusicAppLauncher.verboseAppContext()).toBe("no PalmSystem.launchParams");
		});
		it("should work if  launch params", function(){
			window.PalmSystem = {};
			window.PalmSystem.launchParams = "asdf";
			expect(MusicAppLauncher.verboseAppContext()).toBe("PalmSystem.launchParams = "+window.PalmSystem.launchParams);
		});		
		
		
	});
	describe('test (base)', function(){
		it('should call _constructor',function(){
			spyOn(MusicAppLauncher,'_constructor');
			MusicAppLauncher.base();
			expect(MusicAppLauncher._constructor).toHaveBeenCalled();
		});
	});
	describe('test (ctor)', function(){
		it('should call _constructor',function(){
			spyOn(MusicAppLauncher,'_constructor');
			MusicAppLauncher.ctor();
			expect(MusicAppLauncher._constructor).toHaveBeenCalled();
		});
	});
	describe('test (destroyObject)', function(){
		it('should change undefined',function(){
			MusicAppLauncher.destroyObject();
			expect(MusicAppLauncher.undefined).toEqual(null);
		});
	});
	describe('test (getProperty)', function(){
	});
	describe('test (_setProperty)', function(){
		it('should change undefined',function(){
			MusicAppLauncher._setProperty();
			expect(MusicAppLauncher.undefined).toEqual(undefined);
		});
	});
	describe('test (setProperty)', function(){
		it('should call _setProperty',function(){
			spyOn(MusicAppLauncher,'_setProperty');
			MusicAppLauncher.setProperty();
			expect(MusicAppLauncher._setProperty).toHaveBeenCalledWith(undefined,undefined,"undefinedChanged");
		});
	});
	describe('test (log)', function(){
		it('should call _log',function(){
			spyOn(MusicAppLauncher,'_log');
			MusicAppLauncher.log();
			expect(MusicAppLauncher._log).toHaveBeenCalledWith("log",{});
		});
	});
	describe('test (warn)', function(){
		it('should call _log',function(){
			spyOn(MusicAppLauncher,'_log');
			MusicAppLauncher.warn();
			expect(MusicAppLauncher._log).toHaveBeenCalledWith("warn",{});
		});
	});
	describe('test (error)', function(){
		it('should call _log',function(){
			spyOn(MusicAppLauncher,'_log');
			MusicAppLauncher.error();
			expect(MusicAppLauncher._log).toHaveBeenCalledWith("error",{});
		});
	});
});