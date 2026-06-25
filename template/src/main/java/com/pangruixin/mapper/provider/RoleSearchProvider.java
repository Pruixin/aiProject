package com.pangruixin.mapper.provider;


import com.pangruixin.domain.dto.RoleSearchDto;

public class RoleSearchProvider {
    public String page(RoleSearchDto roleSearchDto){
        StringBuilder stringBuilder = new StringBuilder(
                "SELECT "+
                        " * "+
                        " FROM role "+
                        " WHERE " +
                        " role.deleted = 0 "
        );
        if (roleSearchDto.getName() !=null && !roleSearchDto.getName().trim().equals("")){
            stringBuilder.append(" and role.name like '%"+roleSearchDto.getName()+"%' ");
        }
        if (roleSearchDto.getCreateBy() != null){
            stringBuilder.append(" and role.create_by =  "+roleSearchDto.getCreateBy());
        }
        if (roleSearchDto.getUpdateBy() !=null){
            stringBuilder.append(" and role.update_by =   "+roleSearchDto.getUpdateBy());
        }
        stringBuilder.append(" order by role.update_time desc ");
        Integer page = roleSearchDto.getPage() != null ? roleSearchDto.getPage() : 1;
        Integer pageSize = roleSearchDto.getPageSize() != null ? roleSearchDto.getPageSize() : 10;
        stringBuilder.append(" limit " + ((page - 1) * pageSize) + "," + pageSize);
        return stringBuilder.toString();
    }
}
