package com.example.apkstore.demo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.apkstore.demo.mock.MockDataProvider;
import com.example.apkstore.demo.model.AppRelease;
import com.example.apkstore.demo.model.DownloadTask;
import com.example.apkstore.demo.model.LocalInstall;
import com.example.apkstore.demo.model.OperationLog;
import com.example.apkstore.demo.model.TaskStatus;
import com.example.apkstore.demo.model.UpdateStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * APK 商店客户端 Demo 主界面。
 *
 * @author Codex
 * @date 2026-07-13
 */
public class MainActivity extends Activity {

    private static final int COLOR_PRIMARY = Color.rgb(37, 99, 235);
    private static final int COLOR_PRIMARY_DARK = Color.rgb(30, 64, 175);
    private static final int COLOR_BACKGROUND = Color.rgb(245, 247, 251);
    private static final int COLOR_PANEL = Color.WHITE;
    private static final int COLOR_SURFACE = Color.rgb(248, 250, 252);
    private static final int COLOR_BORDER = Color.rgb(221, 228, 238);
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_MUTED = Color.rgb(101, 112, 131);
    private static final int COLOR_SUCCESS = Color.rgb(22, 163, 74);
    private static final int COLOR_WARNING = Color.rgb(217, 119, 6);
    private static final int COLOR_DANGER = Color.rgb(220, 38, 38);
    private static final String ALL = "全部";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MockDataProvider dataProvider = new MockDataProvider();
    private final List<DownloadTask> taskList = new ArrayList<>(8);

