package com.ke.comment.service;

import com.ke.comment.dto.Result;
import com.ke.comment.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    Result saveWithCache(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
