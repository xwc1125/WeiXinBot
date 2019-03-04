package com.xwc1125.weixinbot.threadpool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author skydu
 *
 */
@Slf4j
public class WxThreadFactory implements ThreadFactory {
	private AtomicInteger threadCounter=new AtomicInteger();
	private String name;
	//
	public WxThreadFactory(String name) {
		this.name=name;
	}
	@Override
	public Thread newThread(Runnable r) {
		Thread t=new Thread(r);
		t.setName(name+"-"+threadCounter.incrementAndGet());
		Thread.UncaughtExceptionHandler logHander=new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				log.error(e.getMessage(),e);
			}
		};
		t.setUncaughtExceptionHandler(logHander);
		return t;
	}
}
