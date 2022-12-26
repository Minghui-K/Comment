package com.ke.comment.service.impl;

import com.ke.comment.entity.BlogComments;
import com.ke.comment.mapper.BlogCommentsMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ke.comment.service.IBlogCommentsService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
