package com.xwc1125.weixinbot.api.domain;

import com.xwc1125.weixinbot.utils.JSONUtil;
import com.xwc1125.weixinbot.utils.StringUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * 
 * @author skydu
 *
 */
public class BaseRequest {

	public String DeviceID;//e519062714508114
	
	public String Uin;
	
	public String Skey;
	
	public String Sid;
	//
	public BaseRequest(){
		randomDeviceID();
	}
	//
	public void randomDeviceID(){
		this.DeviceID="e"+ StringUtil.randomNumbers(15);
	}
	//
	public static void main(String[] args) {
		Map<String, Object> paraMap=new HashMap<>();
		BaseRequest bean=new BaseRequest();
		bean.Uin="112121";
		paraMap.put("BaseRequest",bean);
		System.out.println(JSONUtil.toJson(paraMap));
	}
}
