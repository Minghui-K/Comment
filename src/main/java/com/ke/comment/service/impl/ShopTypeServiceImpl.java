package com.ke.comment.service.impl;

import cn.hutool.json.JSONUtil;
import com.ke.comment.dto.Result;
import com.ke.comment.entity.Shop;
import com.ke.comment.service.IShopTypeService;
import com.ke.comment.entity.ShopType;
import com.ke.comment.mapper.ShopTypeMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ke.comment.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        String key = "cache:shop-type";
        // 1.查询redis
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (shopJson != null) {
            // 直接返回
            List<ShopType> shop = JSONUtil.toList(shopJson, ShopType.class);
            return Result.ok(shop);
        }
        // 3.查数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (typeList.isEmpty()) {
            return Result.fail("店铺类型异常");
        }
        // 4. 写入redis缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));
        return Result.ok(typeList);
    }
}
