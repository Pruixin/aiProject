package com.pangruixin.common.tree;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TreeUtil {

    public static <E> List<E> makeTree(
            List<E> list,
            Predicate<E> rootCheck,
            BiFunction<E, E, Boolean> parentCheck,
            BiConsumer<E, List<E>> setSubChildren
    ) {
        return list.stream()
                .filter(rootCheck) // 筛选根节点
                .peek(root -> setSubChildren.accept(root, makeChildren(root, list, parentCheck, setSubChildren)))
                .collect(Collectors.toList());
    }

    private static <E> List<E> makeChildren(
            E parent,
            List<E> allData,
            BiFunction<E, E, Boolean> parentCheck,
            BiConsumer<E, List<E>> setSubChildren
    ) {
        return allData.stream()
                .filter(child -> parentCheck.apply(parent, child)) // 筛选子节点
                .peek(child -> setSubChildren.accept(child, makeChildren(child, allData, parentCheck, setSubChildren)))
                .collect(Collectors.toList());
    }

}
