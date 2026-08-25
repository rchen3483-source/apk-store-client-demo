package com.example.apkstore.demo.mock;

import com.example.apkstore.demo.model.AppRelease;
import com.example.apkstore.demo.model.LocalInstall;
import com.example.apkstore.demo.model.OperationLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Mock 数据仓库，替代真实服务端交互。
 *
 * @author Codex
 * @date 2026-07-13
 */
public class MockDataProvider {

    private final List<AppRelease> releaseList = new ArrayList<>(8);
    private final Map<String, LocalInstall> localInstallMap = new HashMap<>(8);
    private final List<OperationLog> operationLogList = new ArrayList<>(16);

    public MockDataProvider() {
        reset();
    }

    /**
     * 重置 mock 数据。
     */
    public final void reset() {
        releaseList.clear();
        localInstallMap.clear();
        operationLogList.clear();
        initReleases();
        initLocalInstalls();
        addLog("系统初始化", "已加载本地 mock 应用、版本和安装状态");
    }

    /**
     * 获取版本列表。
     *
     * @return 版本列表
     */
    public List<AppRelease> getReleaseList() {
        return releaseList;
    }

    /**
     * 获取每个产品和环境下的最新版本列表。
     *
     * @return 最新版本列表
     */
    public List<AppRelease> getLatestReleaseList() {
        Map<String, AppRelease> latestMap = new LinkedHashMap<>(releaseList.size());
        for (AppRelease release : releaseList) {
            String key = buildReleaseGroupKey(release);
            AppRelease current = latestMap.get(key);
            if (current == null || release.getVersionCode().compareTo(current.getVersionCode()) > 0) {
                latestMap.put(key, release);
            }
        }
        List<AppRelease> latestList = new ArrayList<>(latestMap.values());
        sortReleaseList(latestList);
        return latestList;
    }

    /**
     * 获取同产品、环境下的历史版本。
     *
     * @param baseRelease 当前版本
     * @return 历史版本列表
     */
    public List<AppRelease> getHistoryReleaseList(AppRelease baseRelease) {
        List<AppRelease> historyList = new ArrayList<>(releaseList.size());
        for (AppRelease release : releaseList) {
            if (isSameReleaseGroup(baseRelease, release)
                    && !release.getReleaseId().equals(baseRelease.getReleaseId())) {
                historyList.add(release);
            }
        }
        sortReleaseList(historyList);
        return historyList;
    }

    /**
     * 获取上一个安装版本。
     *
     * @param baseRelease 当前版本
     * @return 上一个安装版本；不存在时返回 null
     */
    public AppRelease getPreviousInstallRelease(AppRelease baseRelease) {
        LocalInstall install = getLocalInstall(baseRelease.getPackageName());
        if (!Boolean.TRUE.equals(install.getInstalled())) {
            return null;
        }
        for (AppRelease release : releaseList) {
            if (isSameReleaseGroup(baseRelease, release)
                    && release.getVersionCode().equals(install.getVersionCode())
                    && !release.getReleaseId().equals(baseRelease.getReleaseId())) {
                return release;
            }
        }
        AppRelease candidate = null;
        for (AppRelease release : releaseList) {
            if (isSameReleaseGroup(baseRelease, release)
                    && release.getVersionCode().compareTo(install.getVersionCode()) < 0
                    && !release.getReleaseId().equals(baseRelease.getReleaseId())
                    && (candidate == null || release.getVersionCode().compareTo(candidate.getVersionCode()) > 0)) {
                candidate = release;
            }
        }
        return candidate;
    }

    /**
     * 获取操作日志列表。
     *
     * @return 操作日志列表
     */
    public List<OperationLog> getOperationLogList() {
        return operationLogList;
    }

    /**
     * 获取产品选择项，每个 appCode 保留一项。
     *
     * @return 产品列表
     */
    public List<AppRelease> getProductOptions() {
        Map<String, AppRelease> productMap = new LinkedHashMap<>(releaseList.size());
        for (AppRelease release : releaseList) {
            if (!productMap.containsKey(release.getAppCode())) {
                productMap.put(release.getAppCode(), release);
            }
        }
        return new ArrayList<>(productMap.values());
    }

    /**
     * 获取产品可选环境。
     *
     * @param appCode 产品编码
     * @return 环境编码列表
     */
    public List<String> getEnvironmentOptions(String appCode) {
        Set<String> environmentSet = new LinkedHashSet<>(releaseList.size());
        for (AppRelease release : releaseList) {
            if (appCode.equals(release.getAppCode())) {
                environmentSet.add(release.getEnvCode());
            }
        }
        return new ArrayList<>(environmentSet);
    }

    /**
     * 获取本机安装状态。
     *
     * @param packageName 包名
     * @return 本机安装状态
     */
    public LocalInstall getLocalInstall(String packageName) {
        LocalInstall install = localInstallMap.get(packageName);
        if (install != null) {
            return install;
        }
        return new LocalInstall(packageName, "-", 0L, Boolean.FALSE);
    }

