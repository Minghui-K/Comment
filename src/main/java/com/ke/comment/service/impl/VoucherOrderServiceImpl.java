package com.ke.comment.service.impl;


import com.ke.comment.dto.Result;
import com.ke.comment.entity.SeckillVoucher;
import com.ke.comment.entity.Voucher;
import com.ke.comment.service.ISeckillVoucherService;
import com.ke.comment.service.IVoucherOrderService;
import com.ke.comment.entity.VoucherOrder;
import com.ke.comment.mapper.VoucherOrderMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ke.comment.service.IVoucherService;
import com.ke.comment.utils.ILock;
import com.ke.comment.utils.RedisIdWorker;
import com.ke.comment.utils.SimpleRedisLock;
import com.ke.comment.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker idWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private IVoucherOrderService curr;

    private static DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("luaScript/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     * 优化秒杀，缓存库存以及用户id，把检查资格交给redis完成，并且开启线程完成数据库下单。
     * 前端已经检查了下单时间
     * @param voucherId
     * @return
     */
    @Override
    @Transactional
    public Result orderSecKill(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long orderId = idWorker.nextId("order");
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString(), orderId.toString());
        int i = result.intValue();
        if (i != 0) {
            return Result.fail(i == 1 ? "库存不足" : "重复下单");
        }
        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1.订单id
        voucherOrder.setId(orderId);
        // 7.2.用户id
        voucherOrder.setUserId(userId);
        // 7.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        curr = (IVoucherOrderService) AopContext.currentProxy();
        orderTasks.add(voucherOrder);
        return Result.ok(orderId);
    }

    @Override
    public Result createOrder(SeckillVoucher secKill) {
        return null;
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true){
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    curr.createVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            log.error("失败");
            return;
        }

        try {
            // 5.1.查询订单
            long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                log.error("失败");
                return;
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                log.error("库存不足！");
                return;
            }

            // 7.创建订单
            save(voucherOrder);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }

    /*    @Override
    public Result orderSecKill(Long voucherId) {

        SeckillVoucher secKill = seckillVoucherService.getById(voucherId);
        // 检查开始和结束时间
        if (secKill.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (secKill.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        // 检查库存
        if (secKill.getStock() <= 0) {
            return Result.fail("库存不足");
        }
        Long userID = UserHolder.getUser().getId();
        // 根据用户来加锁，相同用户只能获得一把锁。
        // 由于分布式的原因，该锁无法确保正确
        // synchronized (userID.toString().intern()) {

        // 自建锁
        //ILock lock = new SimpleRedisLock("order:" + userID, stringRedisTemplate);

        // Redisson锁
        RLock lock = redissonClient.getLock("order:" + userID);

        // 获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            return Result.fail("用户重复下单");
        }
        // 这里不能直接返回，因为spring使用AOP代理达到事务效果，如果直接调用是没有的，需要获得代理对象。
        try {
            IVoucherOrderService curr = (IVoucherOrderService) AopContext.currentProxy();
            return curr.createOrder(secKill);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }*/

    /*@Transactional
    public Result createOrder(SeckillVoucher secKill) {
        // 检查订单是否已存在，无法上乐观锁，只能悲观锁。
        // 直接给方法上锁会造成性能变差， 而且应该直接给用户加锁防止相同用户，不是只允许一人访问。
        LambdaQueryWrapper<VoucherOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VoucherOrder::getVoucherId, secKill.getVoucherId());
        queryWrapper.eq(VoucherOrder::getUserId, UserHolder.getUser().getId());
        if (this.count(queryWrapper) != 0) {
            return Result.fail("订单已存在");
        }

        LambdaUpdateWrapper<SeckillVoucher> wrapper = new LambdaUpdateWrapper<>();
        // 防止超售，上锁。
        // 乐观锁：不一定不安全，不上锁，用版本号在更新时需要同时确定是允许更新的。悲观锁：必定线程不安全，上锁。
        // 但是在多线程的情况下，容易导致下单失败，因为由于多线程库存不会常常一致。所以尽管保证了安全，但是失败率也大大提高了。
        //wrapper.eq(SeckillVoucher::getStock, secKill.getStock());
        // 在这里可以使用大于来解决，避免矫枉过正
        //wrapper.gt(SeckillVoucher::getStock, 0);

        wrapper.eq(SeckillVoucher::getVoucherId, secKill.getVoucherId())
                .gt(SeckillVoucher::getStock, 0)
                .set(SeckillVoucher::getStock, secKill.getStock()-1);
        boolean success = seckillVoucherService.update(secKill, wrapper);

        if (!success) {
            return Result.fail("库存不足");
        }
        VoucherOrder order= new VoucherOrder();
        // 设置订单ID
        order.setId(idWorker.nextId("order"));
        // 设置秒杀券ID
        order.setVoucherId(secKill.getVoucherId());
        // 设置用户ID
        order.setUserId(UserHolder.getUser().getId());

        this.save(order);

        return Result.ok(order.getId());
    }*/
}
