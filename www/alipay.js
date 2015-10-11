/*global cordova, module*/

module.exports = {
    pay: function (paymentInfo, successCallback, errorCallback) {
        if (errorCallback == null) { errorCallback = function() {}}

        if (typeof errorCallback != "function")  {
            console.log("Alipay pay failure: failure parameter not a function");
            return
        }

        if (typeof successCallback != "function") {
            console.log("Alipay pay failure: success callback parameter must be a function");
            return
        }
        cordova.exec(successCallback, errorCallback, "AliPay", "pay", [paymentInfo]);
    },

    isWalletExist : function (successCallback, errorCallback) {
        if (errorCallback == null) { errorCallback = function() {}}

        if (typeof errorCallback != "function")  {
            console.log("Alipay isWalletExist  failure: failure parameter not a function");
            return
        }

        if (typeof successCallback != "function") {
            console.log("Alipay isWalletExist  failure: success callback parameter must be a function");
            return
        }
        cordova.exec(successCallback, errorCallback, "AliPay", "isWalletExist", []);
    }
};
