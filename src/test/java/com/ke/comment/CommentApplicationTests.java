package com.ke.comment;

import cn.hutool.json.JSONUtil;
import com.ke.comment.entity.Shop;
import com.ke.comment.service.IShopService;
import com.ke.comment.service.impl.ShopServiceImpl;
import com.ke.comment.utils.RedisData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class CommentApplicationTests {

	@Resource
	private RedisTemplate redisTemplate;

	@Resource
	private StringRedisTemplate stringRedisTemplate;

	@Autowired
	private IShopService shopService;


	@Test
	void contextLoads() {
	}

	@Test
	void testRedis() {
		RedisData data = new RedisData();
		Shop shop = new Shop();
		shop.setAddress("slh");
		shop.setId(10L);
		data.setData(shop);
		stringRedisTemplate.opsForValue().set("test", JSONUtil.toJsonStr(data));
		data = JSONUtil.toBean(stringRedisTemplate.opsForValue().get("test"), RedisData.class);
		System.out.println(data.getData());
	}

	@Test
	void loadShopLocaltion() {
		List<Shop> list = shopService.list();
		// ！！！直接分组！！！
		Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
		for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
			Long typeId = entry.getKey();
			List<Shop> value = entry.getValue();
			String key = "shop:geo:" + typeId;
			for (Shop shop : value) {
				// 把shop存入geo
				stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
			}
		}

	}

}
