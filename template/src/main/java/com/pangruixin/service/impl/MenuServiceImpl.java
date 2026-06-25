package com.pangruixin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pangruixin.domain.Menu;
import com.pangruixin.domain.dto.MenuSearchDto;
import com.pangruixin.mapper.MenuMapper;
import com.pangruixin.service.MenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements MenuService {

    @Autowired
    private MenuMapper menuMapper;
    @Override
    public List<String> getUserMenus(Long id) {
       return menuMapper.getUserMenus(id);
    }

    @Override
    public int addMenu(Menu menu) {
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Menu::getPerms, menu.getPerms());
        Menu one = menuMapper.selectOne(wrapper);
        if (one!=null){
//            throw new BusinessException(40005,"该权限已存在");
        }

        int insert = menuMapper.insert(menu);
        return insert;
    }

    @Override
    public List<String> getUserMenusList(Object loginId) {
        return menuMapper.getUserMenusList(loginId);
    }

    @Override
    public List<Menu> pageMenu(MenuSearchDto menuSearchDto) {
        return menuMapper.pageMenu(menuSearchDto);
    }

    @Override
    public int isCommonPerms(String perms) {
        return menuMapper.isCommonPerms(perms);
    }

    @Override
    public int updateIsHavePerms(Menu menu) {
        return menuMapper.updateIsHavePerms(menu);
    }

    @Override
    public List<Menu> getRouterList(long loginIdAsLong) {
        return menuMapper.getRouterList(loginIdAsLong);
    }

    @Override
    public List<Menu> getMLOptions() {

        return menuMapper.getMLOptions();
    }

    @Override
    public List<Menu> getCDOptions() {
        return menuMapper.getCDOptions();
    }

    @Override
    public int isHaveChild(Long id) {
        return menuMapper.isHaveChild(id);
    }

    @Override
    public void removeMenuCascade(Long id) {
        List<Long> childIds = menuMapper.findChildIds(id);
        for (Long childId : childIds) {
            removeMenuCascade(childId);
        }
        menuMapper.deleteById(id);
    }
}
