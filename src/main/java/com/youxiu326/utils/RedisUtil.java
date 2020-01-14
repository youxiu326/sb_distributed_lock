package com.youxiu326.utils;

import com.youxiu326.common.Const;
import com.youxiu326.lock.redis.RedisLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/**
 * redis分布式锁
 */
@Deprecated
@Component
public class RedisUtil {

    private static Logger logger = LoggerFactory.getLogger(RedisUtil.class);

    /**
     * 假设你设置的超时时间为30秒
     * 那么我提前5秒即25秒就将锁过期了
     */
    private int reduceTime = 5;

    private String lockValue = "lock";

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void execute(String key,int timeOut,RedisLockTask task){
        // 如果不存在key 则获得锁
        Boolean lock = redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(System.currentTimeMillis() + Const.LOCK_TIMEOUT));
        if (lock) {
            logger.info("======获取到锁======");
            // 对锁设置有效期
            redisTemplate.expire(key, timeOut, TimeUnit.SECONDS);
            // 执行任务
            task.run();
            // 释放锁
            redisTemplate.delete(key);
        } else {
            long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            /*  不存在key返回-2 | 存在key未设置过期时间 返回-1  */
            // 未设置超时时间,可能上一次获得锁后未设置超时时间，防止死锁，直接获得锁并设置超时时间
            if (expire == -1) {
                logger.info("======获取到锁======");
                redisTemplate.expire(key, timeOut - reduceTime, TimeUnit.SECONDS);
                //执行
                task.run();
                // 释放锁
                redisTemplate.delete(key);
            }
            logger.info("======未获取到锁======");
            logger.info("{}离一下次执行时间还有:{}秒", key,expire==-2?0:expire);
        }
    }

    @FunctionalInterface
    public interface RedisLockTask {
        public void run();
    }

}