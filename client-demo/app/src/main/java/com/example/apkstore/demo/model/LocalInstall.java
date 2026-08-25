package com.example.apkstore.demo.model;

import java.io.Serializable;

/**
 * 本机安装状态。
 *
 * @author Codex
 * @date 2026-07-13
 */
public class LocalInstall implements Serializable {

    private static final long serialVersionUID = 1L;

    private String packageName;
    private String versionName;
    private Long versionCode;
    private Boolean installed;

    public LocalInstall(String packageName, String versionName, Long versionCode, Boolean installed) {
        this.packageName = packageName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.installed = installed;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getVersionName() {
        return versionName;
    }

    public Long getVersionCode() {
        return versionCode;
    }

    public Boolean getInstalled() {
        return installed;
    }

    public void install(String nextVersionName, Long nextVersionCode) {
        this.versionName = nextVersionName;
        this.versionCode = nextVersionCode;
        this.installed = Boolean.TRUE;
    }
}
