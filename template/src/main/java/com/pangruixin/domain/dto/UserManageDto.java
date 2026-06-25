package com.pangruixin.domain.dto;

import lombok.Data;

@Data
public class UserManageDto {
    private Long id;

    private String phone;

    private String password;

    private Integer sex;

    private String avatar;

    private String nickName;

    private String location;

    private Integer enable;
}
