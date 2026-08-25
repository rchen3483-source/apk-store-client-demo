package com.example.apkstore.demo.model;

import java.io.Serializable;

/**
 * 操作日志。
 *
 * @author Codex
 * @date 2026-07-13
 */
public class OperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private String time;
    private String title;
    private String content;

    public OperationLog(String time, String title, String content) {
        this.time = time;
        this.title = title;
        this.content = content;
    }

    public String getTime() {
        return time;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }
}
