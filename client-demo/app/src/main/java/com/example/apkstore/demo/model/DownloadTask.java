package com.example.apkstore.demo.model;

import java.io.Serializable;

/**
 * 模拟下载任务。
 *
 * @author Codex
 * @date 2026-07-13
 */
public class DownloadTask implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long taskId;
    private AppRelease release;
    private Integer progress;
    private TaskStatus status;
    private String message;

    public DownloadTask(Long taskId, AppRelease release) {
        this.taskId = taskId;
        this.release = release;
        this.progress = 0;
        this.status = TaskStatus.WAITING;
        this.message = "等待开始";
    }

    public Long getTaskId() {
        return taskId;
    }

    public AppRelease getRelease() {
        return release;
    }

    public Integer getProgress() {
        return progress;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public void update(Integer progress, TaskStatus status, String message) {
        this.progress = progress;
        this.status = status;
        this.message = message;
    }
}
