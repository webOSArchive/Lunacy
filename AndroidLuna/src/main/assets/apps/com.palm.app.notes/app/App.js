function App() {
  var self = this;
  self.dbApi = new DbApi();
  self.appManagerApi = new AppManagerApi();

  self.go = function() {
    self.appView = new AppView();
    self.appView.renderInto(document.getElementById('app-container'));
    self.appView.go();
  };

  return self;
}

var app = new App();
