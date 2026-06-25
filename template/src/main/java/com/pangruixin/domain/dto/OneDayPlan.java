package com.pangruixin.domain.dto;

import lombok.Data;

@Data
public class OneDayPlan {
    private Diet diet;
    private Motion Motion; // 注意你JSON里是大写M，保持一致
}