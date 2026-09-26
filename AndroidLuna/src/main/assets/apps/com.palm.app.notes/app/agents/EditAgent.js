function EditAgent(view, memo) {
  var self = this;

  self.go = function() {
    view.drawMemo();
  };

  self.getMemo = function() {
    return memo;  
  };

  self.setMemoText = function(text) {
    memo.text = text.replace(/((&nbsp;)|(\s)|(<br>)|(<BR>))+$/, '');
  };

  self.done = function() {
    view.updateMemoText();
    saveMemoThenTellView(goGrid);
  };

  self.saveMemo = function() {
    saveMemoThenTellView(memoSaved);
  };

  self.sendMemo = function() {
    view.updateMemoText();
    app.appManagerApi.sendMemo(memo.displayText,  {
      onSuccess: enyo.nop,
      onFailure: enyo.nop
    })
  };

  return self;

  function saveMemoThenTellView(onSuccess) {
    if (!memo.text) {
      if (memo.id) {
        app.dbApi.deleteMemo(memo, {
          onSuccess: onSuccess,
          onFailure: onDeleteFailure
        });
      } else {
        onSuccess();
      }
      return;
    }

    app.dbApi.saveMemo(memo, {
      onSuccess: onSuccess,
      onFailure: onSaveFailure
    });
  }

  function goGrid() {
    view.goGrid()
  }

  function memoSaved() {    
    view.memoSaved()
  }

  function onSaveFailure(request, response, xhr) {
    view.showError('failed to save memo: ' + JSON.stringify(response));
  }

  function onDeleteFailure(request, response, xhr) {
   view.showError('failed to delete memo: ' + JSON.stringify(response));
  }
}