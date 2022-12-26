package com.ke.comment.service;

import com.ke.comment.dto.Result;
import com.ke.comment.entity.SeckillVoucher;
import com.ke.comment.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result orderSecKill(Long voucherId);

    Result createOrder(SeckillVoucher secKill);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
