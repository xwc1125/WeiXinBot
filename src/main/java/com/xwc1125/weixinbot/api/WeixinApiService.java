package com.xwc1125.weixinbot.api;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.xwc1125.weixinbot.Config;
import com.xwc1125.weixinbot.api.domain.BatchGetContactResp;
import com.xwc1125.weixinbot.api.domain.GetContactResp;
import com.xwc1125.weixinbot.api.domain.SendMsgResp;
import com.xwc1125.weixinbot.api.domain.SyncCheckResp;
import com.xwc1125.weixinbot.api.domain.UpdateChatRoomResp;
import com.xwc1125.weixinbot.api.domain.UploadmediaResp;
import com.xwc1125.weixinbot.api.domain.WebWxStatusNotifyResp;
import com.xwc1125.weixinbot.api.domain.WxInitResp;
import com.xwc1125.weixinbot.api.domain.WxSyncResp;
import com.xwc1125.weixinbot.utils.JSONUtil;
import com.xwc1125.weixinbot.utils.StringUtil;
import com.xwc1125.weixinbot.utils.Utils;
import com.xwc1125.weixinbot.utils.WxCookieJar;
import io.itit.itf.okhttp.FastHttpClient;
import io.itit.itf.okhttp.HttpClient;
import io.itit.itf.okhttp.PostRequest;
import io.itit.itf.okhttp.Response;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * @author skydu
 */
@Slf4j
public class WeixinApiService {
    // 微信登陆url
    public static final String API_URL = "https://login.weixin.qq.com";
    public String wxRootURL;//wx2.qq.com or wx.qq.com
    public String wxURL;//
    //
    public static WxCookieJar cookieJar = new WxCookieJar();
    //
    private HttpClient httpClient;
    //
    public static String LANG = "zh_CN";

    public static boolean isDebugEnabled = false;

    /**
     * Description: 基于OKhttp3的客户端
     * </p>
     *
     * @param
     * @return
     * @Author: xwc1125
     * @Date: 2019-03-03 22:08:11
     */
    public WeixinApiService() {
        httpClient = FastHttpClient.newBuilder().
                readTimeout(120, TimeUnit.SECONDS).
                connectTimeout(120, TimeUnit.SECONDS).
                cookieJar(cookieJar).build();
    }

    /**
     * Description: 通过微信接口获取登陆时所需的UUID
     * </p>
     *
     * @param
     * @return java.lang.String
     * @Author: xwc1125
     * @Date: 2019-03-03 22:08:22
     */
    public String getUUID() throws Exception {
        Response response = httpClient.get().
                url(API_URL + "/jslogin").
                addParams("appid", "wx782c26e4c19acffb").
                addParams("fun", "new").
                addParams("lang", LANG).
                addParams("_", String.valueOf(System.currentTimeMillis())).
                build().
                execute();
        String rsp = response.string();
        String uuid = rsp.substring(rsp.indexOf('"') + 1, rsp.lastIndexOf('"'));
        return uuid;
    }

    /***
     * Description: 获取二维码
     * </p>
     * @param uuid
     *
     * @return byte[]
     * @Author: xwc1125
     * @Date: 2019-03-03 22:09:28
     */
    public byte[] getQRCode(String uuid) throws Exception {
        Response response = FastHttpClient.
                get().
                url(API_URL + "/qrcode/" + uuid).
                addHeader("User-Agent", Config.USER_AGENT).
                build().
                execute();
        return response.bytes();
    }

    /**
     * Description: TODO
     * </p>
     *
     * @param uuid
     * @param tip
     * @param func
     * @return java.lang.String
     * @Author: xwc1125
     * @Date: 2019-03-03 22:09:46
     */
    public String login(String uuid, String tip, String func) throws Exception {
        String rspMsg = null;
        String url = API_URL + "/cgi-bin/mmwebwx-bin/login";
        long now = System.currentTimeMillis();
        rspMsg = httpClient.get().
                url(url).
                addParams("uuid", uuid).
                addParams("tip", tip).
                addParams("loginicon", "true").
                addParams("R", getR()).
                addParams("_", String.valueOf(now)).
                build().
                execute().
                string();
        //
        if (rspMsg.indexOf("window.code=200") != -1) {
            //success
            String[] content = rspMsg.split("\n");
            String regex = "window.redirect_uri=\"(\\S+)\";";
            String redirectUri = Utils.getMatchGroup0(regex, content[1]) + "";
            String tmpUrl = redirectUri.replaceAll("https://", "");
            wxRootURL = tmpUrl.substring(0, tmpUrl.indexOf("/")).trim();
            wxURL = redirectUri.substring(0, redirectUri.lastIndexOf("/"));
            //
            String xml = FastHttpClient.newBuilder().
                    cookieJar(cookieJar).
                    followRedirects(false).
                    followSslRedirects(false).
                    build().
                    get().
                    url(redirectUri).
                    build().
                    execute().
                    string();//login success
            log.debug("redirectUri:{} wxRootURL:{} wxURL:{} tmpUrl:{} xml:{}", redirectUri, wxRootURL, wxURL, xml);
            return xml;
        }
        throw new IllegalArgumentException(rspMsg);
    }

