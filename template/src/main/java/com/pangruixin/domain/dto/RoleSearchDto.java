package com.pangruixin.domain.dto;

import lombok.Data;

@Data
public class RoleSearchDto {
    private Integer page;
    private Integer pageNum;
    private Integer pageSize;
    private String name;
    private Integer status;
    private Long createBy;
    private Long updateBy;
}