    /**
     * 更新本机安装版本。
     *
     * @param release 版本信息
     */
    public void installRelease(AppRelease release) {
        LocalInstall install = getLocalInstall(release.getPackageName());
        install.install(release.getVersionName(), release.getVersionCode());
        localInstallMap.put(release.getPackageName(), install);
        addLog("安装成功", release.getAppName() + " 已更新到 " + release.getVersionName());
    }

    /**
     * 添加操作日志。
     *
     * @param title 标题
     * @param content 内容
     */
    public void addLog(String title, String content) {
        operationLogList.add(0, new OperationLog(now(), title, content));
    }

    private void initReleases() {
        releaseList.add(createTranslationTestRelease());
        releaseList.add(createTranslationTestHistoryRelease(10011L, "2.7.0", 2070001L,
                "build-20260701-006", 101711872L));
        releaseList.add(createTranslationTestHistoryRelease(10012L, "2.6.5", 2060500L,
                "build-20260618-014", 100663296L));
        releaseList.add(createDictDevRelease());
        releaseList.add(createDictDevHistoryRelease());
        releaseList.add(createStudyTestRelease());
        releaseList.add(createStudyTestHistoryRelease());
        releaseList.add(createMeetingTestRelease());
        releaseList.add(createTranslationCustomerRelease());
        releaseList.add(createVoiceDemoTestRelease());
        releaseList.add(createVoiceDemoTestHistoryRelease());
        releaseList.add(createVoiceSitTestRelease());
    }

    private AppRelease createTranslationTestRelease() {
        AppRelease release = new AppRelease();
        release.setReleaseId(10001L);
        release.setAppCode("translation_app");
        release.setAppName("翻译 App");
        release.setPackageName("com.company.translation.test");
        release.setEnvCode("test");
        release.setChannelCode("internal");
        release.setVersionName("2.8.0");
        release.setVersionCode(2080001L);
        release.setBuildNo("build-20260710-001");
        release.setApkSize(104857600L);
        fillDerivedFields(release);
        return release;
    }

    private AppRelease createDictDevRelease() {
        AppRelease release = new AppRelease();
        release.setReleaseId(10002L);
        release.setAppCode("dict_app");
        release.setAppName("词典 App");
        release.setPackageName("com.company.dict.dev");
        release.setEnvCode("dev");
        release.setChannelCode("debug");
        release.setVersionName("1.14.2");
        release.setVersionCode(1140200L);
        release.setBuildNo("build-20260710-017");
        release.setApkSize(86245312L);
        fillDerivedFields(release);
        return release;
    }

    private AppRelease createDictDevHistoryRelease() {
        AppRelease release = new AppRelease();
        release.setReleaseId(10013L);
        release.setAppCode("dict_app");
        release.setAppName("词典 App");
        release.setPackageName("com.company.dict.dev");
        release.setEnvCode("dev");
        release.setChannelCode("debug");
        release.setVersionName("1.13.9");
        release.setVersionCode(1130900L);
        release.setBuildNo("build-20260628-011");
        release.setApkSize(84934656L);
        fillDerivedFields(release);
        return release;
    }

    private AppRelease createStudyTestRelease() {
        AppRelease release = new AppRelease();
        release.setReleaseId(10003L);
        release.setAppCode("study_center");
        release.setAppName("学习中心");
        release.setPackageName("com.company.study.test");
        release.setEnvCode("test");
        release.setChannelCode("internal");
        release.setVersionName("4.0.0");
        release.setVersionCode(4000000L);
        release.setBuildNo("build-20260710-033");
        release.setApkSize(129433600L);
        fillDerivedFields(release);
        return release;
    }

    private AppRelease createStudyTestHistoryRelease() {
        AppRelease release = new AppRelease();
        release.setReleaseId(10014L);
        release.setAppCode("study_center");
        release.setAppName("学习中心");
        release.setPackageName("com.company.study.test");
        release.setEnvCode("test");
        release.setChannelCode("internal");
        release.setVersionName("3.9.8");
        release.setVersionCode(3090800L);
        release.setBuildNo("build-20260625-009");
        release.setApkSize(126877696L);
        fillDerivedFields(release);
        return release;
    }

    private AppRelease createMeetingTestRelease() {
        AppRelease release = new AppRelease();
        release.setReleaseId(10004L);
        release.setAppCode("meeting_app");
        release.setAppName("会议助手");
        release.setPackageName("com.company.meeting.test");
        release.setEnvCode("test");
        release.setChannelCode("internal");
        release.setVersionName("1.6.3");
        release.setVersionCode(1060301L);
        release.setBuildNo("build-20260710-044");
        release.setApkSize(73400320L);
        fillDerivedFields(release);
        return release;
    }

