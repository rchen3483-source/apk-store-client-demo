package com.example.apkstore.demo.model;

/**
 * 下载任务状态。
 *
 * @author Codex
 * @date 2026-07-13
 */
public enum TaskStatus {

    /**
     * 等待开始。
     */
    WAITING("等待中"),

    /**
     * 模拟下载中。
     */
    DOWNLOADING("下载中"),

    /**
     * 校验 SHA-256。
     */
    VERIFYING("校验中"),

    /**
     * 模拟安装中。
     */
    INSTALLING("安装中"),

    /**
     * 已完成。
     */
    DONE("已完成"),

    /**
     * 已失败。
     */
    FAILED("失败");

    private final String displayName;

    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取展示名称。
     *
     * @return 展示名称
     */
    public String getDisplayName() {
        return displayName;
    }
}
