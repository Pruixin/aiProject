package com.pangruixin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pangruixin.common.R;
import com.pangruixin.domain.Role;
import com.pangruixin.domain.UserRole;
import com.pangruixin.domain.dto.RoleSearchDto;

import java.util.List;


public interface RoleService extends IService<Role> {
    R add(Role role);

    int deleteRole(Long id);

    Role getOneRole(Long id);

    int updateRole(Role role);

    List<String> getRoleKeyByUserId(List<Long> roleIds);

    List<Role> rolePage(RoleSearchDto roleSearchDto);
}
