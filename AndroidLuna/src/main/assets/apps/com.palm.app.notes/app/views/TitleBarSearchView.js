var TitleBarSearchView = "TitleBarSearchView";

enyo.kind({
  name: TitleBarSearchView,
  kind: enyo.HFlexBox,
  flex: 1,
  events: {
    onSearch: ""
  },
  components:[
    {
      name: "search",
      kind: "enyo.RoundedSearchInput",
      className: "search-input",
      hint: $L("Search memos"),
      onchange: "propagateSearch",
      onSearch: "propagateSearch",
      changeOnInput: true,
      tabIndex: -1,
      autoCapitalize: false,
      onCancel: "resetSearch"
    },
    {kind: enyo.Control, flex: 1}
  ],

  resetSearch: function() {
    this.propagateSearch("");
  },

  getValue: function() {
    return this.$.search.getValue();
  },

  setValue: function(value) {
    return this.$.search.setValue(value);
  },

  propagateSearch: function() {
    this.doSearch(arguments);
  }
});