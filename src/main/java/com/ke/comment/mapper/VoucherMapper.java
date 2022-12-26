package com.ke.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ke.comment.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @since 2021-12-22
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);

    List<Voucher> selectPayValueByIdEquals(@Param("id") Long id);

    int setActualValueByIdEquals(@Param("actualValue") Long actualValue, @Param("id") Long id);

    List<Voucher> queryAllByShopId(@Param("shopId") Long shopId);
}
