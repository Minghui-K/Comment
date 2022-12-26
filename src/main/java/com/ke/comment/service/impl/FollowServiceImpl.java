package com.ke.comment.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ke.comment.dto.Result;
import com.ke.comment.dto.UserDTO;
import com.ke.comment.entity.User;
import com.ke.comment.service.IFollowService;
import com.ke.comment.entity.Follow;
import com.ke.comment.mapper.FollowMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ke.comment.service.IUserService;
import com.ke.comment.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long id, Boolean isFollow) {
        long userId = UserHolder.getUser().getId();
        // 关注操作
        if (BooleanUtil.isTrue(isFollow)) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean success = save(follow);
            if (success) {
                String key = "follows" + userId;
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }

        } else {
            // 取关操作
            boolean success = remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, id));
            if (success) {

                String key = "follows:" + userId;
                stringRedisTemplate.opsForSet().remove(key, id.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        long userId = UserHolder.getUser().getId();
        // 直接在redis里取数据，不需要这个了
//        long count = count(new LambdaQueryWrapper<Follow>()
//                .eq(Follow::getUserId, userId)
//                .eq(Follow::getFollowUserId, id));
        Boolean isMember = stringRedisTemplate.opsForSet().isMember("follows:" + userId, id);
        return Result.ok(isMember);
    }

    @Override
    public Result common(Long id) {
        long userId = UserHolder.getUser().getId();
        // 查共同关注
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect("follows:" + id, "follows:" + userId);
        // 解析id查信息
        List<Long> collect = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userList = userService.listByIds(collect).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userList);
    }
}
