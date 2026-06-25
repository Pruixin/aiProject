package com.pangruixin.mapper.provider;


import com.pangruixin.domain.dto.UserSearchDto;

public class UserSearchProvider {
        public String page(UserSearchDto userSearchDto){
            StringBuilder stringBuilder = new StringBuilder(
                    "SELECT "+
                            " id ,"+
                            " phone ,"+
                            " sex ,"+
                            " avatar ,"+
                            " nick_name ,"+
                            " location ,"+
                            " enable ,"+
                            " create_by ,"+
                            " update_by ,"+
                            " create_time ,"+
                            " update_time "+
                            " FROM user "+
                            " WHERE 1 = 1 "
            );
            if (userSearchDto.getNickName()!=null && !userSearchDto.getNickName().trim().equals("")){
                stringBuilder.append(" and nick_name like '%"+userSearchDto.getNickName()+"%'");
            }
            if (userSearchDto.getPhone()!=null && !userSearchDto.getPhone().trim().equals("")){
                stringBuilder.append(" and phone = '"+userSearchDto.getPhone()+"'" );
            }
            if (userSearchDto.getEnable() != null){
                stringBuilder.append(" and enable = " + userSearchDto.getEnable());
            }
            stringBuilder.append(" order by update_time desc ");
            stringBuilder.append(" limit "+((userSearchDto.getPageNum()-1) * userSearchDto.getPageSize())+","+userSearchDto.getPageSize());
            return stringBuilder.toString();
        }
}