    /**
     * Description: 获取时间戳
     * </p>
     *
     * @param
     * @return java.lang.String
     * @Author: xwc1125
     * @Date: 2019-03-03 22:10:40
     */
    public String getR() {
        return System.currentTimeMillis() + "";
    }

    /**
     * Description: 获取时间戳
     * </p>
     *
     * @param
     * @return java.lang.String
     * @Author: xwc1125
     * @Date: 2019-03-03 22:11:01
     */
    public String getRr() {
        int now = (int) System.currentTimeMillis();
        return (~now) + "";
    }

    /**
     * Description: 初始化
     * </p>
     *
     * @param body
     * @return com.xwc1125.weixinbot.api.domain.WxInitResp
     * @Author: xwc1125
     * @Date: 2019-03-03 22:11:14
     */
    public WxInitResp wxInit(String body) throws Exception {
        String url = wxURL + "/webwxinit?r=" + getRr();
        return post(url, body, WxInitResp.class);
    }

    /***
     * Description: 获取联系人
     * </p>
     * @param passTicket
     * @param skey
     * @param body
     *
     * @return com.xwc1125.weixinbot.api.domain.GetContactResp
     * @Author: xwc1125
     * @Date: 2019-03-03 22:11:24
     */
    public GetContactResp getContact(String passTicket, String skey, String body) throws Exception {
        String url = String.format("%s/webwxgetcontact?seq=0&r=%s&skey=%s",
                wxURL,
                getR(),
                skey);
        return post(url, body, GetContactResp.class);
    }

    /***
     * Description: 批量获取联系人
     * </p>
     * @param body
     *
     * @return com.xwc1125.weixinbot.api.domain.BatchGetContactResp
     * @Author: xwc1125
     * @Date: 2019-03-03 22:11:54
     */
    public BatchGetContactResp batchGetContact(String body) throws Exception {
        String url = String.format("%s/webwxbatchgetcontact?type=%s&r=%s",
                wxURL,
                "ex",
                getR());
        return post(url, body, BatchGetContactResp.class);
    }

    /**
     * Description: TODO
     * </p>
     *
     * @param body
     * @return com.xwc1125.weixinbot.api.domain.WebWxStatusNotifyResp
     * @Author: xwc1125
     * @Date: 2019-03-03 22:12:03
     */
    public WebWxStatusNotifyResp wxSatusNotify(String body) throws Exception {
        String url = wxURL + "/webwxstatusnotify";
        return post(url, body, WebWxStatusNotifyResp.class);
    }

