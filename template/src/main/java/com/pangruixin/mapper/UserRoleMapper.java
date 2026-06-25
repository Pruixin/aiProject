package com.pangruixin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.pangruixin.domain.UserRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    @Insert("INSERT INTO user_role values(#{userId},#{roleId})")
    boolean addUserRole(UserRole userRole);

    @Select("select role_id from user_role where user_id=#{loginId}")
    List<Long> getRoleIdsByUserId(Object loginId);
}
