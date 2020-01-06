package org.airasia.app;

import com.alibaba.fastjson.JSON;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.*;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

abstract class AirasiaTest {
    private static final Integer CONNECTION_TIMEOUT = 8000;
    private static final Integer SO_TIMEOUT = 50000;

    private static SSLConnectionSocketFactory sslConnectionSocketFactory = getSslConnectionSocketFactory();

    //响应处理器
    private static ResponseHandler<String> RESPONSE_HANDLER = new AbstractResponseHandler<String>() {
        @Override
        public String handleEntity(HttpEntity entity) throws IOException {
            return EntityUtils.toString(entity);
        }
    };

    public static RequestConfig requestConfig() {
        return requestConfig(null);
    }

    public static RequestConfig requestConfig(HttpHost proxy) {
        return requestConfig(SO_TIMEOUT, proxy);
    }

    public static HttpClientContext httpClientContext() {
        HttpClientContext httpContext = HttpClientContext.adapt(new BasicHttpContext());
        BasicCookieStore cookieStore = new BasicCookieStore();
        httpContext.setCookieStore(cookieStore);
        return httpContext;
    }

    public static RequestConfig requestConfig(int socketTimeout, HttpHost proxy) {
        return RequestConfig.custom()
                .setConnectTimeout(CONNECTION_TIMEOUT)
                .setConnectionRequestTimeout(CONNECTION_TIMEOUT)
                .setSocketTimeout(socketTimeout)
                .setProxy(proxy)
                .build();
    }


    public static CloseableHttpClient getHttpClient() {
        return getHttpClient(null);
    }

    public static CloseableHttpClient getHttpClient(CredentialsProvider credentialsProvider) {
        final HttpClientBuilder httpClientBuilder = HttpClients.custom()
                .setSSLSocketFactory(sslConnectionSocketFactory);
        if (credentialsProvider != null) {
            httpClientBuilder
                    .setDefaultCredentialsProvider(credentialsProvider);
        }
        return httpClientBuilder.build();
    }

