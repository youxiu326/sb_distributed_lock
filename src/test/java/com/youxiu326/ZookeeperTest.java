package com.youxiu326;

import org.apache.zookeeper.*;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class ZookeeperTest {

    // 连接地址
    private static final String ADDRES = "youxiu326.com:2181";

    // 连接超时时间
    private static final int SESSIN_TIME_OUT = 2000;

    //计数器
    private static final CountDownLatch countDownLatch = new CountDownLatch(1);


    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {

        //创建ZooKeeper连接 [指定地址 session超时时间]
        ZooKeeper zooKeeper = new ZooKeeper(ADDRES, SESSIN_TIME_OUT, new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                // 获取事件状态
                KeeperState keeperState = watchedEvent.getState();
                // 获取事件类型
                EventType eventType = watchedEvent.getType();

                if (KeeperState.SyncConnected == keeperState) {
                    if (EventType.None == eventType) {
                        countDownLatch.countDown();
                        System.out.println("开启连接............");
                    }
                }
            }
        });

        //如果我的计数器不是为0的话，会一直等待
        countDownLatch.await();
        // 创建节点
        String result = zooKeeper.create("/youxiu326", Thread.currentThread().getName().getBytes(), Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL);
        System.out.println("result:" + result);
        //Thread.sleep(1000 * 10);
        zooKeeper.close();
    }

} 