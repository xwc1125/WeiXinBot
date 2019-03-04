package com.xwc1125.weixinbot;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import com.xwc1125.weixinbot.utils.JSONUtil;
import io.itit.itf.okhttp.FastHttpClient;

/**
 * Description: 自动回复AI
 * </p>
 *
 * @Author: xwc1125
 * @Date: 2019-03-03 22:40:34
 * @Copyright Copyright@2019
 */
@Slf4j
public class AutoReplyAI {
    //
    private static final String TOKEN = "97AAF6D2AEE22DC53F4C90D8B1BD7348";
    private static AtomicLong sessionId = new AtomicLong(0);

    /**
     * Description: 查询
     * </p>
     *
     * @param query
     * @return java.lang.String
     * @Author: xwc1125
     * @Date: 2019-03-03 22:40:54
     */
    public static String query(String query) {
        String json = null;
        try {
            if (query != null && query.length() > 0) {
                if (!query.endsWith("？")) {
                    query += "？";
                }
            }
            json = FastHttpClient.post().url("http://www.yige.ai/v1/query").
                    addParams("token", TOKEN).
                    addParams("reset_state", "1").
                    addParams("session_id", "" + sessionId.incrementAndGet()).
                    addParams("query", query).build().execute().string();
            AIQueryResp resp = JSONUtil.fromJson(json, AIQueryResp.class);
            if (!resp.status.code.equals("200")) {
                return "[微笑]" + resp.answer;
            }
            return resp.answer;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return "[微笑]您说的我不明白";
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("query {} json:{}", query, json);
            }
        }
    }

    //
    public static void main(String[] args) throws IOException, Exception {
        System.out.println(AutoReplyAI.query("你多大"));
    }
}
