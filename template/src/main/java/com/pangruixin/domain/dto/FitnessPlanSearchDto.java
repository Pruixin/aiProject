package com.pangruixin.domain.dto;

import lombok.Data;

@Data
public class FitnessPlanSearchDto {
    private String phone;
    private String nickName;
    private String goal;
    private Long userId;
    private long pageNum = 1;
    private long pageSize = 10;
}
