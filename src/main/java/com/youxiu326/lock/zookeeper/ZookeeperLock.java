package com.youxiu326.lock.zookeeper;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class ZookeeperLock {

    private static Logger logger = LoggerFactory.getLogger(ZookeeperLock.class);

    private ZooKeeper zkClient;

    private static final String LOCK_ROOT_PATH = "/locks";

    private static final String LOCK_NAME = "lock_";

    private volatile String lockPath;

    // 监控lockPath的前一个节点的watcher
    private Watcher watcher = new Watcher() {
        @Override
        public void process(WatchedEvent event) {
            logger.info("{} 前锁释放", event.getPath());
            synchronized (this) {
                notifyAll();
            }
        }
    };

    public ZookeeperLock() throws IOException {
        zkClient = new ZooKeeper("youxiu326.com:2181", 10000, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if(event.getState()== Event.KeeperState.Disconnected){
                    logger.info("失去连接");
                }
            }
        });
    }
    public ZookeeperLock(String address) throws IOException {
        zkClient = new ZooKeeper(address, 10000, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if(event.getState()== Event.KeeperState.Disconnected){
                    logger.info("失去连接");
                }
            }
        });
    }

    /**
     * 获取锁
     * @throws KeeperException
     * @throws InterruptedException
     */
    public void lock() throws KeeperException, InterruptedException {
        createLock();
        getLock();
    }

    /**
     * 释放锁
     * @throws InterruptedException
     * @throws KeeperException
     */
    public void unLock() throws InterruptedException, KeeperException {
        zkClient.delete(lockPath, -1);
        zkClient.close();
       logger.info(" 锁释放：{}", lockPath);
    }


    /**
     * 创建锁节点
     */
    private void createLock() throws KeeperException, InterruptedException {
        //如果根节点不存在，则创建根节点(持久节点)
        Stat stat = zkClient.exists(LOCK_ROOT_PATH, false);
        if (stat == null) {
            zkClient.create(LOCK_ROOT_PATH, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }

        // 创建EPHEMERAL_SEQUENTIAL类型节点
        String lockPath = zkClient.create(LOCK_ROOT_PATH + "/" + LOCK_NAME,
                Thread.currentThread().getName().getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);

        logger.info("{}创建锁:{}",Thread.currentThread().getName(),lockPath);
        this.lockPath=lockPath;
    }

    /**
     * 尝试获取锁
     * @throws KeeperException
     * @throws InterruptedException
     */
    private void getLock() throws KeeperException, InterruptedException {

        // 获取Lock所有子节点
        List<String>  lockPaths = zkClient.getChildren(LOCK_ROOT_PATH, false);
        // 按照节点序号排序
        Collections.sort(lockPaths);

        /*
            lockPaths   【Lock_0000000756】
            lockPath    【/Locks/Lock_0000000756】
         */
        int index = lockPaths.indexOf(lockPath.substring(LOCK_ROOT_PATH.length() + 1));

        // 如果lockPath是序号最小的节点，则获取锁
        if (index == 0) {
            logger.info("{} 锁获得, lockPath: {}", Thread.currentThread().getName(), lockPath);
        }else{
            // lockPath不是序号最小的节点，监控前一个节点
            String preLockPath = lockPaths.get(index - 1);

            Stat stat = zkClient.exists(LOCK_ROOT_PATH + "/" + preLockPath, watcher);

            // 假如前一个节点不存在了，比如说执行完毕，或者执行节点掉线，重新获取锁
            if (stat == null) {
                getLock();
            } else {
                // 阻塞当前进程，直到preLockPath释放锁，被watcher观察到，notifyAll后，重新acquireLock
                logger.info("等待前锁释放，prelocakPath：{} ",preLockPath);
                synchronized (watcher) {
                    watcher.wait();
                }
                getLock();
            }
        }

    }

}