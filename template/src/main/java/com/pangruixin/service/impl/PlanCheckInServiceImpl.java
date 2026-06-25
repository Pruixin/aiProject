package com.pangruixin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pangruixin.domain.PlanCheckIn;
import com.pangruixin.mapper.PlanCheckInMapper;
import com.pangruixin.service.PlanCheckInService;
import org.springframework.stereotype.Service;

@Service
public class PlanCheckInServiceImpl extends ServiceImpl<PlanCheckInMapper, PlanCheckIn> implements PlanCheckInService {
}