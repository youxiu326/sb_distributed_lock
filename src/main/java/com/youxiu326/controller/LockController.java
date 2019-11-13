package com.youxiu326.controller;

import com.youxiu326.lock.redis.RedisLock;
import com.youxiu326.lock.redis.RedissonLock;
import com.youxiu326.lock.zookeeper.ZookeeperLock;
import com.youxiu326.utils.RedisUtil;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
public class LockController {

    //@Autowired
    //private RedisUtil redisUtil;

    @Autowired
    private RedisLock redisLock;


    @Autowired
    private RedissonLock redissonLock;

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

    @RequestMapping("/redisson/lock")
    private String redissonLock(){

        ExecutorService executorService = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 1000; i++) {
            final  int index = i;
            executorService.submit(()->{
                boolean res=false;
                try {
                    // 尝试获取锁，最多等待3秒，上锁以后20秒自动解锁
                    res = redissonLock.tryLock("redis_lock_youxiu326", TimeUnit.SECONDS, 3, 20);
                    if(res) {
                        System.out.println(index+"运行完毕");
                        Thread.sleep(3000);
                    }else{
                        System.out.println(index+"未获取到锁");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (res){
                        redissonLock.unlock("redis_lock_youxiu326");
                    }
                }
            });
        }

        return "ok";
    }

} 