    /***
     * Description: 心跳包，与服务器同步并获取状态
     * </p>
     * @param sid
     * @param skey
     * @param uin
     * @param passTicket
     * @param syncKey
     * @param deviceId
     *
     * @return com.xwc1125.weixinbot.api.domain.SyncCheckResp
     * @Author: xwc1125
     * @Date: 2019-03-03 22:12:13
     */
    public SyncCheckResp syncCheck(String sid, String skey, String uin, String passTicket,
                                   String syncKey, String deviceId)
            throws Exception {
        SyncCheckResp rsp = null;
        Map<String, String> params = new HashMap<>();
        params.put("uin", uin);
        params.put("sid", urlEncode(sid));
        params.put("skey", urlEncode(skey));
        params.put("r", String.valueOf(System.currentTimeMillis()));
        params.put("synckey", urlEncode(syncKey));
        params.put("deviceid", "e" + StringUtil.randomNumbers(15));
        params.put("_", String.valueOf(System.currentTimeMillis()));
        String url = String.format("https://webpush.%s/cgi-bin/mmwebwx-bin/synccheck", wxRootURL);
        try {
            String json = FastHttpClient.newBuilder().
                    connectTimeout(30, TimeUnit.SECONDS).
                    readTimeout(30, TimeUnit.SECONDS).
                    writeTimeout(30, TimeUnit.SECONDS).
                    followSslRedirects(true).
                    build().
                    get().
                    url(url).
                    addHeader("cookie", WxCookieJar.cookieHeader()).
                    params(params).
                    addHeader("User-Agent", Config.USER_AGENT).
                    build().
                    execute().
                    string();
            if (isDebugEnabled) {
                log.info("syncCheck result:{}", json);
            }
            json = json.replace("window.synccheck=", "").
                    replace("retcode", "\"retcode\"").
                    replace("selector", "\"selector\"");
            rsp = JSONUtil.fromJson(json, SyncCheckResp.class);
        } finally {
            if (isDebugEnabled) {
                log.debug("syncCheck \nurl:{} sid:{} skey:{} syncKey:{} params:{} cookies:{}"
                                + "\njson:{} ",
                        url, sid, skey, syncKey, JSONUtil.dump(params),
                        WxCookieJar.cookieHeader(), JSONUtil.dump(rsp));
            }
        }
        return rsp;
    }

    /**
     * @param data
     * @return
     * @throws UnsupportedEncodingException
     */
    public String urlEncode(String data) throws UnsupportedEncodingException {
        if (data == null) {
            return null;
        }
        return URLEncoder.encode(data, "utf8");
    }

    /**
     * Description: 微信同步
     * </p>
     *
     * @param sid
     * @param skey
     * @param passTicket
     * @param body
     * @return com.xwc1125.weixinbot.api.domain.WxSyncResp
     * @Author: xwc1125
     * @Date: 2019-03-03 22:14:31
     */
    public WxSyncResp wxSync(String sid, String skey, String passTicket, String body) throws Exception {
        String url = String.format(wxURL + "/webwxsync?sid=%s&skey=%s",
                urlEncode(sid),
                urlEncode(skey));
        return post(url, body, WxSyncResp.class);
    }

    /***
     * Description: 发送文本消息
     * </p>
     * @param passTicket
     * @param body
     *
     * @return com.xwc1125.weixinbot.api.domain.SendMsgResp
     * @Author: xwc1125
     * @Date: 2019-03-03 22:14:37
     */
    public SendMsgResp sendMsg(String passTicket, String body) throws Exception {
        log.info("发送文本消息：passTicket=" + passTicket + "，body=" + body);
        String url = String.format(wxURL + "/webwxsendmsg?pass_ticket=%s&r=%s&lang=%s",
                passTicket, System.currentTimeMillis() + "", LANG);
        return post(url, body, SendMsgResp.class);
    }

    /***
     * Description: 发送图片(测试ok)
     * </p>
     * @param body
     *
     * @return com.xwc1125.weixinbot.api.domain.SendMsgResp
     * @Author: xwc1125
     * @Date: 2019-03-03 22:14:50
     */
    public SendMsgResp sendImgMsg(String body) throws Exception {
        log.info("发送图片：" + "body=" + body);
        String url = String.format(wxURL + "/webwxsendmsgimg?fun=async&f=json");
        return post(url, body, SendMsgResp.class);
    }

    /***
     * Description: 撤回消息
     * </p>
     * @param passTicket
     * @param body
     *
     * @return void
     * @Author: xwc1125
     * @Date: 2019-03-03 22:15:03
     */
    public void revokeMsg(String passTicket, String body) throws Exception {
        log.info("撤回消息：passTicket=" + passTicket + "，body=" + body);
        String url = String.format(wxURL + "/webwxrevokemsg?pass_ticket=%s&r=%s",
                passTicket,
                getR());
        post(url, body);
    }

    /***
     * Description: 同意加为好友
     * </p>
     * @param passTicket
     * @param body
     *
     * @return void
     * @Author: xwc1125
     * @Date: 2019-03-03 22:15:16
     */
    public void verifyUser(String passTicket, String body) throws Exception {
        log.info("同意加为好友：passTicket=" + passTicket + "，body=" + body);
        String url = String.format(wxURL + "/webwxverifyuser?" +
                        "pass_ticket=%s&r=%s&lang=%s",
                passTicket,
                System.currentTimeMillis() + "",
                LANG);
        post(url, body);
    }

