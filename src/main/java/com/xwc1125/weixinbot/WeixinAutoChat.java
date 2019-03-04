package com.xwc1125.weixinbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.xwc1125.weixinbot.api.WeixinApiService;
import com.xwc1125.weixinbot.api.domain.BaseRequest;
import com.xwc1125.weixinbot.api.domain.BatchGetContactResp;
import com.xwc1125.weixinbot.api.domain.Contact;
import com.xwc1125.weixinbot.api.domain.GetContactResp;
import com.xwc1125.weixinbot.api.domain.Message;
import com.xwc1125.weixinbot.api.domain.SendMsg;
import com.xwc1125.weixinbot.api.domain.SyncCheckResp;
import com.xwc1125.weixinbot.api.domain.SyncKey;
import com.xwc1125.weixinbot.api.domain.SyncKey.KeyValue;
import com.xwc1125.weixinbot.api.domain.UpdateChatRoomResp;
import com.xwc1125.weixinbot.api.domain.UploadmediaResp;
import com.xwc1125.weixinbot.api.domain.WxInitResp;
import com.xwc1125.weixinbot.api.domain.WxSyncResp;
import com.xwc1125.weixinbot.threadpool.ThreadPool;
import com.xwc1125.weixinbot.utils.FileUtil;
import com.xwc1125.weixinbot.utils.JSONUtil;
import com.xwc1125.weixinbot.utils.QRCodeUtil;
import com.xwc1125.weixinbot.utils.XmlUtil;

/**
 * @author skydu
 */
@Slf4j
public class WeixinAutoChat {
    //
    static {
        System.setProperty("jsse.enableSNIExtension", "false");
    }

    //
    //
    private String uuid;
    public WeixinApiService apiService;
    public BaseRequest baseRequest;
    public WxInitResp wxInitResp;
    public GetContactResp getContactResp;
    public String passTicket;
    public ThreadPool threadPool;
    private WeixinCallback callback;
    private SyncKey syncKey;
    private ScheduledFuture<?> startReceiveFuture;
    private Map<String, Contact> contacts;

    /**
     * Description: 构造函数
     * </p>
     *
     * @param callback
     * @return
     * @Author: xwc1125
     * @Date: 2019-03-03 22:24:39
     */
    public WeixinAutoChat(WeixinCallback callback) {
        apiService = new WeixinApiService();
        this.callback = callback;
        threadPool = new ThreadPool();
        contacts = new ConcurrentHashMap<>();
        threadPool.start();
    }

    /**
     * Description:
     * </p>
     *
     * @Author: xwc1125
     * @Date: 2019-03-03 22:26:54
     * @Copyright Copyright@2019
     */
    public interface WeixinCallback {
        void onLogin();

        void onReceiveMsg(List<Message> msgs);
    }

    /**
     * Description:登陆
     * </p>
     *
     * @param
     * @return void
     * @Author: xwc1125
     * @Date: 2019-03-03 22:27:13
     */
    public void login() throws Exception {
        try {
            uuid = apiService.getUUID();
            byte[] content = apiService.getQRCode(uuid);
            FileUtil.save(Config.qrCodePath, content);
            QRCodeUtil.showQrcode(Config.qrCodePath);
            threadPool.executeThreadWorker(this::startLoginThread);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException("获取登录二维码失败");
        }
    }

