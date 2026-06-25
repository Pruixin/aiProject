package com.pangruixin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pangruixin.domain.User;
import com.pangruixin.mapper.UserMapper;
import com.pangruixin.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements  UserService{
}
