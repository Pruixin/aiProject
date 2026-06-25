package com.pangruixin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.pangruixin.domain.Menu;
import com.pangruixin.domain.dto.MenuSearchDto;
import com.pangruixin.mapper.provider.MenuSearchProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Mapper
@Transactional
public interface MenuMapper extends BaseMapper<Menu> {
    
    @Select("SELECT `perms` from menu t1"+
            " INNER JOIN  role_menu t2 on t2.`menu_id`=t1.`id`"+
            " inner join role t3 on t3.`id`=t2.`role_id`"+
            " inner join user_role t4 on t4.`role_id`=t3.`id`"+
            " inner join user t5 on t5.`id`=t4.`user_id`"+
            "where t5.`id`=#{id}")
    List<String> getUserMenus(Long id);

    @Select("select perms from  menu where id in (" +
            "select menu_id from role_menu where role_id in (" +
            "select role_id from user_role where user_id=#{login}" +
            " )" +
            ")")
    List<String> getUserMenusList(Object loginId);

    @SelectProvider(value = MenuSearchProvider.class,method = "page")
    List<Menu> pageMenu(MenuSearchDto menuSearchDto);

    @Select("select count(*) from menu where perms=#{perms} and menu_type != 'M'")
    int isCommonPerms(String perms);


    @Select("select count(*) from menu where perms=#{perms} and menu_type != 'M' and id != #{id} and perms!= null")
    int updateIsHavePerms(Menu menu);

    @Select("select distinct id,parent_id,path,name,component,visible,status,perms,menu_type,icon,order_num " +
            "from menu " +
            "where deleted = 0 " +
            "and visible = 0 " +
            "and status = 0 " +
            "and menu_type in ('M','C') " +
            "and id in (" +
            "select menu_id from role_menu where role_id in (" +
            "select role_id from user_role where user_id=#{login}" +
            " )" +
            ") " +
            "order by order_num asc, id asc")
    List<Menu> getRouterList(long loginIdAsLong);

    @Select("select id,name from menu where menu_type='M' and deleted = 0")
    List<Menu> getMLOptions();

    @Select("select id,name from menu where menu_type='C' and deleted = 0")
    List<Menu> getCDOptions();

    @Select("select count(*) from menu where parent_id=#{id} and deleted = 0")
    int isHaveChild(Long id);

    @Select("select id from menu where parent_id=#{parentId} and deleted = 0")
    List<Long> findChildIds(Long parentId);
}
