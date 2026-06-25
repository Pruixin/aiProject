package com.pangruixin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pangruixin.domain.UserRole;

import java.util.List;


public interface UserRoleService extends IService<UserRole> {
    boolean addUserRole(UserRole userRole);

    List<Long> getRoleIdsByUserId(Object loginId);
}
