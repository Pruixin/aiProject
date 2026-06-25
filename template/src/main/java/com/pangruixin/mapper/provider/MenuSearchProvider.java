package com.pangruixin.mapper.provider;


import com.pangruixin.domain.dto.MenuSearchDto;

public class MenuSearchProvider {

    public  String page(MenuSearchDto menuSearchDto){
        StringBuilder stringBuilder = new StringBuilder(
                "SELECT "+
                        " * "+
                        " FROM menu "+
                        " WHERE " +
                        " deleted = 0 "
        );
        if (menuSearchDto.getName() !=null && !menuSearchDto.getName().trim().equals("")){
            stringBuilder.append(" and name like '%"+menuSearchDto.getName()+"%' ");
        }
        if (menuSearchDto.getCreateBy() != null){
            stringBuilder.append(" and create_by =  "+menuSearchDto.getCreateBy());
        }
        if (menuSearchDto.getUpdateBy() !=null){
            stringBuilder.append(" and update_by =   "+menuSearchDto.getUpdateBy());
        }
        stringBuilder.append(" order by update_time desc ");
        stringBuilder.append(" limit " + ((menuSearchDto.getPage()-1) * menuSearchDto.getPageSize())+","+menuSearchDto.getPageSize());
        return stringBuilder.toString();
    }
}
