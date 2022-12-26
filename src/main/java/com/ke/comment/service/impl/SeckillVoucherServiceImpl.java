package com.ke.comment.service.impl;

import com.ke.comment.service.ISeckillVoucherService;
import com.ke.comment.entity.SeckillVoucher;
import com.ke.comment.mapper.SeckillVoucherMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

}