    /**
     * Description: 启动登陆的线程
     * </p>
     *
     * @param
     * @return void
     * @Author: xwc1125
     * @Date: 2019-03-03 22:28:09
     */
    private void startLoginThread() {
        String loginRsp = "";
        log.info("1.startLoginThread");
        //check login
        while (true) {
            try {
                loginRsp = apiService.login(uuid, "0", null);
                break;
            } catch (Exception e) {
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }
        log.info("2.登录成功");
        try {
            Map<String, String> map = XmlUtil.parseXmlMessage(loginRsp);
            baseRequest = new BaseRequest();
            baseRequest.Sid = map.get("wxsid");
            baseRequest.Skey = map.get("skey");
            baseRequest.Uin = map.get("wxuin");
            passTicket = map.get("pass_ticket");
            wxInitResp = apiService.wxInit(getBaseRequestParas());
            wxInitResp.ContactList.forEach((c) -> contacts.put(c.UserName, c));
            log.info("3.初始化成功");
            syncKey = wxInitResp.SyncKey;
            getContactResp = apiService.getContact(passTicket, baseRequest.Skey, getBaseRequestParas());
            getContactResp.MemberList.forEach((c) -> contacts.put(c.UserName, c));
            log.info("4.获取联系人列表成功");
            apiService.wxSatusNotify(getWxSatusNotifyBody());
            //
            log.info("5.开启微信状态通知成功");
            //
            startReceiveFuture = threadPool.scheduleAtFixedRate(this::startReceive, 1, 5, TimeUnit.SECONDS);
            //
            if (callback != null) {
                callback.onLogin();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    //
    private Map<String, Object> createBody() {
        Map<String, Object> map = new HashMap<>();
        baseRequest.randomDeviceID();
        map.put("BaseRequest", baseRequest);
        return map;
    }

    /**
     * Description: 批量获取联系人
     * </p>
     *
     * @param chatRoomUserNames
     * @return void
     * @Author: xwc1125
     * @Date: 2019-03-03 22:30:51
     */
    public void batchGetContact(List<String> chatRoomUserNames) throws Exception {
        Map<String, Object> map = createBody();
        map.put("Count", chatRoomUserNames.size());
        List<Map<String, String>> list = new ArrayList<>();
        for (String userName : chatRoomUserNames) {
            Map<String, String> member = new HashMap<>();
            member.put("UserName", userName);
            member.put("EncryChatRoomId", "");
            list.add(member);
        }
        map.put("List", list);
        BatchGetContactResp resp = apiService.batchGetContact(JSONUtil.toJson(map));
        if (resp.BaseResponse.Ret == 0 && resp.ContactList != null) {
            resp.ContactList.forEach((c) -> contacts.put(c.UserName, c));
        }

    }

    //
    private String getWxSatusNotifyBody() {
        Map<String, Object> map = createBody();
        map.put("Code", 3);
        map.put("FromUserName", wxInitResp.User.UserName);
        map.put("ToUserName", wxInitResp.User.UserName);
        map.put("ClientMsgId", System.currentTimeMillis() + "");
        return JSONUtil.toJson(map);
    }

    //
    private String getBaseRequestParas() {
        Map<String, Object> map = new HashMap<>();
        map.put("BaseRequest", baseRequest);
        return JSONUtil.toJson(map);
    }

    /**
     * Description: 开始接收
     * </p>
     *
     * @param
     * @return void
     * @Author: xwc1125
     * @Date: 2019-03-03 22:31:31
     */
    private void startReceive() {
        Map<String, Object> map = createBody();
        map.put("SyncKey", wxInitResp.SyncKey);
        map.put("rr", apiService.getRr());
        try {
            SyncCheckResp resp = apiService.syncCheck(baseRequest.Sid, baseRequest.Skey, baseRequest.Uin,
                    passTicket, createSyncKey(syncKey), baseRequest.DeviceID);
            if (resp.retcode != SyncCheckResp.RETCODE_正常) {
                if (resp.retcode == SyncCheckResp.RETCODE_退出) {
                    log.info("退出");
                    startReceiveFuture.cancel(true);
                } else if (resp.retcode == SyncCheckResp.RETCODE_移动端退出) {
                    log.info("移动端退出");
                    startReceiveFuture.cancel(true);
                } else if (resp.retcode == SyncCheckResp.RETCODE_其它地方登陆) {
                    log.info("其它地方登陆");
                    startReceiveFuture.cancel(true);
                } else if (resp.retcode == SyncCheckResp.RETCODE_未知错误) {
                    log.info("未知错误");
                }
                return;
            } else {
                if (resp.selector != 0) {
                    //说明有新消息
                    WxSyncResp syncResp = apiService.wxSync(baseRequest.Sid, baseRequest.Skey,
                            passTicket, JSONUtil.toJson(map));
                    if (resp.selector == 2) {
                        //新消息
                        callback.onReceiveMsg(syncResp.AddMsgList);
                    }
                    syncKey = syncResp.SyncCheckKey;
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    //
    private String createSyncKey(SyncKey key) throws Exception {
        StringBuilder skey = new StringBuilder();
        for (KeyValue kv : key.List) {
            skey.append(kv.Key).append("_").append(kv.Val).append("|");
        }
        skey.deleteCharAt(skey.length() - 1);
        return skey.toString();
    }

    /**
     * Description: 发送消息
     * </p>
     *
     * @param msg
     * @return void
     * @Author: xwc1125
     * @Date: 2019-03-03 22:32:16
     */
    public void sendMsg(SendMsg msg) throws Exception {
        Map<String, Object> map = createBody();
        msg.FromUserName = wxInitResp.User.UserName;
        map.put("Msg", msg);
        map.put("Scene", 0);
        if (msg.Type == Message.TYPE_文本消息) {
            apiService.sendMsg(passTicket, JSONUtil.toJson(map));
        } else if (msg.Type == Message.TYPE_图片消息) {
            apiService.sendImgMsg(JSONUtil.toJson(map));
        }
    }

    /**
     * Description: 撤销
     * </p>
     *
     * @param msgId
     * @param toUserName
     * @return void
     * @Author: xwc1125
     * @Date: 2019-03-03 22:32:30
     */
    public void revokeMsg(String msgId, String toUserName) throws Exception {
        Map<String, Object> map = createBody();
        map.put("SvrMsgId", msgId);
        map.put("ToUserName", toUserName);
        map.put("ClientMsgId", System.currentTimeMillis());
        apiService.revokeMsg(passTicket, JSONUtil.toJson(map));
        log.debug("revokeMsg msg:{}", JSONUtil.dump(map));
    }

    /**
     * Description: 认证用户
     * </p>
     *
     * @param fromUserName
     * @param verifyUserTicket
     * @return void
     * @Author: xwc1125
     * @Date: 2019-03-03 22:32:42
     */
    public void verifyUser(String fromUserName, String verifyUserTicket) throws Exception {
        Map<String, Object> map = createBody();
        map.put("Opcode", 3);
        map.put("VerifyUserListSize", 1);
        Map<String, String> VerifyUserList = new HashMap<>();
        VerifyUserList.put("Value", fromUserName);
        VerifyUserList.put("VerifyUserTicket", verifyUserTicket);
        map.put("VerifyUserList", Arrays.asList(VerifyUserList));
        map.put("VerifyContent", "");
        map.put("SceneListCount", 1);
        map.put("SceneList", Arrays.asList(33));
        map.put("skey", baseRequest.Skey);
        apiService.verifyUser(passTicket, JSONUtil.toJson(map));
    }

    /**
     * Description: 创建房间
     * </p>
     *
     * @param topic
     * @param contacts
     * @return void
     * @Author: xwc1125
     * @Date: 2019-03-03 22:32:54
     */
    public void createChatRoom(String topic, List<Contact> contacts) throws Exception {
        if (contacts == null || contacts.size() < 2) {
            throw new IllegalAccessException("参数错误");
        }
        Map<String, Object> map = createBody();
        map.put("MemberCount", contacts.size());
        List<Map<String, String>> contactList = new ArrayList<>();
        for (Contact c : contacts) {
            Map<String, String> contact = new HashMap<>();
            contact.put("UserName", c.UserName);
            contactList.add(contact);
        }
        map.put("MemberList", contactList);
        map.put("Topic", topic);
        apiService.createChatRoom(JSONUtil.toJson(map));
    }

    /**
     * Description: 群组添加成员
     * </p>
     *
     * @param addMemberList
     * @param chatRoomName
     * @return com.xwc1125.weixinbot.api.domain.UpdateChatRoomResp
     * @Author: xwc1125
     * @Date: 2019-03-03 22:33:02
     */
    public UpdateChatRoomResp updateChatRoom4AddMember(List<String> addMemberList, String chatRoomName)
            throws Exception {
        Map<String, Object> map = createBody();
        StringBuilder memberList = new StringBuilder();
        for (String member : addMemberList) {
            memberList.append(member).append(",");
        }
        memberList.deleteCharAt(memberList.length() - 1);
        map.put("AddMemberList", memberList);
        map.put("ChatRoomName", chatRoomName);
        return apiService.updateChatRoom4AddMember(passTicket, JSONUtil.toJson(map));
    }

    /***
     * Description: 发送图片 
     * </p>
     * @param toUserName
     * @param fileName
     * @param content
     *
     * @return void
     * @Author: xwc1125
     * @Date: 2019-03-03 22:33:26
     */
    public void sendImgMsg(String toUserName, String fileName, byte[] content) throws Exception {
        Contact to = getContactByUserName(toUserName);
        if (to == null) {
            throw new IllegalArgumentException("用户不存在" + toUserName);
        }
        UploadmediaResp resp = uploadMedia(toUserName, fileName, content);
        //
        SendMsg msg = new SendMsg();
        msg.Type = Message.TYPE_图片消息;
        msg.Content = "";
        msg.MediaId = resp.MediaId;
        msg.FromUserName = wxInitResp.User.UserName;
        msg.ToUserName = to.UserName;
        sendMsg(msg);
    }

    /**
     * Description: 上传文件
     * </p>
     *
     * @param toUserName
     * @param fileName
     * @param content
     * @return com.xwc1125.weixinbot.api.domain.UploadmediaResp
     * @Author: xwc1125
     * @Date: 2019-03-03 22:33:39
     */
    public UploadmediaResp uploadMedia(String toUserName, String fileName, byte[] content) throws Exception {
        if (content == null || content.length < 0) {
            throw new IllegalAccessException("参数错误");
        }
        Map<String, Object> map = createBody();
        map.put("UploadType", 2);
        map.put("ClientMediaId", System.currentTimeMillis());
        map.put("TotalLen", content.length);
        map.put("StartPos", 0);
        map.put("DataLen", content.length);
        map.put("MediaType", 4);
        map.put("FromUserName", wxInitResp.User.UserName);
        map.put("ToUserName", toUserName);
        return apiService.uploadMedia(JSONUtil.toJson(map), fileName, content);
    }

    /**
     * Description: 通过昵称获取联系人
     * </p>
     *
     * @param nickName
     * @return com.xwc1125.weixinbot.api.domain.Contact
     * @Author: xwc1125
     * @Date: 2019-03-03 22:34:12
     */
    public Contact getContactsByNickName(String nickName) {
        for (Contact contact : getContactResp.MemberList) {
            if (contact.NickName.equals(nickName)) {
                return contact;
            }
        }
        return null;
    }

    /**
     * Description: 通过用户名获取联系人
     * </p>
     *
     * @param userName
     * @return com.xwc1125.weixinbot.api.domain.Contact
     * @Author: xwc1125
     * @Date: 2019-03-03 22:34:52
     */
    public Contact getContactByUserName(String userName) {
        return contacts.get(userName);
    }

    /**
     * Description: 通过昵称获取房间
     * </p>
     *
     * @param nickName
     * @return com.xwc1125.weixinbot.api.domain.Contact
     * @Author: xwc1125
     * @Date: 2019-03-03 22:35:11
     */
    public Contact getChatRoomByNickName(String nickName) {
        for (Contact contact : contacts.values()) {
            if (contact.UserName.startsWith("@@") &&
                    contact.NickName.equals(nickName)) {
                return contact;
            }
        }
        return null;
    }

    /**
     * Description: 发送文本内容
     * </p>
     *
     * @param toUserName
     * @param text
     * @return void
     * @Author: xwc1125
     * @Date: 2019-03-03 22:35:22
     */
    public void sendTextMsg(String toUserName, String text) throws Exception {
        SendMsg msg = new SendMsg();
        msg.Type = Message.TYPE_文本消息;
        msg.Content = text;
        msg.FromUserName = wxInitResp.User.UserName;
        msg.ToUserName = toUserName;
        sendMsg(msg);
    }

    public byte[] getHeadimg(String headImg) throws Exception {
        return apiService.getHeadimg(headImg);
    }

    public Collection<Contact> getContacts() {
        return contacts.values();
    }

    public Contact getMySelf() {
        if (wxInitResp == null) {
            return null;
        }
        return wxInitResp.User;
    }
}
