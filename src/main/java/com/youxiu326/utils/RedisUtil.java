package com.youxiu326.utils;

import com.youxiu326.common.Const;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis锁
 * @link https://blog.csdn.net/dazou1/article/details/88088223
 */
@Component
public class RedisUtil {

    private static final Logger logger = LoggerFactory.getLogger(RedisUtil.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * @param key     锁
     * @param timeOut 有效期
     * @param task    任务
     */
    public void lock(String key, int timeOut, RedisLockTask task) {
        // 这个方法的缺陷在这里，如果setnx成功后，锁已经存到Redis里面了，服务器异常关闭重启，将不会执行closeOrder，
        // 也就不会设置锁的有效期，这样的话锁就不会释放了，就会产生死锁
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
            logger.info("======未获取到锁======");
            // 不存在key返回-2  | 存在key未设置过期时间 返回-1
            Long expire = redisTemplate.getExpire(key,TimeUnit.SECONDS);
            if (expire == -2){
                logger.info("==========={}距离锁过期还有:{}",key,expire);
            }
            // 通过当前的时间与锁设置的时间做比较，如果当前时间已经大于锁设置的时间临界，即可以进一步判断是否可以获取锁，
            // 否则说明该锁还在被占用，不能获取该锁
            String lockValueStr = redisTemplate.opsForValue().get(key);
            System.out.println(System.currentTimeMillis() +"-"+lockValueStr);
            if (lockValueStr != null && System.currentTimeMillis() > Long.parseLong(lockValueStr)) {
                //通过getSet设置新的超时时间，并返回旧值，以作判断，因为在分布式环境，在进入这里时可能另外的进程获取到锁并对值进行了修改，只有旧值与返回的值一致才能说明中间未被其他进程获取到这个锁
                String getSetResult = redisTemplate.opsForValue().getAndSet(key, String.valueOf(System.currentTimeMillis() + Const.LOCK_TIMEOUT));
                //再次用当前时间戳getset。
                //返回给定的key的旧值，与旧值判断，是否可以获取锁
                //当key没有旧值时，即key不存在时，返回nil ->获取锁
                //这里我们set了一个新的value值，获取旧的值。
                //若果getSetResult为null，说明该锁已经被释放了，此时该进程可以获取锁；旧值与返回的getSetResult一致说明中间未被其他进程获取该锁，可以获取锁
                if (getSetResult == null || (getSetResult != null && StringUtils.equals(lockValueStr, getSetResult))) {
                    logger.info("======进一步判断后->获取到锁======");
                    // 对锁设置有效期
                    redisTemplate.expire(key, timeOut, TimeUnit.SECONDS);
                    // 执行任务
                    task.run();
                    // 释放锁
                    redisTemplate.delete(key);
                } else {
                    logger.info("没有获得分布式锁");
                }
            }
        }

    }

    @FunctionalInterface
    public interface RedisLockTask {
        public void run();
    }

}