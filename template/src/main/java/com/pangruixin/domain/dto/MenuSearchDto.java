package com.pangruixin.domain.dto;

import lombok.Data;

@Data
public class MenuSearchDto {
    private Integer page;
    private Integer pageSize;
    private String name;
    private Long createBy;
    private Long updateBy;
}
