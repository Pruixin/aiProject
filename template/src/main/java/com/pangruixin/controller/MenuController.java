package com.pangruixin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.pangruixin.common.R;
import com.pangruixin.common.tree.TreeUtil;
import com.pangruixin.domain.Menu;
import com.pangruixin.domain.dto.MenuSearchDto;
import com.pangruixin.service.MenuService;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/menu")
@Validated
@Transactional
public class MenuController {
    @Autowired
    private MenuService menuService;


    @PostMapping()
    @SaCheckPermission("system:menu:add")
    public R addMenu(@RequestBody @Validated Menu menu){

        // 权限标识 perms 需要全局唯一，否则角色授权和按钮权限判断会冲突。
        int commonPerms = menuService.isCommonPerms(menu.getPerms());
        if (commonPerms>0){
            return R.error("该权限键已存在");
        }

        int result = menuService.addMenu(menu);
        if (result==0){
            return R.error("添加失败");
        }
        return R.success("添加成功");
    }

    // 删除菜单时级联删除所有子菜单，防止树结构断裂。
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:menu:delete")
    public R deleteMenu(@PathVariable Long id){
        Menu menu = menuService.getById(id);
        if (menu ==null){
            return R.error("该权限已不存在");
        }
        menuService.removeMenuCascade(id);
        return R.success("删除成功");
    }

    // 查询单个菜单详情，用于编辑弹窗回显。
    @GetMapping("/{id}")
    @SaCheckPermission("system:menu:select")
    public R getOneMenu(@PathVariable Long id){
        Menu menu = menuService.getById(id);
        if (menu ==null){
            return R.error("该权限已不存在");
        }
        return R.success(menu);
    }

    // 修改菜单时也要检查 perms 是否与其他菜单冲突。
    @PutMapping()
    @SaCheckPermission("system:menu:update")
    public R updataMenu(@RequestBody @Validated Menu menu){

        int count = menuService.updateIsHavePerms(menu);
        if (count>0){
            return R.error("该权限健已存在");
        }

        boolean result = menuService.updateById(menu);
        if (result){
            return R.success("修改成功");
        }
        return R.error("修改失败");
    }

    // 返回菜单树，供后台菜单管理页直接渲染树形结构。
    @GetMapping("/list")
    @SaCheckPermission("system:menu:list")
    public R getListMenu(){

        // 当前实现未按登录人过滤菜单管理树，而是返回系统中所有启用菜单。
        Object loginId = StpUtil.getLoginId();
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Menu::getStatus,0);
        List<Menu> menuList = menuService.list(wrapper);
        // 菜单管理页使用树表格，因此这里直接在后端组装成树。
        List<Menu> menus = TreeUtil.makeTree(
                menuList,
                x -> x.getParentId() == 0,
                (parent, child) -> parent.getId().equals(child.getParentId()),
                Menu::setChildren
        );
        return R.success(menus);

    }

    @GetMapping("/getMLOptions")
    @SaCheckPermission("system:menu:list")
    public R getMLOptions(){
        // 获取所有目录节点，供“新增菜单”时选择父级目录。
        List<Menu> mlOptions = menuService.getMLOptions();
        return R.success(mlOptions);
    }

    @GetMapping("/getCDOptions")
    @SaCheckPermission("system:menu:list")
    public R getCDOptions(){
        // 获取所有页面菜单节点，供“新增按钮”时选择所属菜单。
        List<Menu> cdOptions = menuService.getCDOptions();
        return R.success(cdOptions);
    }
}
