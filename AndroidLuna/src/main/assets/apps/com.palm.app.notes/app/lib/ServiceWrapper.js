var ServiceWrapper = 'ServiceWrapper';

enyo.kind({
  name: ServiceWrapper,
  kind: enyo.Component,
  components: [
    {
      kind: enyo.DbService,
      dbKind: "com.palm.note:1",
      components: [
        {name: 'find', method: 'find', onSuccess: 'onSuccess', onFailure: 'onFailure'},
        {name: 'search', method: 'search', onSuccess: 'onSuccess', onFailure: 'onFailure'},
        {name: 'merge', method: 'merge', onSuccess: 'onSuccess', onFailure: 'onFailure'},
        {name: 'put', method: 'put', onSuccess: 'onSuccess', onFailure: 'onFailure'},
        {name: 'del', method: 'del', onSuccess: 'onSuccess', onFailure: 'onFailure'},
        {name: 'delAll', method: 'delByQuery', onSuccess: 'onSuccess', onFailure: 'onFailure'}
      ]
    },
    {
      kind: enyo.PalmService,
      service: 'luna://com.palm.applicationManager/',
      components: [
        {name: 'launch', method: 'open', onSuccess: 'onSuccess', onFailure: 'onFailure'}
      ]
    }
  ],

  find: function(callbacks) {
    this.saveCallbacks(callbacks);
    this.$.find.call({
      query: {
        orderBy: "position"
      }
    });
  },

  search: function(filter, callbacks) {
    this.saveCallbacks(callbacks);
    this.$.search.call({
      query: {
        from: "com.palm.note:1",
        where: [{
            prop: "text",
            op: "?",
            val: filter,
            collate: "primary",
            tokenize: "all"
          }],
          orderBy: "position"
      }
    });
  },

  save: function(memo, callbacks) {
    this.saveCallbacks(callbacks);
    var memoJson = memo.serialize();
    var method = memoJson._id ? 'merge' : 'put';
    this.$[method].call({
      objects: [memoJson]
    });
  },

  del: function(memo, callbacks) {
    this.saveCallbacks(callbacks);
    var memoJson = memo.serialize();
    this.$.del.call({
      ids: [memoJson._id]
    });
  },

  delAll: function(callbacks) {
    this.saveCallbacks(callbacks);
    this.$.delAll.call();
  },

  launch: function(memoHtml, callbacks) {
    this.saveCallbacks(callbacks);
    this.$.launch.call({
      id: 'com.palm.app.email',
      params: {
        summary: $L("Just a quick memo"),
        text: memoHtml
      }
    });
  },

  saveCallbacks:function (callbacks) {
    this.onSuccess = callbacks.onSuccess;
    this.onFailure = util.wrap(callbacks.onFailure || function() {}, this.failure);
  },

  failure: function(originalOnFailure, request, response, xhr) {
    if (logApiFailures) {
      console.error("==> FAILED Luna Request");
      console.error("=============>", arguments);
//      console.error("==> request: ", JSON.stringify(request));
//      console.error("==> response: ", JSON.stringify(response));
      for (var key in response) {
        console.error("- " + key + ': ' + response[key]);
      }
      console.error("=============> XHR");
      for (key in xhr) {
        console.error("- " + key + ': ' + response[key]);
      }
    }
    originalOnFailure(request, response, xhr);
  }
});
