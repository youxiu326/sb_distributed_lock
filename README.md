
##### 学习redis分布式锁与zookeeper分布式锁

http://localhost:8888//redis/lock

http://localhost:8888//redisson/lock

参考博客：
[redis分布式锁原理与实现](https://blog.csdn.net/dazou1/article/details/88088223)


```

public void redis2() {
        log.info("关闭订单定时任务启动");
        long lockTimeout = Long.parseLong(PropertiesUtil.getProperty("lock.timeout", "5000"));
        Long setnxResult = RedisShardedPoolUtil.setnx(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK, String.valueOf(System.currentTimeMillis() + lockTimeout));
        if (setnxResult != null && setnxResult.intValue() == 1) {
            //如果返回值为1，代表设置成功，获取锁
            closeOrder(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
        } else {
            //未获取到锁，继续判断，判断时间戳，看是否可以重置并获取到锁
            String lockValueStr = RedisShardedPoolUtil.get(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
            //通过当前的时间与锁设置的时间做比较，如果当前时间已经大于锁设置的时间临界，即可以进一步判断是否可以获取锁，否则说明该锁还在被占用，不能获取该锁
            if (lockValueStr != null && System.currentTimeMillis() > Long.parseLong(lockValueStr)) {
                //通过getSet设置新的超时时间，并返回旧值，以作判断，因为在分布式环境，在进入这里时可能另外的进程获取到锁并对值进行了修改，只有旧值与返回的值一致才能说明中间未被其他进程获取到这个锁
                String getSetResult = RedisShardedPoolUtil.getSet(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK, String.valueOf(System.currentTimeMillis() + lockTimeout));
                //再次用当前时间戳getset。
                //返回给定的key的旧值，与旧值判断，是否可以获取锁
                //当key没有旧值时，即key不存在时，返回nil ->获取锁
                //这里我们set了一个新的value值，获取旧的值。
                //若果getSetResult为null，说明该锁已经被释放了，此时该进程可以获取锁；旧值与返回的getSetResult一致说明中间未被其他进程获取该锁，可以获取锁
                if (getSetResult == null || (getSetResult != null && StringUtils.equals(lockValueStr, getSetResult))) {
                    //真正获取到锁
                    closeOrder(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
                } else {
                    log.info("没有获得分布式锁:{}", Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
                }
            } else {
                log.info("没有获得分布式锁:{}", Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
            }
        }
        log.info("关闭订单定时任务结束");
    }
    private void closeOrder(String lockName) {
        //对锁设置有效期
        RedisShardedPoolUtil.expire(lockName, 5);//有效期为5秒，防止死锁
        log.info("获取锁:{},ThreadName:{}",Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK, Thread.currentThread().getName());
        //执行业务
        int hour = Integer.parseInt(PropertiesUtil.getProperty("close.order.task.time.hour", "2"));
        iOrderService.closeOrder(hour);
        //执行完业务后，释放锁
        RedisShardedPoolUtil.del(Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK);
        log.info("释放锁:{},ThreadName:{}",Const.REDIS_LOCK.CLOSE_ORDER_TASK_LOCK, Thread.currentThread().getName());
        log.info("=================================");
    }


```




实现参考： https://blog.csdn.net/liyiming2017/article/details/83786331

如何使用zookeeper实现分布式锁？

在描述算法流程之前，先看下zookeeper中几个关于节点的有趣的性质：

有序节点：假如当前有一个父节点为/lock，我们可以在这个父节点下面创建子节点；
zookeeper提供了一个可选的有序特性，例如我们可以创建子节点“/lock/node-”并且指明有序，
那么zookeeper在生成子节点时会根据当前的子节点数量自动添加整数序号，也就是说如果是第一个创建的子节点，
那么生成的子节点为/lock/node-0000000000，下一个节点则为/lock/node-0000000001，依次类推。


临时节点：客户端可以建立一个临时节点，在会话结束或者会话超时后，zookeeper会自动删除该节点。

事件监听：在读取数据时，我们可以同时对节点设置事件监听，当节点数据或结构变化时，
zookeeper会通知客户端。当前zookeeper有如下四种事件：
1）节点创建；2）节点删除；3）节点数据修改；4）子节点变更。

————————————————————————————

下面描述使用zookeeper实现分布式锁的算法流程，假设锁空间的根节点为/lock：

   客户端连接zookeeper，并在/lock下创建临时的且有序的子节点，
   第一个客户端对应的子节点为/lock/lock-0000000000，
   第二个为/lock/lock-0000000001，以此类推。

   客户端获取/lock下的子节点列表，判断自己创建的子节点是否为当前子节点列表中序号最小的子节点，
   如果是则认为获得锁，否则监听/lock的子节点变更消息，获得子节点变更通知后重复此步骤直至获得锁；

   执行业务代码；
   完成业务流程后，删除对应的子节点释放锁。
    步骤1中创建的临时节点能够保证在故障的情况下锁也能被释放，
    考虑这么个场景：假如客户端a当前创建的子节点为序号最小的节点，获得锁之后客户端所在机器宕机了，
    客户端没有主动删除子节点；如果创建的是永久的节点，那么这个锁永远不会释放，导致死锁；
    由于创建的是临时节点，客户端宕机后，过了一定时间zookeeper没有收到客户端的心跳包判断会话失效，
    将临时节点删除从而释放锁。

    另外细心的朋友可能会想到，
    在步骤2中获取子节点列表与设置监听这两步操作的原子性问题，
    考虑这么个场景：客户端a对应子节点为/lock/lock-0000000000，
    客户端b对应子节点为/lock/lock-0000000001，客户端b获取子节点列表时发现自己不是序号最小的，
    但是在设置监听器前客户端a完成业务流程删除了子节点/lock/lock-0000000000，
    客户端b设置的监听器岂不是丢失了这个事件从而导致永远等待了？
    这个问题不存在的。因为zookeeper提供的API中设置监听器的操作与读操作是原子执行的，
    也就是说在读子节点列表时同时设置监听器，保证不会丢失事件。

    最后，对于这个算法有个极大的优化点：假如当前有1000个节点在等待锁，如果获得锁的客户端释放锁时，
    这1000个客户端都会被唤醒，这种情况称为“羊群效应”；在这种羊群效应中，zookeeper需要通知1000个客户端，
    这会阻塞其他的操作，最好的情况应该只唤醒新的最小节点对应的客户端。应该怎么做呢？在设置事件监听时，
    每个客户端应该对刚好在它之前的子节点设置事件监听，
    例如子节点列表为/lock/lock-0000000000、/lock/lock-0000000001、/lock/lock-0000000002，
    序号为1的客户端监听序号为0的子节点删除消息，序号为2的监听序号为1的子节点删除消息。

——————————————————————————

我们在2.0版本中，让每个客户端在/exlusive_lock下创建的临时节点为有序节点，这样每个客户端都在/exlusive_lock下有自己对应的锁节点，而序号排在最前面的节点，代表对应的客户端获取锁成功。排在后面的客户端监听自己前面一个节点，那么在他前序客户端执行完成后，他将得到通知，获得锁成功。逻辑修改如下：

   每个客户端往/exlusive_lock下创建有序临时节点/exlusive_lock/lock_。创建成功后/exlusive_lock下面会有每个客户端对应的节点，如/exlusive_lock/lock_000000001
   客户端取得/exlusive_lock下子节点，并进行排序，判断排在最前面的是否为自己。
   如果自己的锁节点在第一位，代表获取锁成功，此客户端执行业务逻辑
   如果自己的锁节点不在第一位，则监听自己前一位的锁节点。例如，自己锁节点lock_000000002，那么则监听lock_000000001.
   当前一位锁节点（lock_000000001）对应的客户端执行完成，释放了锁，将会触发监听客户端（lock_000000002）的逻辑。
   监听客户端重新执行第2步逻辑，判断自己是否获得了锁。

