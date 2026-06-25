package com.pangruixin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.pangruixin.domain.UserRole;
import com.pangruixin.mapper.UserRoleMapper;
import com.pangruixin.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole> implements UserRoleService {
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Override
    public boolean addUserRole(UserRole userRole) {
        boolean b = userRoleMapper.addUserRole(userRole);
        return b;
    }

    @Override
    public List<Long> getRoleIdsByUserId(Object loginId) {
        return userRoleMapper.getRoleIdsByUserId(loginId);
    }
}
