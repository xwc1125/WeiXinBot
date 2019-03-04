package com.xwc1125.weixinbot.threadpool;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author skydu
 *
 */
@Slf4j
public class ThreadPool{
	//
	private ScheduledExecutorService scheduledThreadPool;
	
	private ThreadPoolExecutor workerThreadPool;
	
	private LinkedBlockingQueue<Runnable> requestQueue;
	//
	private int workerPoolSize=16;
	private int maxWorkerPoolSize=32;
	private int scheduledPoolSize=5;
	//
	public ThreadPool() {
	}
	//
	public void start() {
		if(log.isInfoEnabled()){
			log.info(getClass().getSimpleName()+" start");
		}
		int linkedBlockingQueueSize=workerPoolSize*2;
		requestQueue=new LinkedBlockingQueue<Runnable>(linkedBlockingQueueSize);
		scheduledThreadPool=new ScheduledThreadPoolExecutor(
				scheduledPoolSize,
				new WxThreadFactory("WxSchedule"),
				new ThreadPoolExecutor.AbortPolicy());
		//
		workerThreadPool=new ThreadPoolExecutor(
				workerPoolSize,
				maxWorkerPoolSize,
				60L,
				TimeUnit.SECONDS, 
				requestQueue,
				new WxThreadFactory("WxWorker"));
		workerThreadPool.setRejectedExecutionHandler(
				new ThreadPoolExecutor.CallerRunsPolicy());
	}
	//
	public void executeThreadWorker(Runnable worker){
		workerThreadPool.execute(worker);
	}
	//
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
			long initialDelay, long period, TimeUnit unit) {
		return scheduledThreadPool.scheduleAtFixedRate(command,
				initialDelay, period, unit);
	}
}
