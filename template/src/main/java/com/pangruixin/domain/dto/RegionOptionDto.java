package com.pangruixin.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RegionOptionDto {
    private String value;

    private String label;

    private List<RegionOptionDto> children = new ArrayList<>();
}
