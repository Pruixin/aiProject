package com.pangruixin.service.impl;

import cn.dev33.satoken.stp.StpInterface;

import com.pangruixin.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;


@Component
@Slf4j
public class StpInterfaceImpl implements StpInterface {
    @Autowired
    private UserRoleService userRoleService;


    @Autowired
    private MenuService menuService;

    @Autowired
    private RoleService roleService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {

//       查询用户权限
        return  menuService.getUserMenusList(loginId);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {

//        根据userId查询其权限id
        List<Long> roleIds = userRoleService.getRoleIdsByUserId(loginId);
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        return roleService.getRoleKeyByUserId(roleIds);
    }
}
