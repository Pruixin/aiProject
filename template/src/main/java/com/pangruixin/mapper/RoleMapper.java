package com.pangruixin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pangruixin.domain.Role;
import com.pangruixin.domain.dto.RoleSearchDto;
import com.pangruixin.mapper.provider.RoleSearchProvider;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    @Select({"<script>",
        "select role_key from role where id in <foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id} </foreach>",
        "</script>"
    })
    List<String> getRoleKeyByUserId(List<Long> roleIds);


    @SelectProvider(value = RoleSearchProvider.class,method = "page")
    List<Role> rolePage(RoleSearchDto roleSearchDto);
}
