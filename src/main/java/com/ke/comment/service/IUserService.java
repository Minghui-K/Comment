package com.ke.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ke.comment.dto.LoginFormDTO;
import com.ke.comment.dto.Result;
import com.ke.comment.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result logout(String token);

    Result sign();

    Result checkSign();
}
