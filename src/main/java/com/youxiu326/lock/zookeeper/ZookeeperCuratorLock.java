package com.youxiu326.lock.zookeeper;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import java.util.concurrent.TimeUnit;

public class ZookeeperCuratorLock {

    private static String address = "49.235.105.251:2181";

    public static CuratorFramework client;

    private ZookeeperCuratorLock() {}

    static{
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        client = CuratorFrameworkFactory.newClient(address, retryPolicy);
        client.start();
    }

    private static class SingletonHolder{
        /**
         * 静态初始化器，由JVM来保证线程安全
         * 参考：http://ifeve.com/zookeeper-lock/
         * 这里建议 new 一个
         */
        private  static InterProcessMutex mutex = new InterProcessMutex(client, "/zookeeper_youxiu326/lock");
    }

    public static InterProcessMutex getMutex(){
        return SingletonHolder.mutex;
    }

    // 获得了锁
    public static boolean acquire(long time, TimeUnit unit){
        try {
            return getMutex().acquire(time,unit);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 释放锁
    public static void release(){
        try {
            getMutex().release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}