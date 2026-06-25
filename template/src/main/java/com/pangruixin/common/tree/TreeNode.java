package com.pangruixin.common.tree;

import java.util.List;

public interface TreeNode<T> {
    T getId(); // 获取节点ID
    T getParentId(); // 获取父节点ID
    void setChildren(List<TreeNode<T>> children); // 设置子节点列表
    List<TreeNode<T>> getChildren(); // 获取子节点列表
}
