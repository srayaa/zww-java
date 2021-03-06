package com.bfei.icrane.api.service.impl;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bfei.icrane.api.service.BaseTest;

import cn.jiguang.common.ClientConfig;
import cn.jiguang.common.resp.APIConnectionException;
import cn.jiguang.common.resp.APIRequestException;
import cn.jpush.api.JPushClient;
import cn.jpush.api.push.PushResult;
import cn.jpush.api.push.model.Platform;
import cn.jpush.api.push.model.PushPayload;
import cn.jpush.api.push.model.audience.Audience;
import cn.jpush.api.push.model.notification.Notification;

public class JPushServiceImpl extends BaseTest {

    protected static final Logger LOG = LoggerFactory.getLogger(JPushServiceImpl.class);
    public static void main(String[] args) {
		
	
        ClientConfig clientConfig = ClientConfig.getInstance();
        JPushClient jpushClient = new JPushClient(MASTER_SECRET, APP_KEY);
        PushPayload payload = buildPushObject_all_all_alert();
        try {
            PushResult result = jpushClient.sendPush(payload);
            int status = result.getResponseCode();
            LOG.info("Got result - " + result);

        } catch (APIConnectionException e) {
            LOG.error("Connection error. Should retry later. ", e);
            LOG.error("Sendno: " + payload.getSendno());

        } catch (APIRequestException e) {
            LOG.error("Error response from JPush server. Should review and fix it. ", e);
            LOG.info("HTTP Status: " + e.getStatus());
            LOG.info("Error Code: " + e.getErrorCode());
            LOG.info("Error Message: " + e.getErrorMessage());
            LOG.info("Msg ID: " + e.getMsgId());
            LOG.error("Sendno: " + payload.getSendno());
        }
    }

//    @Test
//    public void testSendPushWithCallback() {
//        ClientConfig clientConfig = ClientConfig.getInstance();
//        String host = (String) clientConfig.get(ClientConfig.PUSH_HOST_NAME);
//        final NettyHttpClient client = new NettyHttpClient(ServiceHelper.getBasicAuthorization(APP_KEY, MASTER_SECRET),
//                null, clientConfig);
//        try {
//            URI uri = new URI(host + clientConfig.get(ClientConfig.PUSH_PATH));
//            PushPayload payload = buildPushObject_all_alias_alert();
//            client.sendRequest(HttpMethod.POST, payload.toString(), uri, new NettyHttpClient.BaseCallback() {
//                @Override
//                public void onSucceed(ResponseWrapper responseWrapper) {
//                    LOG.info("Got result: " + responseWrapper.responseContent);
//                }
//            });
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        }
//    }
//
//
    public static PushPayload buildPushObject_all_all_alert() {
        return PushPayload.alertAll(ALERT);
    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void test_invalid_json() {
//        PushClient pushClient = new PushClient(MASTER_SECRET, APP_KEY);
//
//        try {
//            pushClient.sendPush("{aaa:'a}");
//        } catch (APIConnectionException e) {
//            e.printStackTrace();
//        } catch (APIRequestException e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void test_empty_string() {
//        PushClient pushClient = new PushClient(MASTER_SECRET, APP_KEY);
//
//        try {
//            pushClient.sendPush("");
//        } catch (APIConnectionException e) {
//            e.printStackTrace();
//        } catch (APIRequestException e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void test_empty_password() {
//        new HttpProxy("127.0.0.1", 8080, "", null);
//    }
//
//    @Test
//    public void test_validate() {
//        PushClient pushClient = new PushClient(MASTER_SECRET, APP_KEY);
//
//        try {
//            PushResult result = pushClient.sendPushValidate(PushPayload.alertAll("alert"));
//            assertTrue("", result.isResultOK());
//        } catch (APIRequestException e) {
//            fail("Should not fail");
//        } catch (APIConnectionException e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Test
//    public void testGetCidList() {
//        JPushClient jPushClient = new JPushClient(MASTER_SECRET, APP_KEY);
//        try {
//            CIDResult result = jPushClient.getCidList(3, "push");
//            LOG.info("Got result - " + result);
//        } catch (APIConnectionException e) {
//            LOG.error("Connection error. Should retry later. ", e);
//        } catch (APIRequestException e) {
//            LOG.error("Error response from JPush server. Should review and fix it. ", e);
//            LOG.info("HTTP Status: " + e.getStatus());
//            LOG.info("Error Code: " + e.getErrorCode());
//            LOG.info("Error Message: " + e.getErrorMessage());
//        }
//    }
//
//    @Test
//    public void testSendPushWithCid() {
//        JPushClient jPushClient = new JPushClient(MASTER_SECRET, APP_KEY);
//        PushPayload pushPayload = buildPushObject_android_cid();
//        try {
//            PushResult result = jPushClient.sendPush(pushPayload);
//            LOG.info("Got result - " + result);
//        } catch (APIConnectionException e) {
//            LOG.error("Connection error. Should retry later. ", e);
//        } catch (APIRequestException e) {
//            LOG.error("Error response from JPush server. Should review and fix it. ", e);
//            LOG.info("HTTP Status: " + e.getStatus());
//            LOG.info("Error Code: " + e.getErrorCode());
//            LOG.info("Error Message: " + e.getErrorMessage());
//        }
//    }
//
//    public static PushPayload buildPushObject_android_cid() {
//        return PushPayload.newBuilder()
//                .setPlatform(Platform.android())
//                .setAudience(Audience.registrationId("18071adc030dcba91c0"))
//                .setNotification(Notification.alert(ALERT))
//                .setCid("d4ee2375846bc30fa51334f5-21f305e0-4a52-42f3-a3dd-d2e49bdf0499")
//                .build();
//    }

}
