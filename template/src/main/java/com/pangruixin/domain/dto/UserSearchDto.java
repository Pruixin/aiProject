package com.pangruixin.domain.dto;

import lombok.Data;

@Data
public class UserSearchDto {

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String nickName;
    private String phone;
    private Integer enable;
}
