package com.pangruixin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.pangruixin.common.R;
import com.pangruixin.domain.Role;
import com.pangruixin.domain.dto.RoleSearchDto;
import com.pangruixin.service.RoleMenuService;
import com.pangruixin.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/role")
@Validated //开启验证
@Transactional
public class RoleController {

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleMenuService roleMenuService;

    // 新增角色时先校验 roleKey 唯一，避免权限标识冲突。
    @PostMapping
    @SaCheckPermission("system:role:add")
    public R addRole(@RequestBody @Validated Role role){

        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        // roleKey 是权限体系里的稳定标识，必须唯一。
        wrapper.eq(Role::getRoleKey,role.getRoleKey());
        Role one = roleService.getOne(wrapper);
        if (one!=null){
            return R.error("该角色标识已存在");
        }

        R add = roleService.add(role);
        return add;
    }

    // 删除角色前先确认角色存在；真正的删除联动逻辑在 service 层处理。
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:role:delete")
    public R deleteRole(@PathVariable Long id){

        Role oneRole = roleService.getOneRole(id);
        if (oneRole==null){
            return R.error("该角色不存在");
        }

        int result = roleService.deleteRole(id);
        if (result==0){
            return R.error("删除失败");
        }
        return R.success("删除成功");
    }


    // 角色分页查询用于后台角色管理页。
    @GetMapping("/page")
    @SaCheckPermission("system:role:list")
    public R getRole(RoleSearchDto roleSearchDto){
        // 同时兼容 page/pageNum 两种参数命名，便于不同前端调用。
        long currentPage = roleSearchDto.getPageNum() != null ? roleSearchDto.getPageNum() : (roleSearchDto.getPage() != null ? roleSearchDto.getPage() : 1);
        long pageSize = roleSearchDto.getPageSize() != null ? roleSearchDto.getPageSize() : 10;
        Page<Role> page = new Page<>(currentPage, pageSize);
        String nameFilter = roleSearchDto.getName() != null && !roleSearchDto.getName().trim().isEmpty() ? roleSearchDto.getName().trim() : null;
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>()
                .like(nameFilter != null, Role::getName, nameFilter)
                .eq(roleSearchDto.getStatus() != null, Role::getStatus, roleSearchDto.getStatus())
                .eq(roleSearchDto.getCreateBy() != null, Role::getCreateBy, roleSearchDto.getCreateBy())
                .eq(roleSearchDto.getUpdateBy() != null, Role::getUpdateBy, roleSearchDto.getUpdateBy())
                // 角色列表默认按最近修改时间倒序。
                .orderByDesc(Role::getUpdateTime);
        Page<Role> result = roleService.page(page, wrapper);
        HashMap<String, Object> map = new HashMap<>();
        // 返回结构与后台其它分页接口保持一致。
        map.put("data",result.getRecords());
        map.put("total",result.getTotal());
        return R.success(map);

    }

    // 根据 id 获取角色详情，供编辑弹窗回显。
    @GetMapping("/{id}")
    @SaCheckPermission("system:role:select")
    public R getOneRole(@PathVariable Long id){
        Role oneRole = roleService.getOneRole(id);
        if (oneRole==null){
            return R.error("该角色不存在");
        }
        return R.success(oneRole);
    }

    // 修改角色时同样要保证 roleKey 不和其他角色重复。
    @PutMapping()
    @SaCheckPermission("system:role:update")
    public R updataRole(@RequestBody @Validated Role role){
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleKey,role.getRoleKey());
        // 修改时排除自己，避免“原值不变”也被误判为重复。
        wrapper.ne(Role::getId,role.getId());

        Role one = roleService.getOne(wrapper);
        if (one!=null){
            return R.error("该角色已存在");
        }

        int result = roleService.updateRole(role);
        if (result==0){
           return R.error("修改失败");
        }
        return R.success("修改成功");
    }

    // 简化列表接口，供其他模块做角色下拉选择。
    @GetMapping("/list")
    @SaCheckPermission("system:role:list")
    public R getListRole(){
      return  R.success(roleService.list());
    }

    // 保存角色菜单授权：把角色与勾选的菜单 id 列表重新建立关联关系。
    @PostMapping("/power")
    @SaCheckPermission("system:role:update")
    public R addMenu(@RequestParam("roleId") Long roleId,@RequestParam(value = "menuIds", required = false) List<Long> menuIds){
        // roleMenuService 内部会先清旧授权，再按最新 menuIds 重建关系。
        boolean result = roleMenuService.addRoleMenu(roleId, menuIds);
        if (result) {
            return R.success("添加成功");
        }

        return R.error("添加失败");
    }

    // 获取某个角色当前拥有的菜单权限，用于前端树形回显。
    @GetMapping("/power/{id}")
    @SaCheckPermission("system:role:select")
    public R getRoleMenuById(@PathVariable Long id){
        return  R.success(roleMenuService.getRoleMenuById(id));
    }





}
