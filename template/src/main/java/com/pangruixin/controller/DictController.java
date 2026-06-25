package com.pangruixin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pangruixin.common.R;
import com.pangruixin.domain.Dict;
import com.pangruixin.domain.dto.RegionOptionDto;
import com.pangruixin.service.DictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dict")
public class DictController {

    @Autowired
    private DictService dictService;

    @GetMapping("/region/options")
    public R<List<RegionOptionDto>> getRegionOptions() {
        // 地区字典接口把平铺的 dict 记录转换成前端 Cascader 需要的树形结构。
        LambdaQueryWrapper<Dict> wrapper = new LambdaQueryWrapper<>();
        // 只查询地区类型且未删除的字典项。
        wrapper.eq(Dict::getDictType, "region")
                .eq(Dict::getDeleted, 0)
                .orderByAsc(Dict::getId);
        List<Dict> regions = dictService.list(wrapper);

        // childrenMap: parentCode -> 子节点列表，用于后续递归组树。
        Map<String, List<Dict>> childrenMap = regions.stream()
                .collect(Collectors.groupingBy(item -> normalizeParentCode(item.getParentCode())));
        // codeSet 用于判断某条记录的 parentCode 是否真的存在。
        Set<String> codeSet = regions.stream()
                .map(Dict::getDictCode)
                .collect(Collectors.toCollection(HashSet::new));

        List<RegionOptionDto> roots = new ArrayList<>();
        for (Dict region : regions) {
            String parentCode = normalizeParentCode(region.getParentCode());
            if (isRoot(parentCode, codeSet)) {
                // 只有根节点才作为 cascader 第一层入口。
                roots.add(toOption(region, childrenMap));
            }
        }
        return R.success(roots);
    }

    private RegionOptionDto toOption(Dict dict, Map<String, List<Dict>> childrenMap) {
        // 递归组装 { value, label, children } 结构。
        RegionOptionDto option = new RegionOptionDto();
        option.setValue(dict.getDictCode());
        option.setLabel(dict.getDictName());

        List<Dict> children = childrenMap.getOrDefault(dict.getDictCode(), List.of());
        if (!children.isEmpty()) {
            // 有子节点时继续递归，否则 children 保持为空即可。
            option.setChildren(children.stream()
                    .map(child -> toOption(child, childrenMap))
                    .collect(Collectors.toList()));
        }
        return option;
    }

    private String normalizeParentCode(String parentCode) {
        // 统一清洗 parentCode，便于后续按 parentCode 分组构树。
        return parentCode == null ? "" : parentCode.trim();
    }

    private boolean isRoot(String parentCode, Set<String> codeSet) {
        // 某些历史数据的根节点 parentCode 可能是 "", 0, -1, null 或者脏值，
        // 这里统一认定为根，增强地区字典兼容性。
        return parentCode.isEmpty()
                || "0".equals(parentCode)
                || "-1".equals(parentCode)
                || "null".equalsIgnoreCase(parentCode)
                || !codeSet.contains(parentCode);
    }
}
