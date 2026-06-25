package com.pangruixin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.pangruixin.domain.User;
import com.pangruixin.domain.dto.UserSearchDto;
import com.pangruixin.mapper.provider.UserSearchProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface UserSearchMapper extends BaseMapper<UserSearchDto> {

    @SelectProvider(value = UserSearchProvider.class,method = "page")
    List<User> pageUser(UserSearchDto userSearchDto);
}
