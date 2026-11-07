var Util = {

    formatCurrency: function(price, isoCurrency){
        var currfmt = new enyo.g11n.NumberFmt({fractionDigits: 2, style: "currency", currency: isoCurrency});
        return currfmt.format(new Number(price));
    },

    formatTime: function(timeInSeconds){
        var fmtDate = new enyo.g11n.DateFmt({date: "medium"});
        return fmtDate.format(new Date(timeInSeconds));
    },

    formatPercent: function(num){
        var currfmt = new enyo.g11n.NumberFmt({style: "percent"});
        return currfmt.format(new Number(num));
    }

};