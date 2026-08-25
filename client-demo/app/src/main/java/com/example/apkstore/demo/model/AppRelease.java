package com.example.apkstore.demo.model;

import java.io.Serializable;

/**
 * APK 版本信息。
 *
 * @author Codex
 * @date 2026-07-13
 */
public class AppRelease implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long releaseId;
    private String appCode;
    private String appName;
    private String packageName;
    private String envCode;
    private String channelCode;
    private String versionName;
    private Long versionCode;
    private String buildNo;
    private String apkUrl;
    private Long apkSize;
    private String sha256;
    private String releaseNotes;

    public AppRelease() {
        // Empty constructor for mock data assembly.
    }

    public Long getReleaseId() {
        return releaseId;
    }

    public String getAppCode() {
        return appCode;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getEnvCode() {
        return envCode;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public Long getVersionCode() {
        return versionCode;
    }

    public String getBuildNo() {
        return buildNo;
    }

    public String getApkUrl() {
        return apkUrl;
    }

    public Long getApkSize() {
        return apkSize;
    }

    public String getSha256() {
        return sha256;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public void setReleaseId(Long releaseId) {
        this.releaseId = releaseId;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void setEnvCode(String envCode) {
        this.envCode = envCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public void setVersionCode(Long versionCode) {
        this.versionCode = versionCode;
    }

    public void setBuildNo(String buildNo) {
        this.buildNo = buildNo;
    }

    public void setApkUrl(String apkUrl) {
        this.apkUrl = apkUrl;
    }

    public void setApkSize(Long apkSize) {
        this.apkSize = apkSize;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public void setReleaseNotes(String releaseNotes) {
        this.releaseNotes = releaseNotes;
    }
}
