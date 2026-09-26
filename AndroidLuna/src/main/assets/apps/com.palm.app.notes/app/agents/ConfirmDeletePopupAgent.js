function ConfirmDeletePopupAgent(view, memo) {
  var self = this;
  var id;

  self.deleteMemo = function() {
    if (!(memo.id)) {
      view.memoDeleted();
      return;
    }

    id = memo.id;

    app.dbApi.deleteMemo(memo, {
      onSuccess: onDeleteSuccess,
      onFailure: onDeleteFailure
    });
  };

  return self;

  function onDeleteSuccess(request, response, xhr) {
    view.memoDeleted(id);
  }

  function onDeleteFailure(request, response, xhr) {
    view.showError('failed to delete memo: ' + JSON.stringify(response));
  }
}