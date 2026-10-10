function GridAgent(view) {
  var self = this;
  var memos = [];
  var filteredMemos = [];
  var filter = '';

  self.populateGrid = function(filterText) {
    filter = filterText;
    app.dbApi.getMemos(filter, {onSuccess: onSuccess, onFailure: enyo.nop});
  };

  self.populateGridThenEdit = function(newMemoText) {
    app.dbApi.getMemos('', {onSuccess: util.wrap(onSuccess, onSuccessForNew), onFailure: enyo.nop});

    function onSuccessForNew(original, request, response, xhr) {
      original(request, response, xhr);
      view.editNewMemo(newMemoText);
    }
  };

  self.getMemos = function() {
    return (filter.length ? filteredMemos : memos);
  };

  self.getTitleBarLabel = function() {
    var localMemos = self.getMemos();

    var memoCountTemplate = new enyo.g11n.Template($L("0#no memos|1#1 memo|##{numMemos} memos"));
    var searchCountTemplate = new enyo.g11n.Template($L("0#no search results|1#1 search result|##{results} search results"));

    if (filter.length) {
      return searchCountTemplate.formatChoice(localMemos.length, {results: localMemos.length});
    } else {
      return memoCountTemplate.formatChoice(localMemos.length, {numMemos: localMemos.length});
    }
  };

  self.createNewMemo = function(options) {
    var memo = new Memo(options);
    memo.color = Memo.getNextMemoColor(memos[0]);

    var firstMemoPosition = (memos[0] && memos[0].position) || 'z';
    memo.position = Memo.getMemoPosition('a', firstMemoPosition);

    return memo;
  };

  self.deleteMemoFromList = function(memoId) {
    if (!memoId) {
      return;
    }

    removeFromCollection(memos, memoId);

    if (filter.length) {
      removeFromCollection(filteredMemos, memoId);
    }

    refreshGrid();

    function removeFromCollection(collection, id) {
      var index, item;
      for (index = 0; index < collection.length; index++) {
        item = collection[index];
        if (item._id == id) {
          break;
        }
      }

      if (index < collection.length) {
        collection.splice(index, 1);
      }
    }
  };

  return self;

  function onSuccess(request, response, xhr) {
    refreshMemos(response.results);
    if (filter) {
      view.scrollMemosToTop();
      view.drawMemos();
    } else {
      refreshGrid();
    }
  }

  function refreshMemos(results) {
    var localResults = self.getMemos();
    localResults.splice(0);
    localResults.push.apply(localResults, results);
    for (var i = 0; i < localResults.length; i++) {
      var text = localResults[i].text;
      if (text.length < Memo.MAX_LENGTH_FOR_RUN_TEXT_INDEXER) {
        text = enyo.string.runTextIndexer(text);
      }
      if (filter.length) {
        localResults[i].displayText = util.highlightString(filter, text);
      } else {
        localResults[i].displayText = text;
      }
    }
  }

  function refreshGrid() {
    view.drawMemoPadWithColor(Memo.getNextMemoColor(memos[0]));
    view.showGrid();
    view.drawMemos();
  }
}