function GridEditAgent(view, memos) {
  var self = this;

  self.ready = function() {
    refreshGrid();
  };

  self.getMemos = function() {
    return memos;
  };

  self.deleteMemoFromList = function(memoId) {
    var index;
    for (index = 0; index < memos.length; index++) {
      var memo = memos[index];
      if (memo._id == memoId) {
        break;
      }
    }

    if (index < memos.length) {
      memos.splice(index, 1);
    }

    refreshGrid();
  };

  return self;

  function refreshGrid() {
    view.drawMemos();
    view.drawMemoPad(Memo.getNextMemoColor(memos[0]));
  }
}