var MemoRowView = "MemoRowView";

enyo.kind({
  name: MemoRowView,
  kind: "enyo.HFlexBox",
  events: {
    onNewMemoClick: '',
    onDeleteMemoClick: '',
    onMemoClick: '',
    onSetNewMemoColor: ''
  },
  components: [
    {
      name: 'newMemo',
      className: 'new-memo',
      kind: NewMemoView,
      onNewMemoClick: "newMemoClick"
    },
    {
      name: "memo-0",
      kind: MemoView,
      onMemoClick: 'memoClick',
      onDeleteMemoClick: 'deleteMemoClick'
    },
    {
      name: "memo-1",
      kind: MemoView,
      onMemoClick: 'memoClick',
      onDeleteMemoClick: 'deleteMemoClick'
    },
    {
      name: "memo-2",
      kind: MemoView,
      onMemoClick: 'memoClick',
      onDeleteMemoClick: 'deleteMemoClick'
    },
    {
      name: "memo-3",
      kind: MemoView,
      onMemoClick: 'memoClick',
      onDeleteMemoClick: 'deleteMemoClick'
    }
  ],

  showNewMemo: function(visible) {
    this.$.newMemo.setShowing(visible);
  },

  setNewMemoColor: function(color) {
    this.doSetNewMemoColor(color);
  },

  setMemos: function(memos) {
    var memo, memoView;
    for (var i = 0; i < 4; i++) {
      memo = memos[i];
      memoView = this.$["memo-" + i];
      if (memo) {
        memoView.setMemo(memo);
        memoView.show();
      } else {
        memoView.hide();
      }
    }
  },

  newMemoClick: function(color) {
    this.doNewMemoClick(color);
  },

  memoClick: function(sender, memoJson) {
    // TODO: animations   this.doMemoClick(sender, memoJson);
     this.doMemoClick(memoJson);
  },

  deleteMemoClick: function(sender, memoJson) {
    this.doDeleteMemoClick(memoJson);
  }
});