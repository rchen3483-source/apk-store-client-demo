package com.example.apkstore.demo;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * APK 商店客户端主界面，直接调用研发环境 admin-service。
 *
 * @author Codex
 * @date 2026-08-28
 */
public class MainActivity extends Activity {

    /** 低饱和炭灰蓝主题，降低纯黑与高亮蓝之间的视觉冲突。 */
    private static final int COLOR_PRIMARY = Color.rgb(98, 105, 198);
    private static final int COLOR_PRIMARY_DARK = Color.rgb(164, 169, 238);
    private static final int COLOR_PRIMARY_SOFT = Color.rgb(45, 48, 74);
    private static final int COLOR_BACKGROUND = Color.rgb(23, 25, 35);
    private static final int COLOR_PANEL = Color.rgb(32, 35, 46);
    private static final int COLOR_SURFACE = Color.rgb(39, 43, 56);
    private static final int COLOR_BORDER = Color.rgb(53, 58, 74);
    private static final int COLOR_DIVIDER = Color.rgb(48, 53, 67);
    private static final int COLOR_TEXT = Color.rgb(242, 241, 245);
    private static final int COLOR_MUTED = Color.rgb(166, 167, 179);
    private static final int COLOR_SUCCESS = Color.rgb(110, 190, 145);
    private static final int COLOR_SUCCESS_SOFT = Color.rgb(38, 59, 50);
    private static final int COLOR_WARNING = Color.rgb(214, 168, 92);
    private static final int COLOR_WARNING_SOFT = Color.rgb(64, 53, 34);
    private static final int COLOR_DANGER = Color.rgb(220, 122, 130);
    private static final int COLOR_DANGER_SOFT = Color.rgb(70, 42, 49);
    private static final int COLOR_MENU_SELECTED = Color.rgb(54, 58, 88);
    private static final int ENVIRONMENT_SELECTOR_WIDTH_DP = 156;
    private static final long MENU_ANIMATION_DURATION_MILLIS = 180L;
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
    private final Map<String, Bitmap> iconCache = new ConcurrentHashMap<>();
    private final Set<String> iconLoading = ConcurrentHashMap.newKeySet();

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
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(42), dp(42));
        backParams.gravity = Gravity.TOP | Gravity.START;
        backParams.setMargins(dp(14), dp(24), 0, 0);
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
        // 详情页为悬浮返回按钮预留呼吸空间，避免内容贴住系统状态栏。
        int topPadding = selectedAppCode == null ? dp(22) : dp(64);
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
        panel.addView(createAppsSectionHeader());
        for (AppRelease product : products) {
            panel.addView(createProductCard(product));
        }
        return panel;
    }

    /** 应用工作区标题，结构对应 TestApp 的“模块标题 + 描述 + 快捷操作”。 */
    private View createAppsSectionHeader() {
        LinearLayout header = horizontalLayout();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = verticalLayout();
        heading.addView(titleText("应用", 19));
        heading.addView(smallText("管理内部构建与部署环境"));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button refreshButton = compactButton("刷新", true);
        refreshButton.setContentDescription("刷新应用列表");
        refreshButton.setOnClickListener(view -> loadProducts());
        header.addView(refreshButton, new LinearLayout.LayoutParams(dp(68), dp(38)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(2), 0, dp(14));
        header.setLayoutParams(params);
        return header;
    }

    private View createProductCard(final AppRelease product) {
        LinearLayout card = cardLayout();
        card.setOnClickListener(view -> openProduct(product.getAppCode()));
        LinearLayout row = horizontalLayout();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(appIcon(product.getAppName(), product.getIconUrl()),
                new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout details = verticalLayout();
        details.setPadding(dp(12), 0, dp(8), 0);
        details.addView(titleText(displayText(product.getAppName(), "未命名应用"), 17));
        details.addView(smallText(displayText(product.getAppCode(), "-")));
        details.addView(pill("可查看版本", COLOR_PRIMARY, COLOR_PRIMARY_SOFT, COLOR_PRIMARY));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chevron = titleText("›", 24);
        chevron.setTextColor(COLOR_MUTED);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(52)));
        card.addView(row);
        card.addView(divider());
        LinearLayout footer = horizontalLayout();
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView hint = smallText("查看版本与更新");
        footer.addView(hint, new LinearLayout.LayoutParams(0, -2, 1));
        TextView action = smallText("打开应用 ›");
        action.setTextColor(COLOR_PRIMARY);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        footer.addView(action);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.setMargins(0, dp(10), 0, 0);
        card.addView(footer, footerParams);
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
        row.addView(appIcon(product == null ? selectedAppCode : product.getAppName(),
                        product == null ? null : product.getIconUrl()),
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
        backButton.setTextSize(30);
        backButton.setGravity(Gravity.CENTER);
        backButton.setPadding(0, 0, 0, dp(5));
        backButton.setContentDescription("返回应用列表");
        backButton.setBackgroundColor(Color.TRANSPARENT);
        backButton.setElevation(0);
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

    private View createEnvironmentDropdown() {
        LinearLayout container = verticalLayout();
        container.setClipChildren(false);
        FrameLayout selector = createEnvironmentSelector();
        LinearLayout menu = createEnvironmentMenu(selector);
        selector.setOnClickListener(view -> toggleEnvironmentMenu(menu, selector));
        container.addView(selector, new LinearLayout.LayoutParams(-1, dp(42)));
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(-1, -2);
        menuParams.setMargins(0, dp(6), 0, 0);
        container.addView(menu, menuParams);
        return container;
    }

    private FrameLayout createEnvironmentSelector() {
        FrameLayout selector = new FrameLayout(this);
        selector.setBackground(roundedDrawable(COLOR_SURFACE, COLOR_BORDER, dp(11)));
        selector.setContentDescription("展开环境列表");
        TextView value = normalText(environmentDisplayValue());
        value.setTextSize(13);
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setPadding(dp(12), 0, dp(30), 0);
        selector.addView(value, new FrameLayout.LayoutParams(-1, -1));
        TextView arrow = normalText("▾");
        arrow.setTextColor(COLOR_PRIMARY_DARK);
        arrow.setTextSize(15);
        arrow.setGravity(Gravity.CENTER);
        arrow.setContentDescription("展开环境列表");
        FrameLayout.LayoutParams arrowParams = new FrameLayout.LayoutParams(dp(30), -1, Gravity.END);
        selector.addView(arrow, arrowParams);
        selector.setTag(arrow);
        return selector;
    }

    private LinearLayout createEnvironmentMenu(final FrameLayout selector) {
        LinearLayout menu = verticalLayout();
        menu.setPadding(dp(4), dp(4), dp(4), dp(4));
        menu.setBackground(roundedDrawable(COLOR_SURFACE, COLOR_BORDER, dp(10)));
        menu.setElevation(dp(3));
        menu.setVisibility(View.GONE);
        menu.setAlpha(0.0F);
        for (int i = 0; i < environments.size(); i++) {
            if (i > 0) {
                menu.addView(divider());
            }
            ApiClient.EnvironmentOption option = environments.get(i);
            menu.addView(createEnvironmentOption(option, menu, selector));
        }
        return menu;
    }

    private TextView createEnvironmentOption(final ApiClient.EnvironmentOption option,
            final LinearLayout menu, final FrameLayout selector) {
        TextView item = normalText(option.getName());
        item.setTextSize(13);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setSingleLine(true);
        item.setPadding(dp(10), 0, dp(10), 0);
        if (option.getCode().equals(selectedEnvCode)) {
            item.setTextColor(COLOR_PRIMARY_DARK);
            item.setBackgroundColor(COLOR_MENU_SELECTED);
        }
        item.setOnClickListener(view -> {
            selectEnvironment(option.getCode());
            collapseEnvironmentMenu(menu, selector);
        });
        item.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(40)));
        return item;
    }

    private void toggleEnvironmentMenu(LinearLayout menu, FrameLayout selector) {
        if (menu.getVisibility() == View.VISIBLE) {
            collapseEnvironmentMenu(menu, selector);
            return;
        }
        expandEnvironmentMenu(menu, selector);
    }

    private void expandEnvironmentMenu(LinearLayout menu, FrameLayout selector) {
        menu.setVisibility(View.VISIBLE);
        menu.setAlpha(0.0F);
        menu.setTranslationY(-dp(4));
        menu.animate().alpha(1.0F).translationY(0.0F)
                .setDuration(MENU_ANIMATION_DURATION_MILLIS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        rotateEnvironmentArrow(selector, 180.0F);
    }

    private void collapseEnvironmentMenu(LinearLayout menu, FrameLayout selector) {
        menu.animate().alpha(0.0F).translationY(-dp(4))
                .setDuration(MENU_ANIMATION_DURATION_MILLIS)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    menu.setVisibility(View.GONE);
                    menu.setTranslationY(0.0F);
                }).start();
        rotateEnvironmentArrow(selector, 0.0F);
    }

    private void rotateEnvironmentArrow(FrameLayout selector, float rotation) {
        View arrow = (View) selector.getTag();
        if (arrow != null) {
            arrow.animate().rotation(rotation)
                    .setDuration(MENU_ANIMATION_DURATION_MILLIS)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
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

    private void selectEnvironment(String environmentCode) {
        for (ApiClient.EnvironmentOption option : environments) {
            if (option.getCode().equals(environmentCode)
                    && !option.getCode().equals(selectedEnvCode)) {
                selectedEnvCode = option.getCode();
                loadVersions();
                return;
            }
        }
    }

    private View createReleaseCard(final AppRelease release) {
        LinearLayout card = cardLayout();
        UpdateStatus status = calculateStatus(release);
        card.addView(overlineText("最新构建"));
        LinearLayout header = horizontalLayout();
        header.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(0, -2, 1);
        identityParams.setMargins(0, 0, dp(8), 0);
        header.addView(createAppIdentity(release, status), identityParams);
        header.addView(createEnvironmentDropdown(),
                new LinearLayout.LayoutParams(dp(ENVIRONMENT_SELECTOR_WIDTH_DP), -2));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
        headerParams.setMargins(0, 0, 0, dp(8));
        card.addView(header, headerParams);
        card.addView(titleText("版本 " + displayText(release.getVersionName(), "-"), 18));
        card.addView(bodyText(displayText(release.getReleaseNotes(), "暂无版本提交信息")));
        View divider = divider();
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(10), 0, 0);
        card.addView(divider, dividerParams);
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

    private View createUnavailableReleaseCard(String title, String description) {
        LinearLayout card = cardLayout();
        card.addView(overlineText("当前环境"));
        AppRelease product = findProduct(selectedAppCode);
        String displayName = product == null ? appName(selectedAppCode) : product.getAppName();
        LinearLayout identity = horizontalLayout();
        identity.setGravity(Gravity.TOP);
        identity.addView(appIcon(displayName, product == null ? null : product.getIconUrl()),
                new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout details = verticalLayout();
        details.setPadding(dp(12), 0, 0, 0);
        TextView appTitle = titleText(displayText(displayName, "未命名应用"), 20);
        appTitle.setSingleLine(true);
        appTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(appTitle);
        details.addView(pill("暂无版本", COLOR_MUTED, COLOR_SURFACE, COLOR_BORDER));
        identity.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout header = horizontalLayout();
        header.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(0, -2, 1);
        identityParams.setMargins(0, 0, dp(8), 0);
        header.addView(identity, identityParams);
        header.addView(createEnvironmentDropdown(),
                new LinearLayout.LayoutParams(dp(ENVIRONMENT_SELECTOR_WIDTH_DP), -2));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
        headerParams.setMargins(0, 0, 0, dp(8));
        card.addView(header, headerParams);
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
        row.setGravity(Gravity.TOP);
        row.addView(appIcon(release.getAppName(), release.getIconUrl()),
                new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout details = verticalLayout();
        details.setPadding(dp(12), 0, 0, 0);
        TextView appTitle = titleText(displayText(release.getAppName(), "未命名应用"), 20);
        appTitle.setSingleLine(true);
        appTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(appTitle);
        details.addView(statusBadge(status));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private View createMetadataStrip(AppRelease release) {
        LinearLayout strip = horizontalLayout();
        strip.setGravity(Gravity.CENTER);
        strip.setPadding(dp(6), dp(12), dp(6), dp(12));
        strip.setBackground(roundedDrawable(COLOR_SURFACE, COLOR_BORDER, dp(8)));
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

    private boolean isLatestRelease(AppRelease latest, AppRelease candidate) {
        return latest.getRemoteReleaseId() != null
                && latest.getRemoteReleaseId().equals(candidate.getRemoteReleaseId());
    }

    private View createTaskCard(final DownloadTask task) {
        LinearLayout card = cardLayout();
        AppRelease release = task.getRelease();
        LinearLayout statusRow = horizontalLayout();
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.addView(overlineText("DOWNLOAD TASK"), new LinearLayout.LayoutParams(0, -2, 1));
        statusRow.addView(taskStatusBadge(task.getStatus()));
        card.addView(statusRow);
        LinearLayout header = horizontalLayout();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout details = verticalLayout();
        details.addView(titleText(displayText(release.getAppName(), "应用下载"), 17));
        details.addView(smallText("版本 " + displayText(release.getVersionName(), "-")));
        header.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(header);
        card.addView(bodyText(displayText(task.getMessage(), "等待开始")));
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(task.getProgress());
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(COLOR_BORDER));
        progressBar.setProgressTintList(ColorStateList.valueOf(taskProgressColor(task.getStatus())));
        LinearLayout.LayoutParams progressParams = fullWidthParams();
        progressParams.setMargins(0, dp(6), 0, dp(4));
        card.addView(progressBar, progressParams);
        LinearLayout footer = horizontalLayout();
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView progressText = smallText("已完成 " + task.getProgress() + "%");
        footer.addView(progressText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView taskHint = smallText(TaskStatus.DOWNLOADED.equals(task.getStatus())
                ? "可安装" : "后台处理中");
        taskHint.setTextColor(taskProgressColor(task.getStatus()));
        footer.addView(taskHint);
        card.addView(footer);
        if (TaskStatus.DOWNLOADED.equals(task.getStatus())) {
            LinearLayout actions = horizontalLayout();
            Button installButton = primaryButton("安装应用");
            installButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    installTask(task);
                }
            });
            actions.addView(installButton, actionButtonParams(1.0f, 0, dp(6)));
            Button deleteButton = secondaryButton("删除记录");
            deleteButton.setContentDescription("删除下载与安装记录");
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    confirmDeleteTask(task);
                }
            });
            actions.addView(deleteButton, actionButtonParams(0.0f, dp(104), 0));
            card.addView(actions, taskActionRowParams());
        } else if (isTaskDeletable(task.getStatus())) {
            Button deleteButton = secondaryButton("删除记录");
            deleteButton.setContentDescription("删除下载与安装记录");
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    confirmDeleteTask(task);
                }
            });
            card.addView(deleteButton, prominentButtonParams());
        }
        return card;
    }

    private boolean isTaskDeletable(TaskStatus status) {
        return TaskStatus.DOWNLOADED.equals(status)
                || TaskStatus.DONE.equals(status)
                || TaskStatus.FAILED.equals(status);
    }

    private void confirmDeleteTask(final DownloadTask task) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("删除该下载与安装记录？已下载的 APK 缓存也会一并移除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteTask(task))
                .show();
    }

    private void deleteTask(DownloadTask task) {
        File downloadedFile = downloadedFiles.remove(task.getTaskId());
        if (downloadedFile != null && downloadedFile.exists()) {
            downloadedFile.delete();
        }
        taskList.remove(task);
        renderPage();
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
        TextView titleView = titleText(title, 19);
        titleView.setLetterSpacing(0.01F);
        group.addView(titleView);
        TextView descriptionView = smallText(description);
        descriptionView.setTextSize(13);
        group.addView(descriptionView);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(2), 0, dp(12));
        group.setLayoutParams(params);
        return group;
    }

    /** 小号分段标签，模拟 TestApp 的模块 overline，帮助用户快速扫描信息层级。 */
    private TextView overlineText(String text) {
        TextView view = smallText(text);
        view.setTextColor(COLOR_PRIMARY);
        view.setTextSize(11);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLetterSpacing(0.04F);
        view.setPadding(0, 0, 0, dp(8));
        return view;
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
        // TestApp 卡片更接近任务面板：紧凑内边距、细边框，不使用白底大阴影。
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(roundedDrawable(COLOR_PANEL, COLOR_BORDER, dp(10)));
        card.setElevation(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private View appIcon(String appName, String iconUrl) {
        FrameLayout container = new FrameLayout(this);
        container.setBackground(roundedDrawable(COLOR_PRIMARY, COLOR_PRIMARY, dp(16)));
        container.setElevation(0);

        TextView icon = new TextView(this);
        String value = displayText(appName, "A");
        icon.setText(value.substring(0, 1));
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(30);
        icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(gradientDrawable(COLOR_PRIMARY, Color.rgb(130, 136, 218), dp(16)));
        container.addView(icon, new FrameLayout.LayoutParams(-1, -1));

        if (iconUrl != null && !iconUrl.trim().isEmpty()) {
            final String normalizedUrl = iconUrl.trim();
            final android.widget.ImageView imageView = new android.widget.ImageView(this);
            imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            imageView.setTag(normalizedUrl);
            imageView.setClipToOutline(true);
            imageView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
                }
            });
            container.addView(imageView, new FrameLayout.LayoutParams(-1, -1));
            Bitmap cached = iconCache.get(normalizedUrl);
            if (cached != null) {
                imageView.setImageBitmap(cached);
                icon.setVisibility(View.GONE);
            } else if (iconLoading.add(normalizedUrl)) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final Bitmap bitmap = ApiClient.loadIcon(normalizedUrl);
                            iconCache.put(normalizedUrl, bitmap);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (normalizedUrl.equals(imageView.getTag())) {
                                        imageView.setImageBitmap(bitmap);
                                        imageView.setVisibility(View.VISIBLE);
                                        icon.setVisibility(View.GONE);
                                    }
                                }
                            });
                        } catch (Exception ignored) {
                            // 图标加载失败时保留首字母默认图标，不影响版本浏览和下载。
                        } finally {
                            iconLoading.remove(normalizedUrl);
                        }
                    }
                });
            }
        }
        return container;
    }

    private TextView titleText(String text, int sizeSp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_TEXT);
        view.setTextSize(sizeSp);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setIncludeFontPadding(false);
        view.setPadding(0, dp(1), 0, dp(3));
        return view;
    }

    private TextView normalText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_TEXT);
        view.setTextSize(14);
        view.setLineSpacing(0, 1.15F);
        view.setIncludeFontPadding(false);
        view.setPadding(0, dp(2), 0, dp(2));
        return view;
    }

    private TextView bodyText(String text) {
        TextView view = normalText(text);
        view.setTextColor(Color.rgb(176, 188, 205));
        view.setTextSize(14);
        view.setLineSpacing(dp(3), 1.15F);
        view.setPadding(0, dp(12), 0, dp(4));
        return view;
    }

    private TextView smallText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_MUTED);
        view.setTextSize(13);
        view.setIncludeFontPadding(false);
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
        TextView view = pill(status.getDisplayName(), statusColor(status),
                statusBackgroundColor(status), statusColor(status));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, dp(5), 0, 0);
        view.setLayoutParams(params);
        return view;
    }

    private TextView taskStatusBadge(TaskStatus status) {
        int background = TaskStatus.FAILED.equals(status) ? COLOR_DANGER_SOFT : COLOR_PRIMARY_SOFT;
        return pill(status.getDisplayName(), taskProgressColor(status), background,
                taskProgressColor(status));
    }

    private TextView environmentBadge(String text) {
        return pill(text, COLOR_PRIMARY, COLOR_PRIMARY_SOFT, COLOR_PRIMARY);
    }

    private TextView pill(String text, int textColor, int backgroundColor, int strokeColor) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(11);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setPadding(dp(8), dp(3), dp(8), dp(3));
        view.setBackground(roundedDrawable(backgroundColor, strokeColor, dp(8)));
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
        button.setBackground(roundedDrawable(COLOR_PRIMARY, COLOR_PRIMARY, dp(14)));
        button.setElevation(dp(1));
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
        button.setBackground(roundedDrawable(COLOR_PRIMARY_SOFT, COLOR_PRIMARY_SOFT, dp(12)));
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

    private LinearLayout.LayoutParams taskActionRowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.setMargins(0, dp(15), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams actionButtonParams(float weight, int width, int marginLeft) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(50), weight);
        params.setMargins(marginLeft, 0, 0, 0);
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

}
