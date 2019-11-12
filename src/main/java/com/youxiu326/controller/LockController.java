package com.youxiu326.controller;

import com.youxiu326.lock.redis.RedisLock;
import com.youxiu326.lock.zookeeper.ZookeeperLock;
import com.youxiu326.utils.RedisUtil;
import org.apache.zookeeper.KeeperException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class LockController {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RedisLock redisLock;

    @RequestMapping("/redis/lock")
    private String redisLock(){

        ExecutorService executorService = Executors.newFixedThreadPool(10);

//        for (int i = 0; i < 1000; i++) {
//            final  int index = i;
//            executorService.submit(()->{
//                redisUtil.lock("youxiu326", 10, ()->{
//                    System.out.println(index+"运行完毕");
//                });
//            });
//        }


        for (int i = 0; i < 1000; i++) {
            final  int index = i;
            executorService.submit(()->{
                redisLock.execute("youxiu326", 10, ()->{
                    System.out.println(index+"运行完毕");
                });
            });
        }

        return "ok";
    }
    @RequestMapping("/zookeeper/lock")
    private String zookeeperLock() throws Exception {

        ExecutorService executorService = Executors.newFixedThreadPool(10);

        ZookeeperLock zookeeperLock = new ZookeeperLock("youxiu326.com:2181");

        for (int i = 0; i < 1000; i++) {
            final  int index = i;
            executorService.submit(()->{
                try {
                    zookeeperLock.lock();
                    System.out.println(index+"运行完毕");
                    zookeeperLock.unLock();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        return "ok";
    }

} 