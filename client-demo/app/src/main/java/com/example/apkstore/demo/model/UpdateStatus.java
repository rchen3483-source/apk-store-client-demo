package com.example.apkstore.demo.model;

/**
 * APK 更新状态。
 *
 * @author Codex
 * @date 2026-07-13
 */
public enum UpdateStatus {

    /**
     * 本机未安装。
     */
    NOT_INSTALLED("未安装"),

    /**
     * 本机低版本，可更新。
     */
    UPDATE_AVAILABLE("可更新"),

    /**
     * 本机已是最新版本。
     */
    LATEST("已最新"),

    /**
     * 本机版本高于平台版本。
     */
    LOCAL_NEWER("本地较新"),

    /**
     * 模拟安装完成。
     */
    INSTALLED("安装成功");

    private final String displayName;

    UpdateStatus(String displayName) {
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
