package com.example.apkstore.demo;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.apkstore.demo.model.AppRelease;
import com.example.apkstore.demo.model.DownloadTask;
import com.example.apkstore.demo.model.TaskStatus;
import com.example.apkstore.demo.model.UpdateStatus;
import com.example.apkstore.demo.net.ApiClient;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * APK 商店客户端主界面，直接调用研发环境 admin-service。
 *
 * @author Codex
 * @date 2026-08-28
 */
public class MainActivity extends Activity {

    private static final int COLOR_PRIMARY = Color.rgb(0, 102, 255);
    private static final int COLOR_PRIMARY_DARK = Color.rgb(0, 76, 204);
    private static final int COLOR_PRIMARY_SOFT = Color.rgb(232, 241, 255);
    private static final int COLOR_BACKGROUND = Color.rgb(246, 247, 250);
    private static final int COLOR_PANEL = Color.WHITE;
    private static final int COLOR_SURFACE = Color.rgb(247, 249, 252);
    private static final int COLOR_BORDER = Color.rgb(228, 231, 237);
    private static final int COLOR_DIVIDER = Color.rgb(238, 240, 244);
    private static final int COLOR_TEXT = Color.rgb(24, 28, 36);
    private static final int COLOR_MUTED = Color.rgb(111, 118, 132);
    private static final int COLOR_SUCCESS = Color.rgb(22, 163, 74);
    private static final int COLOR_SUCCESS_SOFT = Color.rgb(232, 247, 237);
    private static final int COLOR_WARNING = Color.rgb(217, 119, 6);
    private static final int COLOR_WARNING_SOFT = Color.rgb(255, 246, 225);
    private static final int COLOR_DANGER = Color.rgb(220, 38, 38);
    private static final int COLOR_DANGER_SOFT = Color.rgb(254, 235, 235);
    private static final String[][] DEFAULT_ENVIRONMENTS = {
            {"dev", "研发环境"},
            {"test", "测试环境"},
            {"sit_test", "耳机测试环境"},
            {"online_test", "线上测试环境"},
            {"prod", "线上发布环境"}
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final ApiClient apiClient = new ApiClient();
    private final List<DownloadTask> taskList = new ArrayList<>(8);
    private final Map<Long, File> downloadedFiles = new HashMap<>();

    private LinearLayout contentLayout;
    private View floatingBackButton;
    private SwipeRefreshLayout refreshLayout;
    private List<AppRelease> products = new ArrayList<>();
    private List<ApiClient.EnvironmentOption> environments = new ArrayList<>();
    private List<AppRelease> historyList = new ArrayList<>();
    private AppRelease latestRelease;
    private String selectedAppCode;
    private String selectedEnvCode;
    private String historySearchQuery = "";
    private String errorMessage;
    private boolean productsLoaded;
    private boolean environmentsLoaded;
    private boolean versionsLoading;
    private long taskSequence = 1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showMainPage();
        loadProducts();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void showMainPage() {
        LinearLayout root = verticalLayout();
        root.setBackgroundColor(COLOR_BACKGROUND);
        FrameLayout contentFrame = new FrameLayout(this);
        contentLayout = verticalLayout();
        contentFrame.addView(contentLayout, new FrameLayout.LayoutParams(-1, -1));
        floatingBackButton = createBackButton();
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(46), dp(46));
        backParams.gravity = Gravity.TOP | Gravity.START;
        backParams.setMargins(dp(12), dp(12), 0, 0);
        contentFrame.addView(floatingBackButton, backParams);
        root.addView(contentFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        renderPage();
    }

    private View createHeader() {
        LinearLayout header = horizontalLayout();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(16), dp(18), dp(14));
        header.setBackgroundColor(COLOR_PANEL);
        header.setElevation(dp(2));
        LinearLayout titleGroup = verticalLayout();
        titleGroup.addView(titleText("APK 商店", 22));
        header.addView(titleGroup, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(environmentBadge("研发环境"));
        return header;
    }

    private void renderPage() {
        if (contentLayout == null) {
            return;
        }
        if (floatingBackButton != null) {
            floatingBackButton.setVisibility(selectedAppCode == null ? View.GONE : View.VISIBLE);
        }
        contentLayout.removeAllViews();
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout page = verticalLayout();
        int topPadding = selectedAppCode == null ? dp(16) : dp(68);
        page.setPadding(dp(14), topPadding, dp(14), dp(28));
        if (errorMessage != null) {
            page.addView(errorPanel(errorMessage));
        }
        if (!productsLoaded) {
            page.addView(cardLayoutWithText("加载产品中…"));
        } else if (products.isEmpty()) {
            page.addView(cardLayoutWithText("后端暂无可用产品"));
        } else if (selectedAppCode == null) {
            page.addView(createAppsListPanel());
        } else if (!environmentsLoaded) {
            page.addView(createLoadingCard("正在加载环境列表…"));
        } else if (environments.isEmpty() || selectedEnvCode == null) {
            page.addView(createEmptyCard("暂无可用环境", "后端暂未返回可选环境。"));
        } else {
            if (latestRelease != null) {
                page.addView(createReleaseCard(latestRelease));
                page.addView(createHistoryCard(latestRelease));
            } else if (versionsLoading) {
                page.addView(createUnavailableReleaseCard("正在获取版本信息…",
                        "请稍候，正在查询当前环境的 APK。"));
            } else {
                page.addView(createUnavailableReleaseCard("暂无可用版本",
                        "该产品在当前环境下还没有发布 APK。"));
            }
        }
        if (!taskList.isEmpty()) {
            page.addView(pageSectionTitle("下载与安装", "下载完成后可直接打开系统安装器"));
            for (DownloadTask task : taskList) {
                page.addView(createTaskCard(task));
            }
        }
        scrollView.addView(page);
        refreshLayout = new SwipeRefreshLayout(this);
        refreshLayout.setColorSchemeColors(COLOR_PRIMARY);
        refreshLayout.setOnRefreshListener(() -> refreshAll());
        refreshLayout.addView(scrollView, new SwipeRefreshLayout.LayoutParams(-1, -1));
        contentLayout.addView(refreshLayout, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void refreshAll() {
        errorMessage = null;
        productsLoaded = false;
        environmentsLoaded = false;
        environments = new ArrayList<>();
        latestRelease = null;
        historyList = new ArrayList<>();
        versionsLoading = false;
        renderPage();
        loadProducts();
    }

    private void loadProducts() {
        errorMessage = null;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<AppRelease> result = apiClient.getApps();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            products = result;
                            productsLoaded = true;
                            if (selectedAppCode != null && !containsProduct(selectedAppCode)) {
                                selectedAppCode = null;
                                selectedEnvCode = null;
                                environmentsLoaded = false;
                            }
                            renderPage();
                            stopRefreshing();
                            if (selectedAppCode != null) {
                                loadEnvironments();
                            }
                        }
                    });
                } catch (Exception exception) {
                    showError(exception);
                }
            }
        });
    }

    private void loadEnvironments() {
        final String appCode = selectedAppCode;
        if (appCode == null) {
            return;
        }
        errorMessage = null;
        renderPage();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<ApiClient.EnvironmentOption> result = apiClient.getEnvironments(appCode);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            environments = completeEnvironments(result);
                            environmentsLoaded = true;
                            if (selectedEnvCode == null && !environments.isEmpty()) {
                                selectedEnvCode = environments.get(0).getCode();
                            }
                            renderPage();
                            if (selectedEnvCode != null) {
                                loadVersions();
                            }
                        }
                    });
                } catch (Exception exception) {
                    showError(exception);
                }
            }
        });
    }

    private void loadVersions() {
        final String appCode = selectedAppCode;
        final String envCode = selectedEnvCode;
        if (appCode == null || envCode == null) {
            return;
        }
        errorMessage = null;
        latestRelease = null;
        historyList = new ArrayList<>();
        versionsLoading = true;
        renderPage();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final AppRelease latest = apiClient.getLatest(appCode, envCode);
                    final List<AppRelease> history = apiClient.getHistory(appCode, envCode, historySearchQuery);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            latestRelease = latest;
                            historyList = history;
                            versionsLoading = false;
                            renderPage();
                        }
                    });
                } catch (Exception exception) {
                    showError(exception);
                }
            }
        });
    }

    private void showError(Exception exception) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                versionsLoading = false;
                errorMessage = exception.getMessage() == null ? "网络请求失败" : exception.getMessage();
                renderPage();
                stopRefreshing();
            }
        });
    }

    private View createAppsListPanel() {
        LinearLayout panel = verticalLayout();
        panel.addView(pageSectionTitle("应用", "已接入的应用，点击查看版本与更新"));
        for (AppRelease product : products) {
            panel.addView(createProductCard(product));
        }
        return panel;
    }

    private View createProductCard(final AppRelease product) {
        LinearLayout card = cardLayout();
        card.setOnClickListener(view -> openProduct(product.getAppCode()));
        LinearLayout row = horizontalLayout();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(appIcon(product.getAppName()), new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout details = verticalLayout();
        details.setPadding(dp(14), 0, dp(8), 0);
        details.addView(titleText(displayText(product.getAppName(), "未命名应用"), 19));
        details.addView(smallText(displayText(product.getAppCode(), "-")));
        details.addView(smallText("查看版本与更新"));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chevron = titleText("›", 26);
        chevron.setTextColor(COLOR_MUTED);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(58)));
        card.addView(row);
        return card;
    }

    private void openProduct(String appCode) {
        selectedAppCode = appCode;
        selectedEnvCode = null;
        environments = new ArrayList<>();
        environmentsLoaded = false;
        latestRelease = null;
        historyList = new ArrayList<>();
        errorMessage = null;
        renderPage();
        loadEnvironments();
    }

    private boolean containsProduct(String appCode) {
        for (AppRelease product : products) {
            if (appCode.equals(product.getAppCode())) {
                return true;
            }
        }
        return false;
    }

    private void stopRefreshing() {
        if (refreshLayout != null) {
            refreshLayout.setRefreshing(false);
        }
    }

    private View createAppDetailHeader() {
        LinearLayout card = verticalLayout();
        card.addView(createBackButton());
        LinearLayout row = horizontalLayout();
        row.setGravity(Gravity.CENTER_VERTICAL);
        AppRelease product = findProduct(selectedAppCode);
        row.addView(appIcon(product == null ? selectedAppCode : product.getAppName()),
                new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout details = verticalLayout();
        details.setPadding(dp(14), 0, 0, 0);
        details.addView(titleText(product == null ? selectedAppCode : product.getAppName(), 20));
        details.addView(smallText("appCode：" + displayText(selectedAppCode, "-")));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row);
        return card;
    }

    private View createBackButton() {
        TextView backButton = new TextView(this);
        backButton.setText("‹");
        backButton.setTextColor(COLOR_TEXT);
        backButton.setTextSize(38);
        backButton.setGravity(Gravity.CENTER);
        backButton.setPadding(0, 0, 0, dp(5));
        backButton.setContentDescription("返回应用列表");
        backButton.setBackground(roundedDrawable(COLOR_PANEL, COLOR_BORDER, dp(23)));
        backButton.setElevation(dp(6));
        backButton.setOnClickListener(view -> {
            selectedAppCode = null;
            selectedEnvCode = null;
            environments = new ArrayList<>();
            environmentsLoaded = false;
            latestRelease = null;
            historyList = new ArrayList<>();
            renderPage();
        });
        return backButton;
    }

    private AppRelease findProduct(String appCode) {
        for (AppRelease product : products) {
            if (appCode != null && appCode.equals(product.getAppCode())) {
                return product;
            }
        }
        return null;
    }

    private View createEnvironmentSelectionPanel() {
        LinearLayout panel = cardLayout();
        panel.addView(titleText("选择环境", 20));
        panel.addView(smallText("产品：" + selectedAppCode));
        String[] values = new String[environments.size()];
        for (int i = 0; i < environments.size(); i++) {
            values[i] = environments.get(i).toString();
        }
        final boolean[] initialSelection = {true};
        panel.addView(createSpinner("环境", values, environmentDisplayValue(), new SpinnerAction() {
            @Override
            public void onSelected(String value) {
                if (initialSelection[0]) {
                    initialSelection[0] = false;
                    return;
                }
                for (ApiClient.EnvironmentOption option : environments) {
                    if (option.toString().equals(value) && !option.getCode().equals(selectedEnvCode)) {
                        selectedEnvCode = option.getCode();
                        loadVersions();
                        break;
                    }
                }
            }
        }));
        Button backButton = secondaryButton("返回产品选择");
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedAppCode = null;
                selectedEnvCode = null;
                environments = new ArrayList<>();
                environmentsLoaded = false;
                renderPage();
            }
        });
        panel.addView(backButton, fullWidthParams());
        return panel;
    }

    private View createSelectionSummary() {
        LinearLayout panel = cardLayout();
        panel.addView(createBackButton());
        panel.addView(createContextHeader());
        panel.addView(createEnvironmentSpinner());
        return panel;
    }

    private View createContextHeader() {
        LinearLayout header = horizontalLayout();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = verticalLayout();
        heading.addView(titleText("分发范围", 17));
        heading.addView(smallText("选择需要查看的产品和部署环境"));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button refreshButton = compactButton("刷新", true);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadVersions();
            }
        });
        header.addView(refreshButton, new LinearLayout.LayoutParams(dp(72), dp(38)));
        return header;
    }

    private View createProductSpinner() {
        String[] productValues = new String[products.size()];
        for (int i = 0; i < products.size(); i++) {
            productValues[i] = products.get(i).getAppName() + "（" + products.get(i).getAppCode() + "）";
        }
        return createSpinner("产品", productValues, productDisplayValue(), new SpinnerAction() {
            @Override
            public void onSelected(String value) {
                selectProduct(value);
            }
        });
    }

    private void selectProduct(String value) {
        for (AppRelease product : products) {
            String display = product.getAppName() + "（" + product.getAppCode() + "）";
            if (display.equals(value) && !product.getAppCode().equals(selectedAppCode)) {
                selectedAppCode = product.getAppCode();
                selectedEnvCode = null;
                environments = new ArrayList<>();
                environmentsLoaded = false;
                loadEnvironments();
                return;
            }
        }
    }

    private View createEnvironmentSpinner() {
        String[] environmentValues = new String[environments.size()];
        for (int i = 0; i < environments.size(); i++) {
            environmentValues[i] = environments.get(i).toString();
        }
        LinearLayout row = horizontalLayout();
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, environmentValues);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(indexOf(environmentValues, environmentDisplayValue()));
        spinner.setPadding(dp(8), 0, dp(30), 0);
        spinner.setBackground(roundedDrawable(COLOR_SURFACE, COLOR_BORDER, dp(10)));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < parent.getCount()) {
                    selectEnvironment(String.valueOf(parent.getItemAtPosition(position)));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No action needed.
            }
        });
        FrameLayout spinnerContainer = new FrameLayout(this);
        spinnerContainer.addView(spinner, new FrameLayout.LayoutParams(-1, -1));
        TextView arrow = new TextView(this);
        arrow.setText("▾");
        arrow.setTextColor(COLOR_MUTED);
        arrow.setTextSize(16);
        arrow.setGravity(Gravity.CENTER);
        arrow.setContentDescription("展开环境列表");
        arrow.setOnClickListener(view -> spinner.performClick());
        FrameLayout.LayoutParams arrowParams = new FrameLayout.LayoutParams(dp(28), -1, Gravity.END);
        spinnerContainer.addView(arrow, arrowParams);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(174), dp(38));
        row.addView(spinnerContainer, spinnerParams);
        LinearLayout.LayoutParams rowParams = fullWidthParams();
        rowParams.setMargins(0, dp(4), 0, dp(2));
        row.setLayoutParams(rowParams);
        return row;
    }

    private List<ApiClient.EnvironmentOption> completeEnvironments(
            List<ApiClient.EnvironmentOption> backendEnvironments) {
        List<ApiClient.EnvironmentOption> complete = new ArrayList<>(DEFAULT_ENVIRONMENTS.length);
        if (backendEnvironments != null) {
            complete.addAll(backendEnvironments);
        }
        for (String[] environment : DEFAULT_ENVIRONMENTS) {
            if (!containsEnvironmentCode(complete, environment[0])) {
                complete.add(new ApiClient.EnvironmentOption(environment[0], environment[1]));
            }
        }
        return complete;
    }

    private boolean containsEnvironmentCode(List<ApiClient.EnvironmentOption> environmentOptions,
            String environmentCode) {
        for (ApiClient.EnvironmentOption option : environmentOptions) {
            if (environmentCode.equals(option.getCode())) {
                return true;
            }
        }
        return false;
    }

    private void selectEnvironment(String value) {
        for (ApiClient.EnvironmentOption option : environments) {
            if (option.toString().equals(value) && !option.getCode().equals(selectedEnvCode)) {
                selectedEnvCode = option.getCode();
                loadVersions();
                return;
            }
        }
    }

    private View createReleaseCard(final AppRelease release) {
        LinearLayout card = cardLayout();
        UpdateStatus status = calculateStatus(release);
        card.addView(createAppIdentity(release, status));
        card.addView(createEnvironmentSpinner());
        card.addView(titleText("版本 " + displayText(release.getVersionName(), "-"), 17));
        card.addView(bodyText(displayText(release.getReleaseNotes(), "暂无版本提交信息")));
        Button actionButton = primaryButton(getActionText(status));
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDownload(release);
            }
        });
        card.addView(actionButton, prominentButtonParams());
        return card;
    }

    private View createEnvironmentPickerCard() {
        LinearLayout card = cardLayout();
        card.addView(createEnvironmentSpinner());
        return card;
    }

    private View createUnavailableReleaseCard(String title, String description) {
        LinearLayout card = cardLayout();
        AppRelease product = findProduct(selectedAppCode);
        String displayName = product == null ? appName(selectedAppCode) : product.getAppName();
        LinearLayout identity = horizontalLayout();
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.addView(appIcon(displayName), new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout details = verticalLayout();
        details.setPadding(dp(14), 0, 0, 0);
        details.addView(titleText(displayText(displayName, "未命名应用"), 22));
        details.addView(smallText(displayText(environmentName(selectedEnvCode), selectedEnvCode)
                + "  ·  " + displayText(selectedAppCode, "-")));
        identity.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(identity);
        card.addView(createEnvironmentSpinner());
        card.addView(titleText(title, 17));
        card.addView(bodyText(description));
        return card;
    }

    private View createHistoryCard(final AppRelease latest) {
        LinearLayout card = cardLayout();
        card.addView(pageSectionTitle("历史版本", "支持搜索版本号或构建号"));
        card.addView(createHistorySearch());
        addHistoryRows(card, latest);
        return card;
    }

    private View createHistorySearch() {
        LinearLayout searchRow = horizontalLayout();
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText searchInput = new EditText(this);
        searchInput.setHint("搜索版本号或构建号");
        searchInput.setHintTextColor(COLOR_MUTED);
        searchInput.setTextColor(COLOR_TEXT);
        searchInput.setTextSize(14);
        searchInput.setText(historySearchQuery);
        searchInput.setSingleLine(true);
        searchInput.setGravity(Gravity.CENTER_VERTICAL);
        searchInput.setPadding(dp(12), 0, dp(12), 0);
        searchInput.setBackground(roundedDrawable(COLOR_SURFACE, COLOR_BORDER, dp(10)));
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button searchButton = compactButton("搜索", false);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                historySearchQuery = searchInput.getText().toString().trim();
                loadVersions();
            }
        });
        LinearLayout.LayoutParams searchButtonParams = new LinearLayout.LayoutParams(dp(76), dp(44));
        searchButtonParams.setMargins(dp(8), 0, 0, 0);
        searchRow.addView(searchButton, searchButtonParams);
        LinearLayout.LayoutParams rowParams = fullWidthParams();
        rowParams.setMargins(0, dp(12), 0, dp(8));
        searchRow.setLayoutParams(rowParams);
        return searchRow;
    }

    private void addHistoryRows(LinearLayout card, AppRelease latest) {
        if (historyList.isEmpty()) {
            card.addView(emptyHint("没有匹配的历史版本"));
            return;
        }
        int shown = 0;
        for (AppRelease historyRelease : historyList) {
            if (isLatestRelease(latest, historyRelease)) {
                continue;
            }
            if (shown > 0) {
                card.addView(divider());
            }
            card.addView(createVersionActionRow(historyRelease));
            shown++;
            if (shown == 5) {
                break;
            }
        }
        if (shown == 0) {
            card.addView(emptyHint("没有匹配的历史版本"));
        }
    }

    private View createVersionActionRow(final AppRelease release) {
        LinearLayout row = horizontalLayout();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        LinearLayout details = verticalLayout();
        details.addView(titleText("版本 " + displayText(release.getVersionName(), "-"), 15));
        details.addView(smallText(displayText(release.getReleaseNotes(), "暂无版本提交信息")));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        Button button = compactButton("下载", false);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDownload(release);
            }
        });
        row.addView(button, new LinearLayout.LayoutParams(dp(78), dp(40)));
        return row;
    }

    private View createAppIdentity(AppRelease release, UpdateStatus status) {
        LinearLayout row = horizontalLayout();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(appIcon(release.getAppName()), new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout details = verticalLayout();
        details.setPadding(dp(14), 0, 0, 0);
        details.addView(titleText(displayText(release.getAppName(), "未命名应用"), 22));
        details.addView(appSubtitleText(release));
        details.addView(statusBadge(status));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private View createMetadataStrip(AppRelease release) {
        LinearLayout strip = horizontalLayout();
        strip.setGravity(Gravity.CENTER);
        strip.setPadding(dp(6), dp(12), dp(6), dp(12));
        strip.setBackground(roundedDrawable(COLOR_SURFACE, COLOR_SURFACE, dp(12)));
        strip.addView(metric("版本", displayText(release.getVersionName(), "-")), metricParams());
        strip.addView(metric("构建", displayText(release.getBuildNo(), "-")), metricParams());
        strip.addView(metric("大小", formatSize(release.getApkSize())), metricParams());
        LinearLayout.LayoutParams params = fullWidthParams();
        params.setMargins(0, dp(16), 0, dp(10));
        strip.setLayoutParams(params);
        return strip;
    }

    private View metric(String label, String value) {
        LinearLayout metric = verticalLayout();
        metric.setGravity(Gravity.CENTER);
        TextView valueView = normalText(value);
        valueView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        valueView.setGravity(Gravity.CENTER);
        TextView labelView = smallText(label);
        labelView.setGravity(Gravity.CENTER);
        metric.addView(valueView);
        metric.addView(labelView);
        return metric;
    }

    private LinearLayout.LayoutParams metricParams() {
        return new LinearLayout.LayoutParams(0, -2, 1);
    }

    private TextView appSubtitleText(AppRelease release) {
        String value = displayText(environmentName(release.getEnvCode()), release.getEnvCode())
                + "  ·  " + displayText(release.getAppCode(), "-");
        return smallText(value);
    }

    private boolean isLatestRelease(AppRelease latest, AppRelease candidate) {
        return latest.getRemoteReleaseId() != null
                && latest.getRemoteReleaseId().equals(candidate.getRemoteReleaseId());
    }

    private View createTaskCard(final DownloadTask task) {
        LinearLayout card = cardLayout();
        AppRelease release = task.getRelease();
        LinearLayout header = horizontalLayout();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout details = verticalLayout();
        details.addView(titleText(displayText(release.getAppName(), "应用下载"), 17));
        details.addView(smallText("版本 " + displayText(release.getVersionName(), "-")
                + "  ·  " + displayText(task.getMessage(), "等待开始")));
        header.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(taskStatusBadge(task.getStatus()));
        card.addView(header);
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(task.getProgress());
        progressBar.setProgressTintList(ColorStateList.valueOf(taskProgressColor(task.getStatus())));
        LinearLayout.LayoutParams progressParams = fullWidthParams();
        progressParams.setMargins(0, dp(14), 0, dp(4));
        card.addView(progressBar, progressParams);
        TextView progressText = smallText("已完成 " + task.getProgress() + "%");
        progressText.setGravity(Gravity.END);
        card.addView(progressText);
        if (TaskStatus.DOWNLOADED.equals(task.getStatus())) {
            Button installButton = primaryButton("安装应用");
            installButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    installTask(task);
                }
            });
            card.addView(installButton, prominentButtonParams());
        }
        return card;
    }

    private void startDownload(final AppRelease release) {
        if (release.getRemoteReleaseId() == null || release.getRemoteReleaseId().isEmpty()) {
            errorMessage = "版本缺少服务端 releaseId";
            renderPage();
            return;
        }
        final DownloadTask task = new DownloadTask(taskSequence++, release);
        taskList.add(0, task);
        renderPage();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                executeDownload(release, task);
            }
        });
    }

    private void executeDownload(AppRelease release, DownloadTask task) {
        try {
            ApiClient.DownloadInfo info = apiClient.getDownloadInfo(release.getRemoteReleaseId());
            File target = prepareDownloadTarget(info.getFileName());
            ApiClient.downloadFile(info.getDownloadUrl(), target, createProgressListener(task));
            postDownloadCompleted(task, target);
        } catch (Exception exception) {
            postDownloadFailed(task, exception);
        }
    }

    private File prepareDownloadTarget(String fileName) {
        File parent = getExternalFilesDir("apk");
        if (parent == null) {
            throw new IllegalStateException("无法获取 APK 缓存目录");
        }
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建 APK 缓存目录");
        }
        return new File(parent, safeFileName(fileName));
    }

    private ApiClient.ProgressListener createProgressListener(final DownloadTask task) {
        final int[] lastProgress = {-1};
        return new ApiClient.ProgressListener() {
            @Override
            public void onProgress(long completed, long total) {
                int progress = total <= 0 ? 0 : (int) Math.min(99, completed * 100 / total);
                if (progress == lastProgress[0]) {
                    return;
                }
                lastProgress[0] = progress;
                postTaskUpdate(task, progress, TaskStatus.DOWNLOADING, "正在下载 APK");
            }
        };
    }

    private void postDownloadCompleted(final DownloadTask task, final File target) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                downloadedFiles.put(task.getTaskId(), target);
                task.update(100, TaskStatus.DOWNLOADED, "下载完成，请点击安装");
                renderPage();
            }
        });
    }

    private void postDownloadFailed(DownloadTask task, Exception exception) {
        String message = exception.getMessage() == null ? "APK 下载失败" : exception.getMessage();
        postTaskUpdate(task, task.getProgress(), TaskStatus.FAILED, message);
    }

    private void postTaskUpdate(final DownloadTask task, final int progress,
            final TaskStatus status, final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                task.update(progress, status, message);
                renderPage();
            }
        });
    }

    private void installTask(DownloadTask task) {
        File file = downloadedFiles.get(task.getTaskId());
        if (file == null || !file.exists()) {
            task.update(task.getProgress(), TaskStatus.FAILED, "APK 缓存文件不存在，请重新下载");
            renderPage();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            task.update(100, TaskStatus.INSTALLING, "已打开系统安装确认页");
            startActivity(intent);
            renderPage();
        } catch (Exception exception) {
            task.update(task.getProgress(), TaskStatus.FAILED, "无法打开安装器：" + exception.getMessage());
            renderPage();
        }
    }

    private UpdateStatus calculateStatus(AppRelease release) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(release.getPackageName(), 0);
            long localVersion = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            int result = Long.compare(localVersion, release.getVersionCode());
            if (result < 0) {
                return UpdateStatus.UPDATE_AVAILABLE;
            }
            return result > 0 ? UpdateStatus.LOCAL_NEWER : UpdateStatus.LATEST;
        } catch (PackageManager.NameNotFoundException exception) {
            return UpdateStatus.NOT_INSTALLED;
        }
    }

    private String localVersion(String packageName) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(packageName, 0);
            long code = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return info.versionName + " / " + code;
        } catch (PackageManager.NameNotFoundException exception) {
            return "未安装";
        }
    }

    private String getActionText(UpdateStatus status) {
        if (UpdateStatus.NOT_INSTALLED.equals(status)) {
            return "安装最新版本";
        }
        if (UpdateStatus.UPDATE_AVAILABLE.equals(status)) {
            return "更新到最新版本";
        }
        return "重新下载最新";
    }

    private String appName(String appCode) {
        for (AppRelease product : products) {
            if (appCode.equals(product.getAppCode())) {
                return product.getAppName();
            }
        }
        return appCode;
    }

    private String productDisplayValue() {
        for (AppRelease product : products) {
            if (product.getAppCode().equals(selectedAppCode)) {
                return product.getAppName() + "（" + product.getAppCode() + "）";
            }
        }
        return products.isEmpty() ? "" : products.get(0).getAppName() + "（" + products.get(0).getAppCode() + "）";
    }

    private String environmentDisplayValue() {
        for (ApiClient.EnvironmentOption option : environments) {
            if (option.getCode().equals(selectedEnvCode)) {
                return option.toString();
            }
        }
        return environments.isEmpty() ? "" : environments.get(0).toString();
    }

    private String environmentName(String code) {
        for (ApiClient.EnvironmentOption option : environments) {
            if (option.getCode().equals(code)) {
                return option.getName();
            }
        }
        return code;
    }

    private View errorPanel(String message) {
        LinearLayout panel = cardLayout();
        panel.setBackground(roundedDrawable(COLOR_DANGER_SOFT, COLOR_DANGER_SOFT, dp(16)));
        TextView title = titleText("暂时无法加载", 17);
        title.setTextColor(COLOR_DANGER);
        panel.addView(title);
        panel.addView(bodyText(message));
        Button retry = compactButton("重试", false);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (products.isEmpty()) {
                    loadProducts();
                } else if (selectedEnvCode == null) {
                    loadEnvironments();
                } else {
                    loadVersions();
                }
            }
        });
        panel.addView(retry, fullWidthParams());
        return panel;
    }

    private View createLoadingCard(String message) {
        LinearLayout card = cardLayout();
        LinearLayout row = horizontalLayout();
        row.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar progress = new ProgressBar(this);
        progress.getIndeterminateDrawable().setTint(COLOR_PRIMARY);
        row.addView(progress, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView text = normalText(message);
        text.setPadding(dp(12), 0, 0, 0);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row);
        return card;
    }

    private View createEmptyCard(String title, String description) {
        LinearLayout card = cardLayout();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(20), dp(28), dp(20), dp(28));
        TextView icon = titleText("○", 34);
        icon.setTextColor(COLOR_MUTED);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon);
        TextView titleView = titleText(title, 17);
        titleView.setGravity(Gravity.CENTER);
        card.addView(titleView);
        TextView descriptionView = smallText(description);
        descriptionView.setGravity(Gravity.CENTER);
        card.addView(descriptionView);
        return card;
    }

    private View pageSectionTitle(String title, String description) {
        LinearLayout group = verticalLayout();
        group.addView(titleText(title, 17));
        group.addView(smallText(description));
        return group;
    }

    private TextView emptyHint(String message) {
        TextView view = smallText(message);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(20), dp(8), dp(16));
        return view;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_DIVIDER);
        divider.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return divider;
    }

    private LinearLayout cardLayoutWithText(String text) {
        LinearLayout card = cardLayout();
        card.addView(normalText(text));
        return card;
    }

    private View createSpinner(String label, String[] values, String selectedValue, final SpinnerAction action) {
        LinearLayout panel = verticalLayout();
        TextView labelView = smallText(label);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(labelView);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(indexOf(values, selectedValue));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setBackground(roundedDrawable(COLOR_SURFACE, COLOR_BORDER, dp(10)));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < parent.getCount()) {
                    action.onSelected(String.valueOf(parent.getItemAtPosition(position)));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No action needed.
            }
        });
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(-1, dp(48));
        spinnerParams.setMargins(0, dp(5), 0, dp(10));
        panel.addView(spinner, spinnerParams);
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

    private String safeFileName(String fileName) {
        String value = fileName == null || fileName.trim().isEmpty() ? "app-release.apk" : fileName;
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
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
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundedDrawable(COLOR_PANEL, COLOR_BORDER, dp(18)));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(params);
        return card;
    }

    private TextView appIcon(String appName) {
        TextView icon = new TextView(this);
        String value = displayText(appName, "A");
        icon.setText(value.substring(0, 1));
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(30);
        icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(gradientDrawable(COLOR_PRIMARY, Color.rgb(87, 151, 255), dp(17)));
        icon.setElevation(dp(3));
        return icon;
    }

    private TextView titleText(String text, int sizeSp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_TEXT);
        view.setTextSize(sizeSp);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(2), 0, dp(3));
        return view;
    }

    private TextView normalText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_TEXT);
        view.setTextSize(14);
        view.setLineSpacing(0, 1.15F);
        view.setPadding(0, dp(2), 0, dp(2));
        return view;
    }

    private TextView bodyText(String text) {
        TextView view = normalText(text);
        view.setTextColor(Color.rgb(63, 70, 84));
        view.setTextSize(14);
        view.setLineSpacing(dp(3), 1.15F);
        view.setPadding(0, dp(12), 0, dp(4));
        return view;
    }

    private TextView smallText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_MUTED);
        view.setTextSize(12);
        view.setPadding(0, dp(2), 0, dp(2));
        return view;
    }

    private TextView sectionText(String text) {
        TextView view = smallText(text);
        view.setTextColor(COLOR_PRIMARY_DARK);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(12), 0, dp(4));
        return view;
    }

    private TextView statusBadge(UpdateStatus status) {
        TextView view = new TextView(this);
        view.setText(status.getDisplayName());
        view.setTextColor(statusColor(status));
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), dp(4), dp(9), dp(4));
        view.setBackground(roundedDrawable(statusBackgroundColor(status), statusBackgroundColor(status), dp(12)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, dp(5), 0, 0);
        view.setLayoutParams(params);
        return view;
    }

    private TextView taskStatusBadge(TaskStatus status) {
        TextView view = new TextView(this);
        view.setText(status.getDisplayName());
        view.setTextColor(taskProgressColor(status));
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), dp(4), dp(9), dp(4));
        int background = TaskStatus.FAILED.equals(status) ? COLOR_DANGER_SOFT : COLOR_PRIMARY_SOFT;
        view.setBackground(roundedDrawable(background, background, dp(12)));
        return view;
    }

    private TextView environmentBadge(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_PRIMARY_DARK);
        view.setTextSize(12);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(11), dp(6), dp(11), dp(6));
        view.setBackground(roundedDrawable(COLOR_PRIMARY_SOFT, COLOR_PRIMARY_SOFT, dp(14)));
        return view;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(roundedDrawable(COLOR_PRIMARY, COLOR_PRIMARY, dp(12)));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(COLOR_PRIMARY);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(roundedDrawable(COLOR_PRIMARY_SOFT, COLOR_PRIMARY_SOFT, dp(10)));
        return button;
    }

    private Button compactButton(String text, boolean quiet) {
        Button button = quiet ? secondaryButton(text) : primaryButton(text);
        button.setTextSize(13);
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

    private int statusBackgroundColor(UpdateStatus status) {
        if (UpdateStatus.LATEST.equals(status)) {
            return COLOR_SUCCESS_SOFT;
        }
        if (UpdateStatus.UPDATE_AVAILABLE.equals(status)) {
            return COLOR_WARNING_SOFT;
        }
        if (UpdateStatus.LOCAL_NEWER.equals(status)) {
            return COLOR_DANGER_SOFT;
        }
        return COLOR_PRIMARY_SOFT;
    }

    private int taskProgressColor(TaskStatus status) {
        if (TaskStatus.FAILED.equals(status)) {
            return COLOR_DANGER;
        }
        if (TaskStatus.DOWNLOADED.equals(status) || TaskStatus.DONE.equals(status)) {
            return COLOR_SUCCESS;
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

    private GradientDrawable gradientDrawable(int startColor, int endColor, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{startColor, endColor});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidthParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private LinearLayout.LayoutParams prominentButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.setMargins(0, dp(15), 0, 0);
        return params;
    }

    private String formatSize(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "-";
        }
        return String.format(Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private String displayText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface SpinnerAction {
        void onSelected(String value);
    }
}

