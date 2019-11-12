package com.youxiu326;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SbDistributedLockApplicationTests {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void contextLoads() {

        String key = "youxiu326";
        String value = "中文";

        redisTemplate.opsForValue().set(key,value);

        redisTemplate.expire(key, 6, TimeUnit.SECONDS);

        // 获取设置的过期时间
        Long expire = redisTemplate.getExpire("youxiu326", TimeUnit.SECONDS);
        System.out.println(expire+"=========不存在key返回-2========");
        System.out.println(expire+"=========存在key未设置过期时间 返回-1========");
        System.out.println(expire+"=================");

    }

}
