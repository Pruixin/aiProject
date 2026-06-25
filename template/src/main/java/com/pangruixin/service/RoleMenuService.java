package com.pangruixin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pangruixin.domain.RoleMenu;

import java.util.List;


public interface RoleMenuService extends IService<RoleMenu> {
    List<Long> getRoleMenuById(Long id);

    boolean addRoleMenu(Long roleId, List<Long> menuIds);
}
