function AppManagerApi() {
  var self = this;

  self.sendMemo = function() {
    var appService = new ServiceWrapper();
    appService.launch.apply(appService, arguments);
  };

  return self;
}