package com.example.apkstore.demo;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.FileProvider;

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

/** APK 商店客户端 Demo 主界面，直接调用研发环境 admin-service。 */
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

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final ApiClient apiClient = new ApiClient();
    private final List<DownloadTask> taskList = new ArrayList<>(8);
    private final Map<Long, File> downloadedFiles = new HashMap<>();

    private LinearLayout contentLayout;
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
        root.addView(createHeader());
        contentLayout = verticalLayout();
        root.addView(contentLayout, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        renderPage();
    }

    private View createHeader() {
        LinearLayout header = verticalLayout();
        header.setPadding(dp(16), dp(14), dp(16), dp(12));
        header.setBackgroundColor(COLOR_PANEL);
        header.addView(titleText("APK 商店客户端", 22));
        header.addView(smallText("研发环境 · 接口：" + ApiClient.BASE_URL));
        return header;
    }

    private void renderPage() {
        if (contentLayout == null) {
            return;
        }
        contentLayout.removeAllViews();
        ScrollView scrollView = new ScrollView(this);
        LinearLayout page = verticalLayout();
        page.setPadding(dp(12), dp(12), dp(12), dp(20));
        if (errorMessage != null) {
            page.addView(errorPanel(errorMessage));
        }
        if (!productsLoaded) {
            page.addView(cardLayoutWithText("加载产品中…"));
        } else if (products.isEmpty()) {
            page.addView(cardLayoutWithText("后端暂无可用产品"));
        } else if (selectedAppCode == null) {
            page.addView(createProductSelectionPanel());
        } else if (!environmentsLoaded || environments.isEmpty() || selectedEnvCode == null) {
            page.addView(createEnvironmentSelectionPanel());
        } else {
            page.addView(createSelectionSummary());
            if (latestRelease != null) {
                page.addView(createReleaseCard(latestRelease));
            } else {
                page.addView(cardLayoutWithText("该环境暂无最新版本"));
            }
        }
        if (!taskList.isEmpty()) {
            page.addView(sectionText("下载进度"));
            for (DownloadTask task : taskList) {
                page.addView(createTaskCard(task));
            }
        }
        scrollView.addView(page);
        contentLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));
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
                            renderPage();
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
                            environments = result;
                            environmentsLoaded = true;
                            selectedEnvCode = null;
                            renderPage();
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
                errorMessage = exception.getMessage() == null ? "网络请求失败" : exception.getMessage();
                renderPage();
            }
        });
    }

    private View createProductSelectionPanel() {
        LinearLayout panel = cardLayout();
        panel.addView(titleText("选择产品", 20));
        panel.addView(smallText("产品列表由后端 appCode 接口返回"));
        String[] values = new String[products.size()];
        for (int i = 0; i < products.size(); i++) {
            values[i] = products.get(i).getAppName() + "（" + products.get(i).getAppCode() + "）";
        }
        final boolean[] initialSelection = {true};
        panel.addView(createSpinner("产品", values, productDisplayValue(), new SpinnerAction() {
            @Override
            public void onSelected(String value) {
                if (initialSelection[0]) {
                    initialSelection[0] = false;
                    return;
                }
                for (AppRelease product : products) {
                    String display = product.getAppName() + "（" + product.getAppCode() + "）";
                    if (display.equals(value) && !product.getAppCode().equals(selectedAppCode)) {
                        selectedAppCode = product.getAppCode();
                        selectedEnvCode = null;
                        environments = new ArrayList<>();
                        environmentsLoaded = false;
                        loadEnvironments();
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
        panel.addView(titleText("版本中心", 20));
        panel.addView(normalText("产品：" + appName(selectedAppCode) + " · 环境：" + environmentName(selectedEnvCode)));
        Button refreshButton = secondaryButton("刷新版本");
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadVersions();
            }
        });
        panel.addView(refreshButton, fullWidthParams());
        return panel;
    }

    private View createReleaseCard(final AppRelease release) {
        LinearLayout card = cardLayout();
        UpdateStatus status = calculateStatus(release);
        LinearLayout titleRow = horizontalLayout();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(titleText(release.getAppName(), 19), new LinearLayout.LayoutParams(0, -2, 1));
        titleRow.addView(statusBadge(status));
        card.addView(titleRow);
        card.addView(normalText("appCode：" + release.getAppCode() + " · 环境：" + release.getEnvCode()
                + " · 最新 " + release.getVersionName() + " / " + release.getBuildNo()));
        card.addView(smallText("包名：" + release.getPackageName()));
        card.addView(smallText("本机版本：" + localVersion(release.getPackageName())));
        card.addView(smallText("构建号：" + release.getBuildNo()));
        card.addView(smallText("APK 大小：" + formatSize(release.getApkSize())));
        card.addView(normalText("更新说明：" + release.getReleaseNotes()));
        card.addView(createVersionActionRow("最新版本", release.getVersionName() + " · " + release.getBuildNo(),
                release, getActionText(status), true));
        card.addView(sectionText("历史版本（最近 5 个）"));
        LinearLayout searchRow = horizontalLayout();
        EditText searchInput = new EditText(this);
        searchInput.setHint("搜索版本号或构建号");
        searchInput.setText(historySearchQuery);
        searchInput.setSingleLine(true);
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, -2, 1));
        Button searchButton = secondaryButton("搜索");
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                historySearchQuery = searchInput.getText().toString().trim();
                loadVersions();
            }
        });
        searchRow.addView(searchButton, new LinearLayout.LayoutParams(dp(88), -2));
        card.addView(searchRow);
        if (historyList.isEmpty()) {
            card.addView(smallText("没有匹配的历史版本"));
        } else {
            int shown = 0;
            for (AppRelease historyRelease : historyList) {
                if (release.getRemoteReleaseId() != null
                        && release.getRemoteReleaseId().equals(historyRelease.getRemoteReleaseId())) {
                    continue;
                }
                card.addView(createVersionActionRow("历史 " + historyRelease.getVersionName(),
                        historyRelease.getBuildNo() + " · " + formatSize(historyRelease.getApkSize()),
                        historyRelease, "下载", false));
                shown++;
                if (shown == 5) {
                    break;
                }
            }
            if (shown == 0) {
                card.addView(smallText("没有匹配的历史版本"));
            }
        }
        return card;
    }

    private View createVersionActionRow(String title, String description, final AppRelease release,
            String actionText, boolean primary) {
        LinearLayout row = verticalLayout();
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(roundedDrawable(COLOR_SURFACE, COLOR_BORDER, dp(8)));
        row.addView(normalText(title));
        row.addView(smallText(description));
        Button button = primary ? primaryButton(actionText) : secondaryButton(actionText);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDownload(release);
            }
        });
        row.addView(button, fullWidthParams());
        return row;
    }

    private View createTaskCard(final DownloadTask task) {
        LinearLayout card = cardLayout();
        AppRelease release = task.getRelease();
        card.addView(titleText(release.getAppName() + " · " + task.getStatus().getDisplayName(), 18));
        card.addView(smallText(release.getVersionName() + " · " + task.getMessage()));
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(task.getProgress());
        card.addView(progressBar, fullWidthParams());
        card.addView(smallText("进度：" + task.getProgress() + "%"));
        if (TaskStatus.DOWNLOADED.equals(task.getStatus())) {
            Button installButton = primaryButton("下载完成，安装");
            installButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    installTask(task);
                }
            });
            card.addView(installButton, fullWidthParams());
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
                try {
                    final ApiClient.DownloadInfo info = apiClient.getDownloadInfo(release.getRemoteReleaseId());
                    final File parent = getExternalFilesDir("apk");
                    if (parent == null) {
                        throw new IllegalStateException("无法获取 APK 缓存目录");
                    }
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw new IllegalStateException("无法创建 APK 缓存目录");
                    }
                    final File target = new File(parent, safeFileName(info.getFileName()));
                    final int[] lastProgress = {-1};
                    ApiClient.downloadFile(info.getDownloadUrl(), target, new ApiClient.ProgressListener() {
                        @Override
                        public void onProgress(final long completed, final long total) {
                            final int progress = total <= 0 ? 0 : (int) Math.min(99, completed * 100 / total);
                            if (progress == lastProgress[0]) {
                                return;
                            }
                            lastProgress[0] = progress;
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    task.update(progress, TaskStatus.DOWNLOADING, "正在下载 APK");
                                    renderPage();
                                }
                            });
                        }
                    });
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            downloadedFiles.put(task.getTaskId(), target);
                            task.update(100, TaskStatus.DOWNLOADED, "下载完成，请点击安装");
                            renderPage();
                        }
                    });
                } catch (final Exception exception) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            task.update(task.getProgress(), TaskStatus.FAILED, exception.getMessage());
                            renderPage();
                        }
                    });
                }
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
        panel.addView(titleText("请求失败", 18));
        panel.addView(normalText(message));
        Button retry = secondaryButton("重试");
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

    private LinearLayout cardLayoutWithText(String text) {
        LinearLayout card = cardLayout();
        card.addView(normalText(text));
        return card;
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
                if (position >= 0 && position < parent.getCount()) {
                    action.onSelected(String.valueOf(parent.getItemAtPosition(position)));
                }
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
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundedDrawable(COLOR_PANEL, COLOR_BORDER, dp(10)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private TextView titleText(String text, int sizeSp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_TEXT);
        view.setTextSize(sizeSp);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private TextView normalText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_TEXT);
        view.setTextSize(14);
        view.setPadding(0, dp(3), 0, dp(3));
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
        view.setPadding(0, dp(12), 0, dp(4));
        return view;
    }

    private TextView statusBadge(UpdateStatus status) {
        TextView view = new TextView(this);
        view.setText(status.getDisplayName());
        view.setTextColor(Color.WHITE);
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(4), dp(10), dp(4));
        view.setBackground(roundedDrawable(statusColor(status), statusColor(status), dp(14)));
        return view;
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

    private String formatSize(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "-";
        }
        return String.format(Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface SpinnerAction {
        void onSelected(String value);
    }
}