    private static SSLConnectionSocketFactory getSslConnectionSocketFactory() {
        try {
            SSLContext sslcontext = buildSSLContext();
            return new SSLConnectionSocketFactory(sslcontext, new String[]{"TLSv1.2"}, null, NoopHostnameVerifier.INSTANCE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static SSLContext buildSSLContext() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        return SSLContexts.custom().setSecureRandom(new SecureRandom()).loadTrustMaterial(null, new TrustStrategy() {
            public boolean isTrusted(X509Certificate[] chain, String authType) {
                return true;
            }
        }).build();
    }

    public static Map getWToken(String signServerURL, String input) throws Exception {
        CloseableHttpClient httpClient = getHttpClient();
        HttpPost httpPost = new HttpPost(signServerURL);
        httpPost.setConfig(requestConfig());
        Map<String, String> param = new HashMap<>();
        param.put("data", input);
        String json = JSON.toJSONString(param);
        httpPost.setHeader("content-type", "application/json");
        httpPost.setEntity(new ByteArrayEntity(json.getBytes()));
        String rs;
        // 执行请求
        try {
            rs = httpClient.execute(httpPost, RESPONSE_HANDLER);
            return JSON.parseObject(rs, Map.class);
        } finally {
            httpPost.releaseConnection();
        }
    }

    private static void applyBaseParams(RequestParams requestParams, String deviceID) {
        requestParams.put("requestFrom", "0");
        requestParams.put("deviceID", deviceID);
        requestParams.put("osVersion", "22");
        requestParams.put("deviceModel", "");
        requestParams.put("deviceBrand", "");

        requestParams.put("userCurrencyCode", "CNY");
        requestParams.put("cultureCode", "zh-CN");
        requestParams.put("appVersion", "2699");
        requestParams.put("operatingSystem", "android");
        requestParams.put("password", "2565028E5E329622C5B4F9B7D21CFA051DD7F1DDEF387EF7871F55197B34525B");
        requestParams.put("username", "OneAirAsia_MOBILE");
        requestParams.put("hashKey", ConstantHelper.convertToHash(requestParams.toString()));
    }

    public static byte[] createFlightSearchData(String userSession, String deviceID, int adultPax, int childPax, int infantPax, String departureStation, String arrivalStation, String departureDate, String returnDate) throws Exception {
        RequestParams requestParams = new RequestParams();
        requestParams.put("actionCode", "302");
        requestParams.put("adultPax", "" + adultPax);
        requestParams.put("childPax", "" + childPax);
        requestParams.put("infantPax", "" + infantPax);
        requestParams.put("departureStation", departureStation);
        requestParams.put("arrivalStation", arrivalStation);
        requestParams.put("departureDate", departureDate);
        requestParams.put("returnDate", returnDate);
        applyBaseParams(requestParams, deviceID);
        String data = MCrypt.encryptBase64String(requestParams.toString());
        return ("data=" + URLEncoder.encode(data) + "&userSession=" + URLEncoder.encode(userSession)).getBytes();
    }

    public static byte[] createUserSessionData(String deviceID) throws Exception {
        RequestParams requestParams = new RequestParams();
        requestParams.put("actionCode", "103");
        requestParams.put("requestType", "1");
        applyBaseParams(requestParams, deviceID);
        String data = MCrypt.encryptBase64String(requestParams.toString());
        return ("data=" + URLEncoder.encode(data)).getBytes();
    }

    public static String requestSession(String wTokenServerURL, String deviceID) throws Exception {
        byte[] reqParams = createUserSessionData(deviceID);
        String rs = request(wTokenServerURL, "103", deviceID, reqParams);
        System.out.println("103 rs---> " + rs);
        return (String) ((Map) (JSON.parseObject(rs).get("data"))).get("userSession");
    }

    public static String requestFlightSearch(String wTokenServerURL, String userSession, String deviceID, int adultPax, int childPax, int infantPax, String departureStation, String arrivalStation, String departureDate, String returnDate) throws Exception {
        byte[] reqParams = createFlightSearchData(userSession, deviceID, adultPax, childPax, infantPax, departureStation, arrivalStation, departureDate, returnDate);
        return request(wTokenServerURL, "302", deviceID, reqParams);
    }

    private static String request(String wTokenServerURL, String actionCode, String deviceID, byte[] reqParams) throws Exception {
        RequestConfig config = requestConfig();
        CloseableHttpClient httpClient = getHttpClient();
        HttpClientContext context = httpClientContext();

        Map result = getWToken(wTokenServerURL, Base64.encodeBase64String(reqParams));

        String wToken = (String) result.get("wToken");
        HttpPost httpPost = new HttpPost("https://acmesec.airasia.com/api/apps.php?ad=" + actionCode + "&deviceId=" + deviceID);
        httpPost.setEntity(new ByteArrayEntity(reqParams));
        httpPost.setHeader("User-Agent", "AirAsiaMobile/10.1.1 (Linux; U; Android 0.5; en-us)");
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        httpPost.setHeader("wToken", wToken);
        httpPost.setConfig(config);
        String rs;
        // 执行请求
        try {
            rs = httpClient.execute(httpPost, RESPONSE_HANDLER, context);
            return MCrypt.decryptBase64(rs);
        } finally {
            httpPost.releaseConnection();
        }
    }

    public static void main(String[] args) throws Exception {
        //deviceID 16位十六进制数
        String deviceID = RandomStringUtils.random(16, "0123456789abcdef");

        //wToken计算服务地址
        String wTokenServerURL = "http://192.168.3.5:4778/wToken";

        String userSession = requestSession(wTokenServerURL, deviceID);
        System.out.println("userSession:" + userSession);

        int adultPax = 1;
        int childPax = 0;
        int infantPax = 0;
        String departureStation = "DMK";
        String arrivalStation = "WUH";
        String departureDate = DateFormatUtils.format(DateUtils.addDays(new Date(), 2), "yyyy-MM-dd");
        String returnDate = "";

        String rate = requestFlightSearch(wTokenServerURL, userSession, deviceID, adultPax, childPax, infantPax, departureStation, arrivalStation, departureDate, returnDate);
        System.out.println("302 rs ---> " + rate);
    }

}
