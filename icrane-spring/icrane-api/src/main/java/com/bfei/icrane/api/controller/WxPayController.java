package com.bfei.icrane.api.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.bfei.icrane.api.service.*;
import com.bfei.icrane.common.util.*;
import com.bfei.icrane.core.models.Vip;
import com.bfei.icrane.core.models.WxPay;
import com.bfei.icrane.core.service.RiskManagementService;
import com.bfei.icrane.core.service.VipService;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import com.bfei.icrane.common.wx.utils.GetWxOrderno;
import com.bfei.icrane.common.wx.utils.RequestHandler;
import com.bfei.icrane.common.wx.utils.Sha1Util;
import com.bfei.icrane.common.wx.utils.TenpayUtil;
import com.bfei.icrane.common.wx.utils.WxConfig;
import com.bfei.icrane.core.models.ChargeRules;
import com.bfei.icrane.core.models.Member;
import com.bfei.icrane.core.service.ChargeOrderService;
import com.bfei.icrane.core.service.ValidateTokenService;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

/**
 * Author: perry Version: 1.0 Date: 2017/09/25 Description: 微信支付统一下单和回调.
 * Copyright (c) 2017 伴飞网络. All rights reserved.
 */
@Controller
@RequestMapping("/wx")
@CrossOrigin
public class WxPayController {
    private static final Logger logger = LoggerFactory.getLogger(WxPayController.class);
    private static PropFileManager propFileMgr = new PropFileManager("interface.properties");
    @Autowired
    private ValidateTokenService validateTokenService;
    @Autowired
    private LoginService loginService;
    @Autowired
    private ChargeOrderService chargeOrderService;//订单服务
    @Autowired
    private MemberService memberService;
    @Autowired
    private PayService payService;
    @Autowired
    private RiskManagementService riskManagementService;
    @Autowired
    private VipService vipService;


    private RedisUtil redisUtil = new RedisUtil();

