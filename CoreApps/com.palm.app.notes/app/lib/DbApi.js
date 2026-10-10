function DbApi() {
  var self = this;

  self.getMemos = function(filter, callbacks) {
    var dbService = new ServiceWrapper();
    var newCallbacks = {onSuccess: escapeHtml, onFailure: callbacks.onFailure};

    if (filter.length > 0) {
      dbService.search(filter, newCallbacks);
    } else {
      dbService.find(newCallbacks);
    }

    function escapeHtml(request, response, xhr) {
      var results = response.results;

      for (var i=0; i < results.length; i++) {
        var memo = results[i];

        if (memo.text.match(/<|>/)) {
          memo.text = enyo.string.escapeHtml(memo.text);
          memo.title = enyo.string.escapeHtml(memo.title);
        }
      }

      callbacks.onSuccess(request, response, xhr);
    }
  };

  self.saveMemo = function(memo, callbacks) {
    var dbService = new ServiceWrapper();
    var newCallbacks = {onSuccess: updateMemo, onFailure: callbacks.onFailure};
    dbService.save(memo, newCallbacks);

    function updateMemo(request, response, xhr) {
      var result = response.results[0];
      memo.updateRev(result.rev);
      if (!memo.id) {
        memo.id = result.id;
      }
      callbacks.onSuccess(request, response, xhr);
    }
  };

  self.deleteMemo = function(memo, callbacks) {
    var dbService = new ServiceWrapper();
    var newCallbacks = {onSuccess: updateMemo, onFailure: callbacks.onFailure};

    dbService.del(memo, newCallbacks);

    function updateMemo(request, response, xhr) {
      memo.clear();
      callbacks.onSuccess(request, response, xhr);
    }
  };

  return self;
}