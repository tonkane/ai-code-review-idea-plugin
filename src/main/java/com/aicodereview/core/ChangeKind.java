package com.aicodereview.core;

public enum ChangeKind {
    ADDED("新增"),
    MODIFIED("修改"),
    DELETED("删除"),
    MOVED("重命名/移动");

    private final String label;

    ChangeKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