    /**
     * 微信支付
     */
    @RequestMapping(value = "/pay", method = RequestMethod.POST)
    @ResponseBody
    public IcraneResult wxPay(HttpServletRequest request, int chargeruleid, Double price, int memberId,
                              String token, String IP, String packageName) throws Exception {
        logger.info("微信支付接口参数:规则id" + chargeruleid + ", price=" + price + ", memberId=" + memberId + ", token=" + token + "，IP" + IP);
        try {
            boolean isToken = validateTokenService.validataToken(token, memberId);
            if (isToken) {
                String orderNo = UUID.randomUUID().toString().replaceAll("-", "");
                //数据库 创建订单
                Vip vip = vipService.selectVipByMemberId(memberId);
                //总金额以分为单位，不带小数点
                //查询用户vip信息
                ChargeRules rule = chargeOrderService.queryRule(chargeruleid);
                Double dprice = rule.getChargePrice();
                if (vip != null) {
                    dprice = dprice * 10 * new BigDecimal(vip.getDiscount()).doubleValue();
                }
                if (dprice < 1) {
                    dprice = 1.0;
                }


                Integer result = chargeOrderService.createChareOrder(chargeruleid, dprice / 100, memberId, orderNo);


                if (result == -1) {
                    //return IcraneResult.build(Enviroment.RETURN_SUCCESS, "400", "超过限购次数");
                    return IcraneResult.build(Enviroment.RETURN_FAILE, Enviroment.RETURN_FAILE_CODE, "超过限购次数");
                } else if (result == -2) {
                    //return IcraneResult.build(Enviroment.RETURN_SUCCESS, "400", "你已购买了时长包");
                    return IcraneResult.build(Enviroment.RETURN_FAILE, Enviroment.RETURN_FAILE_CODE, "你已购买周卡或者月卡");
                }
                String currTime = TenpayUtil.getCurrTime();
                String strTime = currTime.substring(8, currTime.length());
                // 四位随机数
                String strRandom = TenpayUtil.buildRandom(4) + "";
                // 10位序列号,可以自行调整。
                String strReq = strTime + strRandom;
                // 设备号 非必输
                String device_info = "";
                // 随机数
                String nonce_str = strReq;
                String body;
                // 商品描述根据情况修改
                if ("xiaoyaojing".equals(packageName)) {
                    body = "小妖精抓抓乐";
                } else {
                    body = "网搜支付";
                }
                // 附加数据
                String attach = memberId + "";
                // 商户订单号
                String out_trade_no = orderNo;


                if (new BigDecimal(vip.getDiscount()).compareTo(new BigDecimal(10)) < 0) {
                    body = body + "-" + vip.getName() + vip.getDiscount() + "折";
                }
                price = rule.getChargePrice();
                Integer total_fee = dprice.intValue();// 正式环境下要*100
                // 订单生成的机器 IP
                String spbill_create_ip = request.getRemoteAddr();
                String notify_url = propFileMgr.getProperty("wx.notify");
                SortedMap<String, String> packageParams = new TreeMap<>();
                packageParams.put("trade_type", "APP");
                if ("xiaoyaojing".equals(packageName)) {
                    packageParams.put("appid", WxConfig.XYJAPPID);
                    packageParams.put("mch_id", WxConfig.XYJPARTNER);
                } else {
                    packageParams.put("appid", WxConfig.APPID);
                    packageParams.put("mch_id", WxConfig.PARTNER);
                }
                if (StringUtils.isNotEmpty(IP) && !"老子是公众号".equals(IP)) {
                    packageParams.put("trade_type", "MWEB");
                    //spbill_create_ip = IP;
                }
                packageParams.put("nonce_str", nonce_str);
                packageParams.put("body", body);
                packageParams.put("attach", attach);
                packageParams.put("out_trade_no", out_trade_no);
                packageParams.put("total_fee", String.valueOf(total_fee));
                packageParams.put("spbill_create_ip", spbill_create_ip);
                packageParams.put("notify_url", notify_url);
                RequestHandler reqHandler = new RequestHandler(null, null);
                if ("xiaoyaojing".equals(packageName)) {
                    reqHandler.init(packageParams.get("appid"), packageParams.get("mch_id"), WxConfig.XYJPARTNERKEY);
                } else {
                    reqHandler.init(packageParams.get("appid"), packageParams.get("mch_id"), WxConfig.PARTNERKEY);
                }
                String gzhopenId = memberService.selectGzhopenId(memberId);
                if ("老子是公众号".equals(IP) && StringUtils.isEmpty(gzhopenId)) {
                    return IcraneResult.build(Enviroment.RETURN_FAILE, Enviroment.RETURN_FAILE_CODE, "公众号支付接口获取预支付订单失败,请先关注网搜抓娃娃公众号");
                }
                if ("老子是公众号".equals(IP)) {
                    packageParams.put("openid", gzhopenId);
                    packageParams.put("trade_type", "JSAPI");
                    packageParams.put("appid", "wx42ac1f22ae0225f3");
                    packageParams.put("mch_id", "1493502502");
                    reqHandler.init("wx42ac1f22ae0225f3", "1493502502", WxConfig.PARTNERKEY);
                }

                String sign = reqHandler.createSign(packageParams);

                String xml = "<xml>" + "<appid>" + packageParams.get("appid") + "</appid>" + "<mch_id>" + packageParams.get("mch_id")
                        + "</mch_id>" + "<nonce_str>" + nonce_str + "</nonce_str>" + "<sign>" + sign + "</sign>"
                        + "<body><![CDATA[" + body + "]]></body>" + "<attach>" + attach + "</attach>" + "<out_trade_no>"
                        + out_trade_no + "</out_trade_no>" + "<total_fee>" + total_fee + "</total_fee>" + "<spbill_create_ip>" + spbill_create_ip
                        + "</spbill_create_ip>" + "<notify_url>" + notify_url + "</notify_url>" + "<trade_type>"
                        + packageParams.get("trade_type") + "</trade_type>";
                if ("老子是公众号".equals(IP)) {
                    xml = xml + "<openid>" + gzhopenId + "</openid>";

                }
                xml = xml + "</xml>";
                String createOrderURL = "https://api.mch.weixin.qq.com/pay/unifiedorder";
                Map map = new GetWxOrderno().getPayNo(createOrderURL, xml);
                String prepay_id = (String) map.get("prepay_id");
                if ("".equals(prepay_id)) {
                    chargeOrderService.orderFailure(orderNo);
                    return IcraneResult.build(Enviroment.RETURN_FAILE, Enviroment.RETURN_FAILE_CODE, "统一支付接口获取预支付订单出错");
                } else {
                    SortedMap<String, String> finalpackage = new TreeMap<String, String>();
                    String timestamp = Sha1Util.getTimeStamp();
                    String nonceStr2 = nonce_str;
                    if ("老子是公众号".equals(IP)) {
                        finalpackage.put("appId", "wx42ac1f22ae0225f3");
                        finalpackage.put("timeStamp", timestamp);
                        finalpackage.put("nonceStr", nonceStr2);
                        finalpackage.put("package", "prepay_id=" + prepay_id);
                        finalpackage.put("signType", "MD5");
                        //finalpackage.put("paySign", prepay_id);
                    } else if ("xiaoyaojing".equals(packageName)) {
                        finalpackage.put("appid", WxConfig.XYJAPPID);
                        finalpackage.put("partnerid", WxConfig.XYJPARTNER);
                        finalpackage.put("timestamp", timestamp);
                        finalpackage.put("noncestr", nonceStr2);
                        finalpackage.put("prepayid", prepay_id);
                        finalpackage.put("package", "Sign=WXPay");
                    } else {
                        finalpackage.put("appid", WxConfig.APPID);
                        finalpackage.put("partnerid", WxConfig.PARTNER);
                        finalpackage.put("timestamp", timestamp);
                        finalpackage.put("noncestr", nonceStr2);
                        finalpackage.put("prepayid", prepay_id);
                        finalpackage.put("package", "Sign=WXPay");
                    }
                    String finalsign = reqHandler.createSign(finalpackage);
                    WxPay wxPay = new WxPay();
                    wxPay.setPrepayId(prepay_id);
                    wxPay.setMwebUrl((String) map.get("mweb_url"));
                    wxPay.setTimeStamp(timestamp);
                    wxPay.setNonceStr(nonceStr2);
                    wxPay.setPaySign(finalsign);
                    wxPay.setOutTradeNo(orderNo);
                    logger.info("微信支付统一下单: wxPay=" + wxPay);
                    return IcraneResult.ok(wxPay);
                }
            } else {
                return IcraneResult.build(Enviroment.RETURN_FAILE, "400", "token已失效");
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 支付回调
     */
    @RequestMapping("/notify")
    @ResponseBody
    public String notify(HttpServletRequest request) throws Exception {
        return payService.wxNotify(request);
    }

    /**
     * 微信登录接口
     *
     * @param code          微信登录code
     * @param lastLoginFrom 登陆时间
     * @param channel       渠道
     * @param phoneModel    用户手机型号
     * @param head          H5标记
     * @return 登录结果
     */
    @RequestMapping(value = "/getAccessToken", method = RequestMethod.POST)
    @ResponseBody
    public IcraneResult getAccessToken(HttpServletRequest request, String code, String lastLoginFrom, String channel, String phoneModel, String head, String unionId, String openId, String accessToken, String IMEI) {
        logger.info("微信登录code=" + code + ",lastLoginFrom=" + lastLoginFrom + ",channel=" + channel + ",phoneModel=" + phoneModel + ",head=" + head + ",unionId=" + unionId + ",openId=" + openId + ",accessToken" + accessToken + ",IMEI=" + IMEI);
        //检测code
        /*if (StringUtils.isEmpty(code)) {
            logger.info("微信登录异常:code为空");
            return IcraneResult.build(Enviroment.RETURN_FAILE, Enviroment.RETURN_FAILE_CODE, Enviroment.CODE_IS_NULL);
        }*/
        try {
            JSONObject object = null;
            if (StringUtils.isEmpty(unionId) && StringUtils.isEmpty(openId) && StringUtils.isEmpty(accessToken) && StringUtils.isNotEmpty(code)) {
                // 通过 code 获得 accessToken unionId
                String state = redisUtil.getString(code);
                if (state != null) {
                    return IcraneResult.build(Enviroment.RETURN_SUCCESS, Enviroment.RETURN_SUCCESS_REAPEAT, Enviroment.CODE_REPEAT);
                }
                String result = WXUtil.getOauthInfo(code, head);
                if (result != null) {
                    redisUtil.setString(code, result, 60);
                    object = JSONObject.fromObject(result);
                    if ("老子是小程序".equals(head)) {
                        accessToken = object.getString("session_key");
                    } else {
                        accessToken = object.getString("access_token");
                    }
                    redisUtil.setString("accessToken" + code, accessToken, 7200);
                    unionId = object.getString("unionid");
                    //unionId登录兼容问题
                    openId = memberService.selectOpenIdByUnionId(unionId);
                    if (StringUtils.isEmpty(openId)) {
                        openId = object.getString("openid");
                    }
                }
            } else if (StringUtils.isNotEmpty(unionId) && StringUtils.isNotEmpty(openId) && StringUtils.isNotEmpty(accessToken) && StringUtils.isEmpty(code)) {
                String s = memberService.selectOpenIdByUnionId(unionId);
                if (StringUtils.isNotEmpty(s)) {
                    openId = s;
                }
            } else {
                logger.info("微信登录异常:参数异常");
                return IcraneResult.build(Enviroment.RETURN_FAILE, Enviroment.RETURN_FAILE_CODE, "参数异常");
            }
            //根据openId获取登录信息
            Member member = memberService.selectByOpenId(openId);
            logger.info("根据微信OpenId查询用户是否存在:{}", member);
            if (member == null) {
                //新用户先注册
                if (StringUtils.isEmpty(IMEI) || "IMEI".equals(IMEI) || riskManagementService.selectIMEICount(IMEI) < 3) {
                    member = loginService.wxRegistered(openId, channel, phoneModel, accessToken, lastLoginFrom, unionId);
                } else {
                    return IcraneResult.build(Enviroment.RETURN_FAILE, "407", "该设备超过注册上限");
                }
                if (member == null) {
                    IcraneResult.build(Enviroment.RETURN_FAILE, Enviroment.RETURN_FAILE_CODE, Enviroment.REGISTRATION_FAILED);
                }
            } else if (member != null && StringUtils.isEmpty(memberService.selectOpenIdByUnionId(unionId))) {
                memberService.insertUnionId(member.getId(), openId, unionId);
            }
            //老用户直接登录
            //登录前记录IMEI和IP]
            if (IMEI != null && IMEI.length() > 40) {
                return IcraneResult.build(Enviroment.RETURN_FAILE, Enviroment.ERROR_CODE, Enviroment.IMEI_TO_LONG);
            }
            int register = riskManagementService.register(member.getId(), IMEI, HttpClientUtil.getIpAdrress(request));
            if (register != 1) {
                return IcraneResult.build(Enviroment.RETURN_FAILE, Enviroment.ERROR_CODE, Enviroment.RISK_CONTROL_ABNORMAL);
            }
            return loginService.wxLogin(member, lastLoginFrom, channel, phoneModel);
        } catch (IOException e) {
            e.printStackTrace();
            logger.info(e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            logger.info(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            logger.info(e.getMessage());

        }
        return IcraneResult.build(Enviroment.RETURN_FAILE, Enviroment.RETURN_FAILE_CODE, Enviroment.CODE_IS_NULL);
    }

    @RequestMapping(value = "/checkAccessToken", method = RequestMethod.POST)
    @ResponseBody
    public IcraneResult getAccessToken(String accessToken, String refreshToken, String openId) throws Exception {
        logger.info("checkAccessToken参数accessToken=" + accessToken + "," + "refreshToken=" + refreshToken + ","
                + "openId=" + openId);
        String url = "https://api.weixin.qq.com/sns/auth?access_token=" + accessToken + "&openid=" + openId;
        URI uri = URI.create(url);
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet(uri);
        HttpResponse response;
        try {
            response = client.execute(get);
            if (response.getStatusLine().getStatusCode() == 200) {
                HttpEntity entity = response.getEntity();

                BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"));
                StringBuilder sb = new StringBuilder();

                for (String temp = reader.readLine(); temp != null; temp = reader.readLine()) {
                    sb.append(temp);
                }
                JSONObject object = JSONObject.fromObject(sb.toString().trim());
                int errorCode = object.getInt("errcode");
                if (errorCode == 0) {
                    return IcraneResult.ok();
                }
            }
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            logger.error(e.getMessage());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            logger.error(e.getMessage());
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            logger.error(e.getMessage());
        }
        return IcraneResult.build(Enviroment.RETURN_FAILE, Enviroment.RETURN_FAILE_CODE, "accessToken无效");
    }

    /**
     * 调用微信JS接口的临时票据
     *
     * @return jsapiTicket
     */
    @RequestMapping(value = "/jsapiTicket", method = RequestMethod.POST)
    @ResponseBody
    public ResultMap jsapiTicket() {
        WXUtil wxUtil = new WXUtil();
        String jsApiTicket = wxUtil.getJSApiTicket();
        ResultMap resultMap = new ResultMap("操作成功");
        resultMap.setResultData(jsApiTicket);
        return resultMap;
    }

    /**
     * 分享接口
     *
     * @return jsapiTicket
     */
    @RequestMapping(value = "/onMenuShareTimeline", method = RequestMethod.POST)
    @ResponseBody
    public ResultMap onMenuShareTimeline(String url) {
        String currTime = TenpayUtil.getCurrTime();
        //随机字符串
        String noncestr = currTime.substring(8, currTime.length()) + TenpayUtil.buildRandom(4);
        //有效的jsapi_ticket
        WXUtil wxUtil = new WXUtil();
        String jsapi_ticket = wxUtil.getJSApiTicket();
        //timestamp（时间戳）
        String timestamp = Sha1Util.getTimeStamp();
        SortedMap<String, String> packageParams = new TreeMap<>();
        packageParams.put("noncestr", noncestr);
        packageParams.put("jsapi_ticket", jsapi_ticket);
        packageParams.put("timestamp", timestamp);
        packageParams.put("url", url);
        RequestHandler reqHandler = new RequestHandler(null, null);
        String sign = reqHandler.createSha1Sign(packageParams);
        packageParams.put("sign", sign);
        packageParams.put("appId", WxConfig.GZHAPPID);
        return new ResultMap("", packageParams);
    }


    /**
     * 多级渠道注册
     *
     * @return jsapiTicket
     */
    /*@RequestMapping(value = "/weChatOfficialAccountsLogin", method = RequestMethod.POST)
    @ResponseBody
    public ResultMap onMenuShareTimeline(HttpServletRequest request, String code, String memberId, String lastLoginFrom, String IMEI, String phoneModel, String channel, String head) {

        return loginService.weChatLogin(request, code, "", memberId, lastLoginFrom, IMEI, phoneModel, channel);
    }*/
}