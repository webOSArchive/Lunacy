var GridEditView = "GridEditView";

enyo.kind({
  name: GridEditView,
  kind: enyo.VFlexBox,
  className: "memo-grid-edit-view",
  nextId: 0,
  events: {
    onDoneClick: ""
  },
  components: [
    {
      kind: TitleBarView,
      components: [
        {kind: enyo.Control, flex: 1},
        {
          content: $L("Delete or rearrange memos"),
          className: "grid-edit-title"
        },
        {
          name: 'done',
          kind: "enyo.gemstone.Button",
          content: $L('Done'),
          className: 'edit-done-button title-bar-button',
          onclick: 'doneClick'
        }        
      ]
    },
    {
      kind: enyo.Scroller,
      className: "grid-scroller",
      flex: 1,
      components: [
        {
          name: "memoPad",
          kind: NewMemoView
        },
        {
          name: "memoList",
          kind: enyo.VirtualRepeater,
          className: "memo-list",
          onGetItem: "getMemoItem",
          components: [
            {
              name: "memoItem",
              kind: MemoWithDeleteView,
              className: "memo-with-delete-item",
              onDeleteMemo: 'deleteMemo'
            }
          ]
        }
      ]
    },
    {
      name: 'confirmDeletePopup',
      kind: ConfirmDeletePopupView,
      onDelete: 'memoDeleted',
      onCancel: 'closeDeleteConfirmPopup'
    }
  ],

  create: function() {
    this.inherited(arguments);
  },

  viewSelected: function(memos) {
    this.agent = new GridEditAgent(this, memos);
    this.agent.ready();
  },

  getMemoItem: function(sender, index) {

    if (!this.agent) {
      return;
    }

    var memos = this.agent.getMemos();

    if (memos && index < memos.length) {
      var n = memos[index];
      this.$.memoItem.setMemo(n);

      return true;
    }
  },

  deleteMemo: function(inSender) {
    var index = inSender.manager.fetchRowIndex();
    this.$.confirmDeletePopup.ready(new Memo(this.agent.getMemos()[index]));
    this.$.confirmDeletePopup.openAtCenter();
  },

  closeDeleteConfirmPopup: function() {
    this.$.confirmDeletePopup.close();
  },

  memoDeleted: function(inSender, memoId) {
    this.closeDeleteConfirmPopup();
    this.agent.deleteMemoFromList(memoId);
  },

  doneClick: function() {
    this.doDoneClick();
  },

  // View interface for GridAgent
  drawMemos: function() {
    this.$.memoList.render();
  },

  drawMemoPad: function(memoPadColor) {
    this.$.memoPad.setCurrentColor(memoPadColor);
  }
});