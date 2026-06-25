package com.pangruixin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pangruixin.domain.Dict;
import com.pangruixin.mapper.DictMapper;
import com.pangruixin.service.DictService;
import org.springframework.stereotype.Service;

@Service
public class DictServiceImpl extends ServiceImpl<DictMapper, Dict> implements DictService {
}
