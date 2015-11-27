package wang.imchao.plugin.alipay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.alipay.sdk.app.PayTask;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class AliPayPlugin extends CordovaPlugin {
    private static String TAG = "AliPayPlugin";
    private static String PAY = "pay";
    private static String IS_WALLET_EXIST = "isWalletExist";

    //商户PID
    private String partner = "";
    //商户收款账号
    private String seller = "";
    //商户私钥，pkcs8格式
    private String privateKey = "";

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        partner = webView.getPreferences().getString("partner", "");
        seller = webView.getPreferences().getString("seller", "");
        privateKey = webView.getPreferences().getString("private_key", "");
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {

        boolean result = false;

        Log.v(TAG, "execute: action=" + action);

        if (PAY.equals(action)) {
            try {
                JSONObject arguments = args.getJSONObject(0);
                String tradeNo = arguments.getString("tradeNo");
                String subject = arguments.getString("subject");
                String body = arguments.getString("body");
                String price = arguments.getString("price");
                String fromUrlScheme = arguments.getString("fromUrlScheme");
                String notifyUrl = arguments.getString("notifyUrl");
                this.pay(tradeNo, subject, body, price, fromUrlScheme, notifyUrl);
                result = true;
                callbackContext.success();
            } catch (Exception e) {
                Log.e(TAG, "execute: Got JSON Exception " + e.getMessage());
                result = false;
                callbackContext.error(e.getMessage());
            }
        } else if (IS_WALLET_EXIST.equals(action)) {
            boolean isWalletExists = this.appInstalled("com.eg.android.AlipayGphone");
            result = true;
            callbackContext.success(isWalletExists ? "true" : "false");
        } else {
            result = false;
            Log.e(TAG, "Invalid action : " + action);
            callbackContext.error("Invalid action : " + action);
        }

        return result;
    }

    public void pay(String tradeNo, String subject, String body, String price, final String fromUrlScheme, String notifyUrl) {
        // 订单
        String orderInfo = createRequestParameters(subject, body, price, tradeNo, notifyUrl);

        // 对订单做RSA 签名
        String sign = sign(orderInfo);
        try {
            // 仅需对sign 做URL编码
            sign = URLEncoder.encode(sign, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // 完整的符合支付宝参数规范的订单信息
        final String payInfo = orderInfo + "&sign=\"" + sign + "\"&"
                + getSignType();

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                // 构造PayTask 对象
                PayTask alipay = new PayTask(cordova.getActivity());
                // 调用支付接口，获取支付结果
                String result = alipay.pay(payInfo);

                final PayResult payResult = new PayResult(result);

                // 支付宝返回此次支付结果及加签，建议对支付宝签名信息拿签约时支付宝提供的公钥做验签
                String resultInfo = payResult.getResult();

                final String resultStatus = payResult.getResultStatus();

                // 判断resultStatus 为“9000”则代表支付成功，具体状态码代表含义可参考接口文档
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        Activity activity = AliPayPlugin.this.cordova.getActivity();
                        if (TextUtils.equals(resultStatus, "9000")) {
                            Toast.makeText(activity, "支付成功, 刷新轿车列表看结果！",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            // 判断resultStatus 为非“9000”则代表可能支付失败
                            // “8000”代表支付结果因为支付渠道原因或者系统原因还在等待支付结果确认，最终交易是否成功以服务端异步通知为准（小概率状态）
                            if (TextUtils.equals(resultStatus, "8000")) {
                                Toast.makeText(activity, "支付结果确认中",
                                        Toast.LENGTH_SHORT).show();

                            } else {
                                // 其他值就可以判断为支付失败，包括用户主动取消支付，或者系统返回的错误
                                String errorMsg = payResult.getMemo();
                                if (TextUtils.isEmpty(errorMsg)) {
                                    errorMsg = "支付失败";
                                }
                                errorMsg += "。请确认手机已安装支付宝！";
                                Toast.makeText(activity, errorMsg,
                                        Toast.LENGTH_SHORT).show();

                            }
                        }
                    }
                });
            }
        });
    }


    /**
     * create the order info. 创建订单信息
     *
     */
    public String createRequestParameters(String subject, String body, String price, String tradeNo, String notifyUrl) {
        // 签约合作者身份ID
        String orderInfo = "partner=" + "\"" + partner + "\"";

        // 签约卖家支付宝账号
        orderInfo += "&seller_id=" + "\"" + seller + "\"";

        // 商户网站唯一订单号
        orderInfo += "&out_trade_no=" + "\"" + tradeNo + "\"";

        // 商品名称
        orderInfo += "&subject=" + "\"" + subject + "\"";

        // 商品详情
        orderInfo += "&body=" + "\"" + body + "\"";

        // 商品金额
        orderInfo += "&total_fee=" + "\"" + price + "\"";

        // 服务器异步通知页面路径
        orderInfo += "&notify_url=" + "\"" + notifyUrl
                + "\"";

        // 服务接口名称， 固定值
        orderInfo += "&service=\"mobile.securitypay.pay\"";

        // 支付类型， 固定值
        orderInfo += "&payment_type=\"1\"";

        // 参数编码， 固定值
        orderInfo += "&_input_charset=\"utf-8\"";

        // 设置未付款交易的超时时间
        // 默认30分钟，一旦超时，该笔交易就会自动被关闭。
        // 取值范围：1m～15d。
        // m-分钟，h-小时，d-天，1c-当天（无论交易何时创建，都在0点关闭）。
        // 该参数数值不接受小数点，如1.5h，可转换为90m。
        orderInfo += "&it_b_pay=\"30m\"";

        // extern_token为经过快登授权获取到的alipay_open_id,带上此参数用户将使用授权的账户进行支付
        // orderInfo += "&extern_token=" + "\"" + extern_token + "\"";

        // 支付宝处理完请求后，当前页面跳转到商户指定页面的路径，可空
        orderInfo += "&return_url=\"m.alipay.com\"";

        // 调用银行卡支付，需配置此参数，参与签名， 固定值 （需要签约《无线银行卡快捷支付》才能使用）
        // orderInfo += "&paymethod=\"expressGateway\"";

        return orderInfo;
    }

    /**
     * sign the order info. 对订单信息进行签名
     *
     * @param content
     *            待签名订单信息
     */
    public String sign(String content) {
        return SignUtils.sign(content, privateKey);
    }

    /**
     * get the sign type we use. 获取签名方式
     *
     */
    public String getSignType() {
        return "sign_type=\"RSA\"";
    }

    private boolean appInstalled(String uri) {
        Context ctx = this.cordova.getActivity().getApplicationContext();
        final PackageManager pm = ctx.getPackageManager();
        boolean app_installed = false;
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            app_installed = true;
        }
        catch(PackageManager.NameNotFoundException e) {
            app_installed = false;
        }
        return app_installed;
    }

    class PayResult {
        private String resultStatus;
        private String result;
        private String memo;

        public PayResult(String rawResult) {

            if (TextUtils.isEmpty(rawResult))
                return;

            String[] resultParams = rawResult.split(";");
            for (String resultParam : resultParams) {
                if (resultParam.startsWith("resultStatus")) {
                    resultStatus = gatValue(resultParam, "resultStatus");
                }
                if (resultParam.startsWith("result")) {
                    result = gatValue(resultParam, "result");
                }
                if (resultParam.startsWith("memo")) {
                    memo = gatValue(resultParam, "memo");
                }
            }
        }

        @Override
        public String toString() {
            return "resultStatus={" + resultStatus + "};memo={" + memo
                    + "};result={" + result + "}";
        }

        private String gatValue(String content, String key) {
            String prefix = key + "={";
            return content.substring(content.indexOf(prefix) + prefix.length(),
                    content.lastIndexOf("}"));
        }

        /**
         * @return the resultStatus
         */
        public String getResultStatus() {
            return resultStatus;
        }

        /**
         * @return the memo
         */
        public String getMemo() {
            return memo;
        }

        /**
         * @return the result
         */
        public String getResult() {
            return result;
        }
    }
}