    private LinearLayout rootLayout;
    private LinearLayout contentLayout;
    private String activeTab = "store";
    private String currentUser = "研发测试用户";
    private String selectedAppCode;
    private String selectedEnvCode;
    private String statusFilter = ALL;
    private long taskSequence = 1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showLoginPage();
    }

    private void showLoginPage() {
        LinearLayout loginLayout = verticalLayout();
        loginLayout.setGravity(Gravity.CENTER);
        loginLayout.setPadding(dp(24), dp(24), dp(24), dp(24));
        loginLayout.setBackgroundColor(COLOR_BACKGROUND);
        loginLayout.addView(titleText("内部 APK 商店 Demo", 28));
        loginLayout.addView(normalText("使用本地 mock 数据演示产品、环境和版本更新流程。"));
        Button loginButton = primaryButton("模拟企业登录");
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dataProvider.addLog("模拟登录", currentUser + " 已进入 APK 商店");
                showMainPage();
            }
        });
        loginLayout.addView(loginButton, fullWidthParams());
        setContentView(loginLayout);
    }

    private void showMainPage() {
        rootLayout = verticalLayout();
        rootLayout.setBackgroundColor(COLOR_BACKGROUND);
        rootLayout.addView(createHeader());
        rootLayout.addView(createNavigation());
        contentLayout = verticalLayout();
        rootLayout.addView(contentLayout, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(rootLayout);
        renderActiveTab();
    }

    private View createHeader() {
        LinearLayout header = verticalLayout();
        header.setPadding(dp(16), dp(14), dp(16), dp(12));
        header.setBackgroundColor(COLOR_PANEL);
        header.addView(titleText("APK 商店客户端", 22));
        header.addView(smallText("当前用户：" + currentUser + " · Mock 模式 · 不连接真实服务端"));
        return header;
    }

    private View createNavigation() {
        LinearLayout navigation = horizontalLayout();
        navigation.setPadding(dp(12), dp(10), dp(12), dp(10));
        navigation.setBackgroundColor(Color.WHITE);
        navigation.addView(tabButton("应用商店", "store"));
        navigation.addView(tabButton("下载任务", "tasks"));
        navigation.addView(tabButton("操作日志", "logs"));
        navigation.addView(tabButton("设置", "settings"));
        return navigation;
    }

    private Button tabButton(String text, final String tab) {
        Button button = secondaryButton(text);
        boolean active = tab.equals(activeTab);
        button.setTextColor(active ? Color.WHITE : COLOR_PRIMARY);
        button.setBackground(roundedDrawable(active ? COLOR_PRIMARY : Color.WHITE,
                active ? COLOR_PRIMARY : COLOR_BORDER, dp(10)));
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

    private void renderActiveTab() {
        contentLayout.removeAllViews();
        if ("store".equals(activeTab)) {
            renderStorePage();
        } else if ("tasks".equals(activeTab)) {
            renderTaskPage();
        } else if ("logs".equals(activeTab)) {
            renderLogPage();
        } else {
            renderSettingsPage();
        }
    }

    private void renderStorePage() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout page = verticalLayout();
        page.setPadding(dp(12), dp(12), dp(12), dp(20));
        if (selectedAppCode == null) {
            page.addView(createProductSelectionPanel());
            scrollView.addView(page);
            contentLayout.addView(scrollView, fullWeightParams());
            return;
        }
        if (selectedEnvCode == null) {
            page.addView(createEnvironmentSelectionPanel());
            scrollView.addView(page);
            contentLayout.addView(scrollView, fullWeightParams());
            return;
        }
        page.addView(createSelectionSummary());
        List<AppRelease> releaseList = filterReleases();
        page.addView(smallText("共 " + releaseList.size() + " 个最新版本，历史版本可在卡片内下载"));
        for (AppRelease release : releaseList) {
            page.addView(createReleaseCard(release));
        }
        scrollView.addView(page);
        contentLayout.addView(scrollView, fullWeightParams());
    }

    private View createProductSelectionPanel() {
        LinearLayout panel = cardLayout();
        panel.addView(titleText("选择产品", 20));
        panel.addView(smallText("先选择 appCode 对应的产品"));
        List<AppRelease> products = dataProvider.getProductOptions();
        String[] values = new String[products.size()];
        for (int i = 0; i < products.size(); i++) {
            values[i] = products.get(i).getAppName() + "（" + products.get(i).getAppCode() + "）";
        }
        panel.addView(createSpinner("产品", values, values.length == 0 ? "" : values[0], new SpinnerAction() {
                    @Override
                    public void onSelected(String value) {
                        for (AppRelease product : products) {
                            if (value.equals(product.getAppName() + "（" + product.getAppCode() + "）")) {
                                selectedAppCode = product.getAppCode();
                                selectedEnvCode = null;
                                renderActiveTab();
                                break;
                            }
                        }
                    }
                }));
        return panel;
    }

    private View createEnvironmentSelectionPanel() {
        LinearLayout panel = cardLayout();
        panel.addView(titleText("选择环境", 20));
        panel.addView(smallText("产品：" + selectedAppCode));
        List<String> environments = dataProvider.getEnvironmentOptions(selectedAppCode);
        String[] values = environments.toArray(new String[0]);
        panel.addView(createSpinner("环境", values, values.length == 0 ? "" : values[0], new SpinnerAction() {
                    @Override
                    public void onSelected(String value) {
                        if (!value.equals(selectedEnvCode)) {
                            selectedEnvCode = value;
                            renderActiveTab();
                        }
                    }
                }));
        Button backButton = secondaryButton("返回产品选择");
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedAppCode = null;
                selectedEnvCode = null;
                renderActiveTab();
            }
        });
        panel.addView(backButton, fullWidthParams());
        return panel;
    }

    private View createSelectionSummary() {
        LinearLayout panel = cardLayout();
        panel.addView(titleText("版本中心", 20));
        panel.addView(normalText("产品：" + selectedAppCode + "    环境：" + selectedEnvCode));
        Button changeButton = secondaryButton("切换产品/环境");
        changeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedAppCode = null;
                selectedEnvCode = null;
                renderActiveTab();
            }
        });
        panel.addView(changeButton, fullWidthParams());
        return panel;
    }

    private List<AppRelease> filterReleases() {
        List<AppRelease> latestReleaseList = dataProvider.getLatestReleaseList();
        List<AppRelease> resultList = new ArrayList<>(latestReleaseList.size());
        for (AppRelease release : latestReleaseList) {
            UpdateStatus status = calculateStatus(release);
            boolean envMatched = selectedAppCode.equals(release.getAppCode())
                    && selectedEnvCode.equals(release.getEnvCode());
            boolean statusMatched = ALL.equals(statusFilter) || statusFilter.equals(status.getDisplayName());
            if (envMatched && statusMatched) {
                resultList.add(release);
            }
        }
        return resultList;
    }

    private View createReleaseCard(final AppRelease release) {
        LinearLayout card = cardLayout();
        UpdateStatus status = calculateStatus(release);
        LocalInstall install = dataProvider.getLocalInstall(release.getPackageName());
        LinearLayout titleRow = horizontalLayout();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(titleText(release.getAppName(), 19), new LinearLayout.LayoutParams(0, -2, 1));
        titleRow.addView(statusBadge(status));
        card.addView(titleRow);
        card.addView(normalText("appCode：" + release.getAppCode() + " · 环境：" + release.getEnvCode()
                + " · 最新 " + release.getVersionName() + " / " + release.getVersionCode()));
        card.addView(smallText("包名：" + release.getPackageName()));
        card.addView(smallText("本机版本：" + install.getVersionName() + " / " + install.getVersionCode()));
        card.addView(smallText("构建号：" + release.getBuildNo()));
        card.addView(smallText("APK 大小：" + formatSize(release.getApkSize())));
        card.addView(normalText("更新说明：" + release.getReleaseNotes()));
        card.addView(sectionText("版本下载"));
        card.addView(createVersionActionRow("最新版本", release.getVersionName() + " · " + release.getBuildNo(),
                release, getActionText(status), true));
        List<AppRelease> historyList = dataProvider.getHistoryReleaseList(release);
        if (!historyList.isEmpty()) {
            card.addView(sectionText("历史版本"));
            int count = Math.min(3, historyList.size());
            for (int i = 0; i < count; i++) {
                AppRelease historyRelease = historyList.get(i);
                card.addView(createVersionActionRow("历史 " + historyRelease.getVersionName(),
                        historyRelease.getBuildNo() + " · " + formatSize(historyRelease.getApkSize()),
                        historyRelease, "下载", false));
            }
        }
        return card;
    }

    private View createVersionActionRow(String title, String description, final AppRelease release, String actionText,
            boolean primary) {
        LinearLayout row = verticalLayout();
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(roundedDrawable(COLOR_SURFACE, COLOR_BORDER, dp(8)));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.setMargins(0, dp(6), 0, dp(6));
        row.setLayoutParams(rowParams);
        row.addView(normalText(title));
        row.addView(smallText(description));
        Button button = primary ? primaryButton(actionText) : secondaryButton(actionText);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dataProvider.addLog("选择版本", release.getAppName() + " " + release.getVersionName()
                        + " 已加入下载任务");
                startMockDownload(release);
            }
        });
        row.addView(button, fullWidthParams());
        return row;
    }

    private void renderTaskPage() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout page = verticalLayout();
        page.setPadding(dp(12), dp(12), dp(12), dp(20));
        page.addView(titleText("下载任务", 22));
        if (taskList.isEmpty()) {
            page.addView(normalText("暂无下载任务。可以在应用商店中点击安装或更新。"));
        }
        for (DownloadTask task : taskList) {
            page.addView(createTaskCard(task));
        }
        scrollView.addView(page);
        contentLayout.addView(scrollView, fullWeightParams());
    }

    private View createTaskCard(DownloadTask task) {
        LinearLayout card = cardLayout();
        AppRelease release = task.getRelease();
        card.addView(titleText(release.getAppName() + " · " + task.getStatus().getDisplayName(), 18));
        card.addView(smallText("appCode：" + release.getAppCode() + " · 环境："
                + release.getEnvCode() + " · " + task.getMessage()));
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(task.getProgress());
        card.addView(progressBar, fullWidthParams());
        card.addView(smallText("进度：" + task.getProgress() + "%"));
        return card;
    }

    private void renderLogPage() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout page = verticalLayout();
        page.setPadding(dp(12), dp(12), dp(12), dp(20));
        page.addView(titleText("操作日志", 22));
        for (OperationLog log : dataProvider.getOperationLogList()) {
            LinearLayout card = cardLayout();
            card.addView(titleText(log.getTitle(), 17));
            card.addView(smallText(log.getTime()));
            card.addView(normalText(log.getContent()));
            page.addView(card);
        }
        scrollView.addView(page);
        contentLayout.addView(scrollView, fullWeightParams());
    }

    private void renderSettingsPage() {
        LinearLayout page = verticalLayout();
        page.setPadding(dp(12), dp(12), dp(12), dp(20));
        page.addView(titleText("设置", 22));
        page.addView(createUserPanel());
        Button resetButton = secondaryButton("重置 Mock 数据");
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetMockData();
            }
        });
        page.addView(resetButton, fullWidthParams());
        contentLayout.addView(page, fullWeightParams());
    }

    private View createUserPanel() {
        LinearLayout panel = cardLayout();
        panel.addView(titleText("Mock 用户", 18));
        panel.addView(normalText("当前：" + currentUser));
        panel.addView(userButton("切换为研发测试用户"));
        panel.addView(userButton("切换为项目负责人"));
        panel.addView(userButton("切换为平台管理员"));
        return panel;
    }

    private Button userButton(final String userName) {
        Button button = secondaryButton(userName);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                currentUser = userName.replace("切换为", "");
                dataProvider.addLog("切换用户", "当前用户切换为 " + currentUser);
                showMainPage();
            }
        });
        return button;
    }

    private void startMockDownload(AppRelease release) {
        final DownloadTask task = new DownloadTask(taskSequence++, release);
        taskList.add(0, task);
        dataProvider.addLog("创建下载任务", release.getAppName() + " 开始模拟下载");
        activeTab = "tasks";
        showMainPage();
        scheduleTaskProgress(task);
    }

    private void scheduleTaskProgress(final DownloadTask task) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                advanceTask(task);
            }
        }, 450L);
    }

    private void advanceTask(DownloadTask task) {
        int nextProgress = task.getProgress() + 20;
        if (nextProgress < 100) {
            task.update(nextProgress, TaskStatus.DOWNLOADING, "正在下载 APK");
            refreshIfTaskPage();
            scheduleTaskProgress(task);
            return;
        }
        finishTask(task);
    }

    private void finishTask(final DownloadTask task) {
        task.update(100, TaskStatus.INSTALLING, "下载完成，模拟安装中");
        refreshIfTaskPage();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                completeInstall(task);
            }
        }, 600L);
    }

    private void completeInstall(DownloadTask task) {
        task.update(100, TaskStatus.DONE, "安装成功，已上报结果");
        dataProvider.installRelease(task.getRelease());
        dataProvider.addLog("安装上报", "已模拟上报 releaseId=" + task.getRelease().getReleaseId());
        refreshIfTaskPage();
    }

    private void refreshIfTaskPage() {
        if ("tasks".equals(activeTab)) {
            renderActiveTab();
        }
    }

    private void resetMockData() {
        taskList.clear();
        dataProvider.reset();
        selectedAppCode = null;
        selectedEnvCode = null;
        statusFilter = ALL;
        activeTab = "store";
        showMainPage();
    }

    private UpdateStatus calculateStatus(AppRelease release) {
        LocalInstall install = dataProvider.getLocalInstall(release.getPackageName());
        if (!Boolean.TRUE.equals(install.getInstalled())) {
            return UpdateStatus.NOT_INSTALLED;
        }
        int result = install.getVersionCode().compareTo(release.getVersionCode());
        if (result < 0) {
            return UpdateStatus.UPDATE_AVAILABLE;
        }
        if (result > 0) {
            return UpdateStatus.LOCAL_NEWER;
        }
        return UpdateStatus.LATEST;
    }

    private String getActionText(UpdateStatus status) {
        if (UpdateStatus.NOT_INSTALLED.equals(status)) {
            return "安装最新版本";
        }
        if (UpdateStatus.UPDATE_AVAILABLE.equals(status)) {
            return "更新到最新版本";
        }
        if (UpdateStatus.LOCAL_NEWER.equals(status)) {
            return "下载后台最新";
        }
        return "重新下载最新";
    }

    private View createSpinner(String label, String[] values, String selectedValue, final SpinnerAction action) {
        LinearLayout panel = verticalLayout();
        panel.addView(smallText(label));
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(indexOf(values, selectedValue));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                action.onSelected(String.valueOf(parent.getItemAtPosition(position)));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No action needed.
            }
        });
        panel.addView(spinner, fullWidthParams());
        return panel;
    }

    private int indexOf(String[] values, String selectedValue) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(selectedValue)) {
                return i;
            }
        }
        return 0;
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
        card.setBackground(roundedDrawable(COLOR_PANEL, COLOR_BORDER, dp(10)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private TextView titleText(String text, int sizeSp) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(COLOR_TEXT);
        textView.setTextSize(sizeSp);
        textView.setGravity(Gravity.START);
        textView.setPadding(0, dp(4), 0, dp(4));
        return textView;
    }

    private TextView normalText(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(COLOR_TEXT);
        textView.setTextSize(14);
        textView.setPadding(0, dp(3), 0, dp(3));
        return textView;
    }

    private TextView smallText(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(COLOR_MUTED);
        textView.setTextSize(12);
        textView.setPadding(0, dp(2), 0, dp(2));
        return textView;
    }

    private TextView sectionText(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(COLOR_PRIMARY_DARK);
        textView.setTextSize(14);
        textView.setPadding(0, dp(12), 0, dp(4));
        return textView;
    }

    private TextView statusBadge(UpdateStatus status) {
        TextView textView = new TextView(this);
        textView.setText(status.getDisplayName());
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(12);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(dp(10), dp(4), dp(10), dp(4));
        textView.setBackground(roundedDrawable(statusColor(status), statusColor(status), dp(14)));
        return textView;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(roundedDrawable(COLOR_PRIMARY, COLOR_PRIMARY, dp(10)));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(COLOR_PRIMARY);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(roundedDrawable(Color.WHITE, COLOR_BORDER, dp(10)));
        return button;
    }

    private int statusColor(UpdateStatus status) {
        if (UpdateStatus.LATEST.equals(status)) {
            return COLOR_SUCCESS;
        }
        if (UpdateStatus.UPDATE_AVAILABLE.equals(status)) {
            return COLOR_WARNING;
        }
        if (UpdateStatus.LOCAL_NEWER.equals(status)) {
            return COLOR_DANGER;
        }
        return COLOR_PRIMARY;
    }

    private GradientDrawable roundedDrawable(int fillColor, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidthParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private LinearLayout.LayoutParams fullWeightParams() {
        return new LinearLayout.LayoutParams(-1, 0, 1);
    }

    private String formatSize(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "-";
        }
        return String.format(Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * Spinner 选择回调。
     *
     * @author Codex
     * @date 2026-07-13
     */
    private interface SpinnerAction {

        /**
         * 选中回调。
         *
         * @param value 选中值
         */
        void onSelected(String value);
    }
}

