package com.example.apkstore.demo.net;

import com.example.apkstore.demo.BuildConfig;
import com.example.apkstore.demo.model.AppRelease;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** APK 商店研发环境接口客户端。 */
public final class ApiClient {

    /** 研发环境 admin-service 网关域名，可在这里替换为实际部署域名。 */
    public static final String BASE_URL = BuildConfig.APK_STORE_BASE_URL;
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;

    public List<AppRelease> getApps() throws Exception {
        JSONArray data = requestArray("/v1/apk-store/apps");
        List<AppRelease> result = new ArrayList<>(data.length());
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.getJSONObject(i);
            AppRelease option = new AppRelease();
            option.setAppCode(item.optString("appCode"));
            option.setAppName(item.optString("appName"));
            result.add(option);
        }
        return result;
    }

    public List<EnvironmentOption> getEnvironments(String appCode) throws Exception {
        JSONObject requestBody = new JSONObject();
        requestBody.put("appCode", appCode);
        JSONArray data = postArray("/v1/apk-store/apps/environments", requestBody);
        List<EnvironmentOption> result = new ArrayList<>(data.length());
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.getJSONObject(i);
            result.add(new EnvironmentOption(item.optString("envCode"), item.optString("envName")));
        }
        return result;
    }

    public AppRelease getLatest(String appCode, String envCode) throws Exception {
        JSONObject requestBody = new JSONObject();
        requestBody.put("appCode", appCode);
        requestBody.put("envCode", envCode);
        try {
            JSONObject data = postObject("/v1/apk-store/apps/latest", requestBody);
            return parseRelease(data);
        } catch (ApiBusinessException exception) {
            if ("404002".equals(exception.getCode())) {
                return null;
            }
            throw exception;
        }
    }

    public List<AppRelease> getHistory(String appCode, String envCode, String keyword) throws Exception {
        JSONObject requestBody = new JSONObject();
        requestBody.put("appCode", appCode);
        requestBody.put("envCode", envCode);
        requestBody.put("page", 1);
        requestBody.put("pageSize", 6);
        requestBody.put("keyword", keyword == null ? "" : keyword.trim());
        JSONArray data = postArray("/v1/apk-store/apps/releases", requestBody);
        if (data == null) {
            data = new JSONArray();
        }
        List<AppRelease> result = new ArrayList<>(data.length());
        for (int i = 0; i < data.length(); i++) {
            result.add(parseRelease(data.getJSONObject(i)));
        }
        return result;
    }

    public DownloadInfo getDownloadInfo(String releaseId) throws Exception {
        JSONObject data = requestObject("/v1/apk-store/releases/" + encode(releaseId) + "/download");
        String downloadUrl = data.optString("downloadUrl");
        if (downloadUrl.isEmpty()) {
            throw new IllegalStateException("下载接口未返回 downloadUrl");
        }
        return new DownloadInfo(data.optString("fileName", "app-release.apk"),
                downloadUrl, data.optLong("apkSize", 0L));
    }

    public static void downloadFile(String downloadUrl, File target, ProgressListener listener) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("APK 下载失败，HTTP " + status);
            }
            long total = connection.getContentLengthLong();
            long completed = 0L;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    completed += count;
                    if (listener != null) {
                        listener.onProgress(completed, total);
                    }
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JSONArray requestArray(String path) throws Exception {
        JSONArray data = request(path).optJSONArray("data");
        if (data == null) {
            throw new IllegalStateException("接口返回 data 不是数组");
        }
        return data;
    }

    private JSONArray postArray(String path, JSONObject requestBody) throws Exception {
        JSONArray data = request(path, "POST", requestBody.toString()).optJSONArray("data");
        if (data == null) {
            throw new IllegalStateException("接口返回 data 不是数组");
        }
        return data;
    }

    private JSONObject requestObject(String path) throws Exception {
        JSONObject data = request(path).optJSONObject("data");
        if (data == null) {
            throw new IllegalStateException("接口返回 data 为空");
        }
        return data;
    }

    private JSONObject postObject(String path, JSONObject requestBody) throws Exception {
        JSONObject data = request(path, "POST", requestBody.toString()).optJSONObject("data");
        if (data == null) {
            throw new IllegalStateException("接口返回 data 为空");
        }
        return data;
    }

    private JSONObject request(String path) throws Exception {
        return request(path, "GET", null);
    }

    private JSONObject request(String path, String method, String requestBody) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            if (requestBody != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (java.io.OutputStream output = connection.getOutputStream()) {
                    output.write(requestBody.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readBody(stream);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("接口请求失败，HTTP " + status + "：" + body);
            }
            JSONObject envelope = new JSONObject(body);
            if (!"000000".equals(envelope.optString("code"))) {
                throw new ApiBusinessException(envelope.optString("code"),
                        envelope.optString("desc", "接口返回业务失败"));
            }
            return envelope;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readBody(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private AppRelease parseRelease(JSONObject item) {
        AppRelease release = new AppRelease();
        release.setRemoteReleaseId(item.optString("releaseId"));
        release.setAppCode(item.optString("appCode"));
        release.setAppName(item.optString("appName"));
        release.setPackageName(item.optString("packageName"));
        release.setEnvCode(item.optString("envCode"));
        release.setChannelCode(item.optString("channelCode"));
        release.setVersionName(item.optString("versionName"));
        long buildNo = item.optLong("buildNo", 0L);
        release.setVersionCode(buildNo);
        release.setBuildNo(String.valueOf(buildNo));
        release.setApkUrl(item.optString("apkUrl"));
        release.setApkSize(item.optLong("apkSize", 0L));
        release.setReleaseNotes(item.optString("releaseNotes"));
        return release;
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
    }

    public interface ProgressListener {
        void onProgress(long completed, long total);
    }

    private static final class ApiBusinessException extends IllegalStateException {
        private final String code;

        private ApiBusinessException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String getCode() {
            return code;
        }
    }

    public static final class EnvironmentOption {
        private final String code;
        private final String name;

        public EnvironmentOption(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name + "（" + code + "）";
        }
    }

    public static final class DownloadInfo {
        private final String fileName;
        private final String downloadUrl;
        private final long apkSize;

        public DownloadInfo(String fileName, String downloadUrl, long apkSize) {
            this.fileName = fileName;
            this.downloadUrl = downloadUrl;
            this.apkSize = apkSize;
        }

        public String getFileName() {
            return fileName;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public long getApkSize() {
            return apkSize;
        }
    }
}
