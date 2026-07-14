package com.example.apkstore.demo;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * APK store client demo with mock data only.
 *
 * @author Codex
 * @date 2026-07-14
 */
public class MainActivity extends Activity {

    private static final int COLOR_PRIMARY = Color.rgb(37, 99, 235);
    private static final int COLOR_BACKGROUND = Color.rgb(245, 247, 251);
    private static final int COLOR_PANEL = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_MUTED = Color.rgb(101, 112, 131);
    private static final String TAB_STORE = "store";
    private static final String TAB_TASK = "task";
    private static final String TAB_LOG = "log";
    private static final String TAB_SETTING = "setting";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ReleaseInfo> releaseList = new ArrayList<>(8);
    private final List<LocalInstallInfo> localInstallList = new ArrayList<>(8);
    private final List<DownloadTaskInfo> taskList = new ArrayList<>(8);
    private final List<String> logList = new ArrayList<>(16);

    private LinearLayout rootLayout;
    private LinearLayout contentLayout;
    private String activeTab = TAB_STORE;
    private String envFilter = "全部";
    private String channelFilter = "全部";
    private String statusFilter = "全部";
    private long taskSequence = 1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        resetData();
        showLoginPage();
    }

    private void showLoginPage() {
        LinearLayout layout = verticalLayout();
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(dp(24), dp(24), dp(24), dp(24));
        layout.setBackgroundColor(COLOR_BACKGROUND);
        layout.addView(text("内部 APK 商店 Demo", 28, COLOR_TEXT));
        layout.addView(text("本 APK 仅使用本地 mock 数据，不连接真实服务端。", 14, COLOR_MUTED));
        Button button = primaryButton("模拟企业登录");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addLog("模拟登录成功");
                showMainPage();
            }
        });
        layout.addView(button, fullWidthParams());
        setContentView(layout);
    }

    private void showMainPage() {
        rootLayout = verticalLayout();
        rootLayout.setBackgroundColor(COLOR_BACKGROUND);
        rootLayout.addView(createHeader());
        rootLayout.addView(createTabs());
        contentLayout = verticalLayout();
        rootLayout.addView(contentLayout, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(rootLayout);
        renderContent();
    }

    private View createHeader() {
        LinearLayout header = verticalLayout();
        header.setPadding(dp(16), dp(14), dp(16), dp(12));
        header.setBackgroundColor(COLOR_PANEL);
        header.addView(text("APK 商店客户端", 22, COLOR_TEXT));
        header.addView(text("研发测试用户 · Mock 模式 · 多环境多渠道", 12, COLOR_MUTED));
        return header;
    }

    private View createTabs() {
        LinearLayout tabs = horizontalLayout();
        tabs.setPadding(dp(8), dp(8), dp(8), dp(8));
        tabs.setBackgroundColor(COLOR_PANEL);
        tabs.addView(tabButton("应用", TAB_STORE));
        tabs.addView(tabButton("任务", TAB_TASK));
        tabs.addView(tabButton("日志", TAB_LOG));
        tabs.addView(tabButton("设置", TAB_SETTING));
        return tabs;
    }

    private Button tabButton(String label, final String tab) {
        Button button = secondaryButton(label);
        button.setTextColor(tab.equals(activeTab) ? Color.WHITE : COLOR_PRIMARY);
        button.setBackgroundColor(tab.equals(activeTab) ? COLOR_PRIMARY : Color.WHITE);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                activeTab = tab;
                showMainPage();
            }
        });
        button.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1));
        return button;
    }

    private void renderContent() {
        contentLayout.removeAllViews();
        if (TAB_STORE.equals(activeTab)) {
            renderStore();
        } else if (TAB_TASK.equals(activeTab)) {
            renderTasks();
        } else if (TAB_LOG.equals(activeTab)) {
            renderLogs();
        } else {
            renderSettings();
        }
    }

    private void renderStore() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout page = verticalLayout();
        page.setPadding(dp(12), dp(12), dp(12), dp(20));
        page.addView(createFilterCard());
        List<ReleaseInfo> filteredList = filterReleases();
        page.addView(text("共 " + filteredList.size() + " 个 APK 版本", 12, COLOR_MUTED));
        for (ReleaseInfo releaseInfo : filteredList) {
            page.addView(createReleaseCard(releaseInfo));
        }
        scrollView.addView(page);
        contentLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, -1));
    }

    private View createFilterCard() {
        LinearLayout card = cardLayout();
        card.addView(text("快速筛选", 18, COLOR_TEXT));
        card.addView(buttonRow("环境", new String[]{"全部", "dev", "test", "pre"}, envFilter, new ValueCallback() {
            @Override
            public void onValue(String value) {
                envFilter = value;
                renderContent();
            }
        }));
        card.addView(buttonRow("渠道", new String[]{"全部", "internal", "debug", "demo", "customer-a"}, channelFilter,
                new ValueCallback() {
                    @Override
                    public void onValue(String value) {
                        channelFilter = value;
                        renderContent();
                    }
                }));
        card.addView(buttonRow("状态", new String[]{"全部", "未安装", "可更新", "已最新", "本地较新"}, statusFilter,
                new ValueCallback() {
                    @Override
                    public void onValue(String value) {
                        statusFilter = value;
                        renderContent();
                    }
                }));
        return card;
    }

    private View buttonRow(String label, String[] values, String selectedValue, final ValueCallback callback) {
        LinearLayout box = verticalLayout();
        box.addView(text(label, 12, COLOR_MUTED));
        LinearLayout row = horizontalLayout();
        for (final String value : values) {
            Button button = secondaryButton(value);
            button.setTextColor(value.equals(selectedValue) ? Color.WHITE : COLOR_PRIMARY);
            button.setBackgroundColor(value.equals(selectedValue) ? COLOR_PRIMARY : Color.WHITE);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    callback.onValue(value);
                }
            });
            row.addView(button, new LinearLayout.LayoutParams(0, dp(38), 1));
        }
        box.addView(row);
        return box;
    }

    private List<ReleaseInfo> filterReleases() {
        List<ReleaseInfo> resultList = new ArrayList<>(releaseList.size());
        for (ReleaseInfo releaseInfo : releaseList) {
            String status = statusOf(releaseInfo);
            boolean envMatched = "全部".equals(envFilter) || envFilter.equals(releaseInfo.envCode);
            boolean channelMatched = "全部".equals(channelFilter) || channelFilter.equals(releaseInfo.channelCode);
            boolean statusMatched = "全部".equals(statusFilter) || statusFilter.equals(status);
            if (envMatched && channelMatched && statusMatched) {
                resultList.add(releaseInfo);
            }
        }
        return resultList;
    }

    private View createReleaseCard(final ReleaseInfo releaseInfo) {
        LinearLayout card = cardLayout();
        LocalInstallInfo localInstallInfo = localOf(releaseInfo.packageName);
        String status = statusOf(releaseInfo);
        card.addView(text(releaseInfo.appName + " · " + status, 18, COLOR_TEXT));
        card.addView(text(releaseInfo.envCode + " / " + releaseInfo.channelCode
                + " · " + releaseInfo.versionName + " / " + releaseInfo.versionCode, 14, COLOR_TEXT));
        card.addView(text("包名：" + releaseInfo.packageName, 12, COLOR_MUTED));
        card.addView(text("本机：" + localInstallInfo.versionName + " / " + localInstallInfo.versionCode, 12, COLOR_MUTED));
        card.addView(text("构建号：" + releaseInfo.buildNo, 12, COLOR_MUTED));
        card.addView(text("APK 大小：" + formatSize(releaseInfo.apkSize), 12, COLOR_MUTED));
        card.addView(text("SHA-256：" + releaseInfo.sha256, 12, COLOR_MUTED));
        card.addView(text("更新说明：" + releaseInfo.releaseNotes, 14, COLOR_TEXT));
        Button actionButton = primaryButton(actionText(status));
        actionButton.setEnabled("未安装".equals(status) || "可更新".equals(status));
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDownload(releaseInfo);
            }
        });
        card.addView(actionButton, fullWidthParams());
        return card;
    }

    private void renderTasks() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout page = verticalLayout();
        page.setPadding(dp(12), dp(12), dp(12), dp(20));
        page.addView(text("下载任务", 22, COLOR_TEXT));
        if (taskList.isEmpty()) {
            page.addView(text("暂无任务，请从应用页点击安装或更新。", 14, COLOR_MUTED));
        }
        for (DownloadTaskInfo taskInfo : taskList) {
            LinearLayout card = cardLayout();
            card.addView(text(taskInfo.releaseInfo.appName + " · " + taskInfo.status, 18, COLOR_TEXT));
            card.addView(text(taskInfo.message, 12, COLOR_MUTED));
            ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(taskInfo.progress);
            card.addView(progressBar, fullWidthParams());
            card.addView(text("进度：" + taskInfo.progress + "%", 12, COLOR_MUTED));
            page.addView(card);
        }
        scrollView.addView(page);
        contentLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, -1));
    }

    private void renderLogs() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout page = verticalLayout();
        page.setPadding(dp(12), dp(12), dp(12), dp(20));
        page.addView(text("操作日志", 22, COLOR_TEXT));
        for (String log : logList) {
            LinearLayout card = cardLayout();
            card.addView(text(log, 14, COLOR_TEXT));
            page.addView(card);
        }
        scrollView.addView(page);
        contentLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, -1));
    }

    private void renderSettings() {
        LinearLayout page = verticalLayout();
        page.setPadding(dp(12), dp(12), dp(12), dp(20));
        page.addView(text("设置", 22, COLOR_TEXT));
        LinearLayout card = cardLayout();
        card.addView(text("当前模式", 18, COLOR_TEXT));
        card.addView(text("Mock 数据，不请求真实服务端，不真实下载 APK，不调起系统安装器。", 14, COLOR_MUTED));
        page.addView(card);
        Button resetButton = primaryButton("重置 Mock 数据");
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetData();
                activeTab = TAB_STORE;
                showMainPage();
            }
        });
        page.addView(resetButton, fullWidthParams());
        contentLayout.addView(page, new LinearLayout.LayoutParams(-1, -1));
    }

    private void startDownload(ReleaseInfo releaseInfo) {
        DownloadTaskInfo taskInfo = new DownloadTaskInfo();
        taskInfo.taskId = taskSequence++;
        taskInfo.releaseInfo = releaseInfo;
        taskInfo.status = "等待中";
        taskInfo.message = "准备下载";
        taskInfo.progress = 0;
        taskList.add(0, taskInfo);
        addLog("创建下载任务：" + releaseInfo.appName);
        activeTab = TAB_TASK;
        showMainPage();
        scheduleNext(taskInfo);
    }

    private void scheduleNext(final DownloadTaskInfo taskInfo) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                advanceTask(taskInfo);
            }
        }, 450L);
    }

    private void advanceTask(DownloadTaskInfo taskInfo) {
        if (taskInfo.progress < 100) {
            taskInfo.progress += 20;
            taskInfo.status = "下载中";
            taskInfo.message = "模拟下载 APK 文件";
            renderContentIfTaskTab();
            scheduleNext(taskInfo);
            return;
        }
        verifyTask(taskInfo);
    }

    private void verifyTask(final DownloadTaskInfo taskInfo) {
        taskInfo.status = "校验中";
        taskInfo.message = "正在校验 SHA-256";
        addLog("SHA-256 校验通过：" + taskInfo.releaseInfo.appName);
        renderContentIfTaskTab();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                completeInstall(taskInfo);
            }
        }, 700L);
    }

    private void completeInstall(DownloadTaskInfo taskInfo) {
        taskInfo.status = "已完成";
        taskInfo.message = "模拟安装成功，已上报结果";
        LocalInstallInfo localInstallInfo = localOf(taskInfo.releaseInfo.packageName);
        localInstallInfo.installed = true;
        localInstallInfo.versionName = taskInfo.releaseInfo.versionName;
        localInstallInfo.versionCode = taskInfo.releaseInfo.versionCode;
        addLog("安装成功并上报：releaseId=" + taskInfo.releaseInfo.releaseId);
        renderContentIfTaskTab();
    }

    private void renderContentIfTaskTab() {
        if (TAB_TASK.equals(activeTab)) {
            renderContent();
        }
    }

    private String statusOf(ReleaseInfo releaseInfo) {
        LocalInstallInfo localInstallInfo = localOf(releaseInfo.packageName);
        if (!localInstallInfo.installed) {
            return "未安装";
        }
        if (localInstallInfo.versionCode < releaseInfo.versionCode) {
            return "可更新";
        }
        if (localInstallInfo.versionCode > releaseInfo.versionCode) {
            return "本地较新";
        }
        return "已最新";
    }

    private LocalInstallInfo localOf(String packageName) {
        for (LocalInstallInfo localInstallInfo : localInstallList) {
            if (localInstallInfo.packageName.equals(packageName)) {
                return localInstallInfo;
            }
        }
        LocalInstallInfo localInstallInfo = new LocalInstallInfo(packageName, "-", 0L, false);
        localInstallList.add(localInstallInfo);
        return localInstallInfo;
    }

    private String actionText(String status) {
        if ("未安装".equals(status)) {
            return "安装";
        }
        if ("可更新".equals(status)) {
            return "更新";
        }
        return status;
    }

    private void resetData() {
        releaseList.clear();
        localInstallList.clear();
        taskList.clear();
        logList.clear();
        seedReleaseData();
        seedInstallData();
        addLog("Mock 数据已初始化");
    }

    private void seedReleaseData() {
        addRelease(10001L, "翻译 App", "translation_app", "com.company.translation.test", "test", "internal", "2.8.0", 2080001L);
        addRelease(10002L, "词典 App", "dict_app", "com.company.dict.dev", "dev", "debug", "1.14.2", 1140200L);
        addRelease(10003L, "学习中心", "study_center", "com.company.study.test", "test", "internal", "4.0.0", 4000000L);
        addRelease(10004L, "会议助手", "meeting_app", "com.company.meeting.test", "test", "internal", "1.6.3", 1060301L);
        addRelease(10005L, "翻译 App", "translation_app", "com.company.translation.customer", "pre", "customer-a", "2.8.0", 2080002L);
        addRelease(10006L, "语音助手", "voice_assistant", "com.company.voice.pre", "pre", "demo", "3.2.1", 3020104L);
    }

    private void addRelease(Long releaseId, String appName, String appCode, String packageName, String envCode,
            String channelCode, String versionName, Long versionCode) {
        ReleaseInfo releaseInfo = new ReleaseInfo();
        releaseInfo.releaseId = releaseId;
        releaseInfo.appName = appName;
        releaseInfo.appCode = appCode;
        releaseInfo.packageName = packageName;
        releaseInfo.envCode = envCode;
        releaseInfo.channelCode = channelCode;
        releaseInfo.versionName = versionName;
        releaseInfo.versionCode = versionCode;
        releaseInfo.buildNo = "build-20260710-" + releaseId;
        releaseInfo.apkSize = 80L * 1024L * 1024L + releaseId;
        releaseInfo.sha256 = "mock-sha256-" + appCode + "-" + versionCode;
        releaseInfo.releaseNotes = "用于研发测试的 " + envCode + "/" + channelCode + " APK 包。";
        releaseList.add(releaseInfo);
    }

    private void seedInstallData() {
        localInstallList.add(new LocalInstallInfo("com.company.translation.test", "2.7.0", 2070001L, true));
        localInstallList.add(new LocalInstallInfo("com.company.dict.dev", "1.14.2", 1140200L, true));
        localInstallList.add(new LocalInstallInfo("com.company.study.test", "4.0.1", 4000001L, true));
        localInstallList.add(new LocalInstallInfo("com.company.meeting.test", "-", 0L, false));
        localInstallList.add(new LocalInstallInfo("com.company.translation.customer", "-", 0L, false));
        localInstallList.add(new LocalInstallInfo("com.company.voice.pre", "3.1.8", 3010800L, true));
    }

    private void addLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
        logList.add(0, time + "  " + message);
    }

    private LinearLayout verticalLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontalLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout cardLayout() {
        LinearLayout card = verticalLayout();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(COLOR_PANEL);
        card.setLayoutParams(fullWidthParams());
        return card;
    }

    private TextView text(String value, int size, int color) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(size);
        textView.setTextColor(color);
        textView.setPadding(0, dp(3), 0, dp(3));
        return textView;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(COLOR_PRIMARY);
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(COLOR_PRIMARY);
        button.setBackgroundColor(Color.WHITE);
        return button;
    }

    private LinearLayout.LayoutParams fullWidthParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(5));
        return params;
    }

    private String formatSize(Long bytes) {
        return String.format(Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * Mock button value callback.
     */
    private interface ValueCallback {
        void onValue(String value);
    }

    /**
     * Mock APK release model.
     */
    private static class ReleaseInfo {
        private Long releaseId;
        private String appName;
        private String appCode;
        private String packageName;
        private String envCode;
        private String channelCode;
        private String versionName;
        private Long versionCode;
        private String buildNo;
        private Long apkSize;
        private String sha256;
        private String releaseNotes;
    }

    /**
     * Mock local install model.
     */
    private static class LocalInstallInfo {
        private String packageName;
        private String versionName;
        private Long versionCode;
        private boolean installed;

        private LocalInstallInfo(String packageName, String versionName, Long versionCode, boolean installed) {
            this.packageName = packageName;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.installed = installed;
        }
    }

    /**
     * Mock download task model.
     */
    private static class DownloadTaskInfo {
        private Long taskId;
        private ReleaseInfo releaseInfo;
        private int progress;
        private String status;
        private String message;
    }
}