    private AppRelease createTranslationCustomerRelease() {
        AppRelease release = new AppRelease();
        release.setReleaseId(10005L);
        release.setAppCode("translation_app");
        release.setAppName("翻译 App");
        release.setPackageName("com.company.translation.customer");
        release.setEnvCode("prod");
        release.setChannelCode("customer-a");
        release.setVersionName("2.8.0");
        release.setVersionCode(2080002L);
        release.setBuildNo("build-20260710-039");
        release.setApkSize(106954752L);
        fillDerivedFields(release);
        return release;
    }

    private AppRelease createVoiceDemoTestRelease() {
        AppRelease release = new AppRelease();
        release.setReleaseId(10006L);
        release.setAppCode("voice_assistant");
        release.setAppName("语音助手");
        release.setPackageName("com.company.voice.demo");
        release.setEnvCode("test");
        release.setChannelCode("demo");
        release.setVersionName("3.2.1");
        release.setVersionCode(3020104L);
        release.setBuildNo("build-20260710-021");
        release.setApkSize(94371840L);
        fillDerivedFields(release);
        return release;
    }

    private AppRelease createVoiceSitTestRelease() {
        AppRelease release = new AppRelease();
        release.setReleaseId(10016L);
        release.setAppCode("voice_assistant");
        release.setAppName("语音助手");
        release.setPackageName("com.company.voice.sit");
        release.setEnvCode("sit_test");
        release.setChannelCode("internal");
        release.setVersionName("3.2.0-headset");
        release.setVersionCode(3020008L);
        release.setBuildNo("build-20260710-052");
        release.setApkSize(97517568L);
        fillDerivedFields(release);
        return release;
    }

    private AppRelease createTranslationTestHistoryRelease(Long releaseId, String versionName, Long versionCode,
            String buildNo, Long apkSize) {
        AppRelease release = new AppRelease();
        release.setReleaseId(releaseId);
        release.setAppCode("translation_app");
        release.setAppName("翻译 App");
        release.setPackageName("com.company.translation.test");
        release.setEnvCode("test");
        release.setChannelCode("internal");
        release.setVersionName(versionName);
        release.setVersionCode(versionCode);
        release.setBuildNo(buildNo);
        release.setApkSize(apkSize);
        fillDerivedFields(release);
        return release;
    }

    private AppRelease createVoiceDemoTestHistoryRelease() {
        AppRelease release = new AppRelease();
        release.setReleaseId(10015L);
        release.setAppCode("voice_assistant");
        release.setAppName("语音助手");
        release.setPackageName("com.company.voice.demo");
        release.setEnvCode("test");
        release.setChannelCode("demo");
        release.setVersionName("3.1.8");
        release.setVersionCode(3010800L);
        release.setBuildNo("build-20260630-018");
        release.setApkSize(92274688L);
        fillDerivedFields(release);
        return release;
    }

    private void fillDerivedFields(AppRelease release) {
        release.setApkUrl("https://artifact.example.com/apk/" + release.getAppCode()
                + "-" + release.getEnvCode() + ".apk");
        release.setSha256("mock-sha256-" + release.getAppCode() + "-" + release.getVersionCode());
        release.setReleaseNotes("这是 " + release.getAppName() + " 的 " + release.getEnvCode()
                + " 环境测试包。");
    }

    private void initLocalInstalls() {
        localInstallMap.put("com.company.translation.test",
                new LocalInstall("com.company.translation.test", "2.7.0", 2070001L, Boolean.TRUE));
        localInstallMap.put("com.company.dict.dev",
                new LocalInstall("com.company.dict.dev", "1.14.2", 1140200L, Boolean.TRUE));
        localInstallMap.put("com.company.study.test",
                new LocalInstall("com.company.study.test", "4.0.1", 4000001L, Boolean.TRUE));
        localInstallMap.put("com.company.meeting.test",
                new LocalInstall("com.company.meeting.test", "-", 0L, Boolean.FALSE));
        localInstallMap.put("com.company.translation.customer",
                new LocalInstall("com.company.translation.customer", "-", 0L, Boolean.FALSE));
        localInstallMap.put("com.company.voice.demo",
                new LocalInstall("com.company.voice.demo", "3.1.8", 3010800L, Boolean.TRUE));
        localInstallMap.put("com.company.voice.sit",
                new LocalInstall("com.company.voice.sit", "-", 0L, Boolean.FALSE));
    }

    private String now() {
        return new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
    }

    private boolean isSameReleaseGroup(AppRelease left, AppRelease right) {
        return left.getAppCode().equals(right.getAppCode())
                && left.getPackageName().equals(right.getPackageName())
                && left.getEnvCode().equals(right.getEnvCode());
    }

    private String buildReleaseGroupKey(AppRelease release) {
        return release.getAppCode() + "|" + release.getPackageName() + "|" + release.getEnvCode();
    }

    private void sortReleaseList(List<AppRelease> targetList) {
        Collections.sort(targetList, new Comparator<AppRelease>() {
            @Override
            public int compare(AppRelease first, AppRelease second) {
                return second.getVersionCode().compareTo(first.getVersionCode());
            }
        });
    }
}

