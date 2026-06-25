package com.pangruixin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pangruixin.domain.Menu;
import com.pangruixin.domain.dto.MenuSearchDto;


import java.util.List;

public interface MenuService extends IService<Menu> {
    List<String> getUserMenus(Long id);

    int addMenu(Menu menu);

    List<String> getUserMenusList(Object loginId);

    List<Menu> pageMenu(MenuSearchDto menuSearchDto);

    int isCommonPerms(String perms);

    int updateIsHavePerms(Menu menu);

    List<Menu> getRouterList(long loginIdAsLong);

    List<Menu> getMLOptions();

    List<Menu> getCDOptions();

    int isHaveChild(Long id);

    void removeMenuCascade(Long id);
}