    /***
     * Description: 创建群组
     * </p>
     * @param body
     *
     * @return void
     * @Author: xwc1125
     * @Date: 2019-03-03 22:15:28
     */
    public void createChatRoom(String body) throws Exception {
        log.info("创建群组：" + "body=" + body);
        String url = String.format(wxURL + "/webwxcreatechatroom?r=%s",
                System.currentTimeMillis() + "");
        post(url, body);
    }

    /***
     * Description: 群组添加成员
     * </p>
     * @param passTicket
     * @param body
     *
     * @return com.xwc1125.weixinbot.api.domain.UpdateChatRoomResp
     * @Author: xwc1125
     * @Date: 2019-03-03 22:15:48
     */
    public UpdateChatRoomResp updateChatRoom4AddMember(String passTicket, String body)
            throws Exception {
        String url = String.format(wxURL + "/webwxupdatechatroom?fun=%s&lang=%s&pass_ticket=%s",
                "addmember",
                LANG,
                urlEncode(passTicket));
        return post(url, body, UpdateChatRoomResp.class);
    }

    /***
     * Description: 上传图片
     * </p>
     * @param body
     * @param fileName
     * @param content
     *
     * @return com.xwc1125.weixinbot.api.domain.UploadmediaResp
     * @Author: xwc1125
     * @Date: 2019-03-03 22:15:54
     */
    @SuppressWarnings("deprecation")
    public UploadmediaResp uploadMedia(String body, String fileName, byte[] content) throws Exception {
        log.info("上传文件：fileName=" + fileName);
        UploadmediaResp rsp = null;
        String url = String.format("https://file.%s/cgi-bin/mmwebwx-bin/webwxuploadmedia?f=json", wxRootURL);
        try {
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            builder.addFormDataPart("id", "WU_FILE_1");
            builder.addFormDataPart("name", "image.png");
            builder.addFormDataPart("type", "image/png");
            builder.addFormDataPart("lastModifiedDate", new Date().toLocaleString());
            builder.addFormDataPart("mediatype", "pic");
            builder.addFormDataPart("uploadmediarequest", body);
            RequestBody fileBody = RequestBody.create(MediaType.parse(PostRequest.getMimeType(fileName)),
                    content);
            builder.addFormDataPart("filename", "image.png", fileBody);
            MultipartBody multipartBody = builder.build();
            String json = httpClient.post().
                    url(url).
                    addHeader("User-Agent", Config.USER_AGENT).
                    addHeader("Accept-Encoding", "gzip, deflate").
                    addHeader("Referer", "https://" + wxRootURL + "/").
                    multipartBody(multipartBody).
                    build().execute().string();
            rsp = JSONUtil.fromJson(json, UploadmediaResp.class);
            if (rsp.BaseResponse.Ret != 0) {
                throw new IllegalArgumentException("参数错误");
            }
            return rsp;
        } finally {
            if (isDebugEnabled) {
                log.debug("createChatroom url:{} body:{} json:{}",
                        url, body, JSONUtil.dump(rsp));
            }
        }
    }

    public byte[] getHeadimg(String headImg) throws Exception {
        String url = String.format("%s%s", "https://%s", headImg, wxRootURL);
        log.info("getHeadimg url:{}", url);
        return httpClient.get().url(url).build().execute().bytes();
    }

    /**
     *
     * @param url
     * @param body
     * @throws Exception
     */
    private void post(String url, String body) throws Exception {
        post(url, body, null);
    }

    /**
     *
     * @param url
     * @param body
     * @param clazz
     * @param <T>
     * @return
     * @throws Exception
     */
    private <T> T post(String url, String body, Class<?> clazz) throws Exception {
        String json = null;
        try {
            json = httpClient.post().
                    url(url).
                    addHeader("Content-type", "application/json; charset=utf-8").
                    addHeader("User-Agent", Config.USER_AGENT).
                    body(body).
                    build().
                    execute().
                    string();
            if (clazz == null) {
                return null;
            }
            return JSONUtil.fromJson(json, clazz);
        } finally {
            if (isDebugEnabled) {
                log.debug("post url:{} body:{} json:{}", url, body, json);
            }
        }
    }
}
