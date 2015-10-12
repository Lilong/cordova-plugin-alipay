#import "AlipayPlugin.h"
#import "Order.h"

#import "DataSigner.h"
#import <AlipaySDK/AlipaySDK.h>

@implementation AlipayPlugin

-(void)pluginInitialize{
    CDVViewController *viewController = (CDVViewController *)self.viewController;
    self.partner = [viewController.settings objectForKey:@"partner"];
    self.seller = [viewController.settings objectForKey:@"seller"];
    self.privateKey = [viewController.settings objectForKey:@"private_key"];
}

- (void) pay:(CDVInvokedUrlCommand*)command
{

    /*
     *商户的唯一的parnter和seller。
     *签约后，支付宝会为每个商户分配一个唯一的 parnter 和 seller。
     */

    //partner和seller获取失败,提示
    if ([self.partner length] == 0 ||
        [self.seller length] == 0 ||
        [self.privateKey length] == 0)
    {
        UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"提示"
                                                        message:@"缺少partner或者seller或者私钥。"
                                                       delegate:self
                                              cancelButtonTitle:@"确定"
                                              otherButtonTitles:nil];
        [alert show];
        return;
    }

    /*
     *生成订单信息及签名
     */

    //从API请求获取支付信息
    NSMutableDictionary *args = [command argumentAtIndex:0];
    NSString   *tradeId  = [args objectForKey:@"tradeNo"];
    NSString   *subject  = [args objectForKey:@"subject"];
    NSString   *body     = [args objectForKey:@"body"];
    NSString   *price    = [args objectForKey:@"price"];
    NSString   *fromUrlScheme    = [args objectForKey:@"fromUrlScheme"];
    NSString   *notifyUrl    = [args objectForKey:@"notifyUrl"];

    Order *order = [[Order alloc] init];
    order.partner = self.partner;
    order.seller = self.seller;
    order.tradeNO = tradeId; //订单ID（由商家自行制定）
    order.productName = subject; //商品标题
    order.productDescription = body; //商品描述
    order.amount = price; //商品价格
    order.notifyURL =  notifyUrl; //回调URL

    order.service = @"mobile.securitypay.pay";
    order.paymentType = @"1";
    order.inputCharset = @"utf-8";
    order.itBPay = @"30m";
    order.showUrl = @"m.alipay.com";

    //将商品信息拼接成字符串
    NSString *orderSpec = [order description];
    NSLog(@"orderSpec = %@",orderSpec);

    //获取私钥并将商户信息签名,外部商户可以根据情况存放私钥和签名,只需要遵循RSA签名规范,并将签名字符串base64编码和UrlEncode
    id<DataSigner> signer = CreateRSADataSigner(self.privateKey);
    NSString *signedString = [signer signString:orderSpec];

    //将签名成功字符串格式化为订单字符串,请严格按照该格式
    NSString *orderString = nil;
    if (signedString != nil) {
        orderString = [NSString stringWithFormat:@"%@&sign=\"%@\"&sign_type=\"%@\"",
                       orderSpec, signedString, @"RSA"];

        [[AlipaySDK defaultService] payOrder:orderString fromScheme:fromUrlScheme callback:^(NSDictionary *resultDic) {
            NSLog(@"reslut = %@",resultDic);
            CDVPluginResult* pluginResult = nil;
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:resultDic];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }];

    }
}

- (void) isWalletExist:(CDVInvokedUrlCommand*)command {

    CDVPluginResult* pluginResult = nil;

    NSString* scheme = @"alipay://";

    if ([[UIApplication sharedApplication] canOpenURL:[NSURL URLWithString:scheme]]) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"true"];
    }
    else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"false"];
    }

    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

#pragma mark "CDVPlugin Overrides"

- (void)handleOpenURL:(NSNotification *)notification
{
    NSURL* url = [notification object];

    if ([url.host isEqualToString:@"safepay"] || [url.host isEqualToString:@"alipay"]) {

        [[AlipaySDK defaultService] processOrderWithPaymentResult:url standbyCallback:^(NSDictionary *resultDic) {
            NSLog(@"result = %@",resultDic);
        }];
    }
}

@end
