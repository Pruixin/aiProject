package com.pangruixin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.pangruixin.common.R;
import com.pangruixin.domain.Role;
import com.pangruixin.domain.UserRole;
import com.pangruixin.domain.dto.RoleSearchDto;
import com.pangruixin.mapper.RoleMapper;
import com.pangruixin.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {
    @Autowired
    private RoleMapper roleMapper;



    @Override
    public R add(Role role) {

        int insert = roleMapper.insert(role);
        if (insert==0){
            return R.error("添加失败");
        }
        return R.success("添加成功");
    }

    @Override
    public int deleteRole(Long id) {
        int result = roleMapper.deleteById(id);

        return result;
    }

    @Override
    public Role getOneRole(Long id) {
        Role role = roleMapper.selectById(id);
        return role;
    }

    @Override
    public int updateRole(Role role) {
        int result = roleMapper.updateById(role);

        return result;
    }

    @Override
    public List<String> getRoleKeyByUserId(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.getRoleKeyByUserId(roleIds);
    }

    @Override
    public List<Role> rolePage(RoleSearchDto roleSearchDto) {
        return roleMapper.rolePage(roleSearchDto);
    }

}
