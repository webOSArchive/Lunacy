var MemoWithDeleteView = "MemoWithDeleteView";

enyo.kind({
  name:MemoWithDeleteView,
  kind: enyo.HFlexBox,
  className: "memo-preview with-delete",
  events: {
    onDeleteMemo: ''
  },
  published: {
    memo: null
  },

  components: [
    {
      kind: enyo.CustomButton,
      className: "delete-button",
      caption: ' ',
      onclick: 'deleteMemoClick'
    },
    {
      name: "text",
      className: "memo-preview-content"
    }
  ],

  memoChanged: function() {
    this.$.text.content = this.memo.text;
    this.setClassName(this.className + " " + this.memo.color);
  },

  deleteMemoClick: function() {
    this.doDeleteMemo(this.memo);
  }
});