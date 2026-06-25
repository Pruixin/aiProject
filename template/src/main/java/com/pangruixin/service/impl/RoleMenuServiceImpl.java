package com.pangruixin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pangruixin.domain.RoleMenu;
import com.pangruixin.mapper.RoleMenuMapper;
import com.pangruixin.service.RoleMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoleMenuServiceImpl extends ServiceImpl<RoleMenuMapper, RoleMenu> implements RoleMenuService {
    @Autowired
    private RoleMenuMapper roleMenuMapper;

    @Override
    public List<Long> getRoleMenuById(Long id) {
        LambdaQueryWrapper<RoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(RoleMenu::getMenuId);
        wrapper.eq(RoleMenu::getRoleId, id);
        List<RoleMenu> menus = roleMenuMapper.selectList(wrapper);
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        return menus.stream()
                .map(RoleMenu::getMenuId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addRoleMenu(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        if (menuIds == null || menuIds.isEmpty()) {
            return true;
        }

        Set<Long> distinctMenuIds = new LinkedHashSet<>(menuIds);
        for (Long menuId : distinctMenuIds) {
            if (menuId == null) {
                continue;
            }
            RoleMenu roleMenu = new RoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            int insert = roleMenuMapper.insert(roleMenu);
            if (insert <= 0) {
                return false;
            }
        }
        return true;
    }
}
