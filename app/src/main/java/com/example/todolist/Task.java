package com.example.todolist;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class Task {
    @PrimaryKey(autoGenerate = true) public long id;
    public String title = "";
    public String tag = "学习";
    public int priority = 1;
    public long createdAt = System.currentTimeMillis();
    public Long dueAt;
    public Long startAt;
    public Integer estimatedMinutes;
    public Long reminderAt;
    /** Calendar day this pending item belongs to; null keeps legacy items visible today. */
    public Long taskDate;
    public Long completedAt;
    public Long deletedAt;
    /** Recurrence: "" (none), "daily" or "weekly". Completing spawns the next occurrence. */
    @ColumnInfo(defaultValue = "") public String repeat = "";
    @ColumnInfo(defaultValue = "") public String note = "";
    /** JSON array of {"t": title, "done": bool} sub-steps. */
    @ColumnInfo(defaultValue = "[]") public String substeps = "[]";
    public Task copy() {
        Task t = new Task();
        t.id = id; t.title = title; t.tag = tag; t.priority = priority;
        t.createdAt = createdAt; t.dueAt = dueAt; t.startAt = startAt; t.estimatedMinutes = estimatedMinutes; t.reminderAt = reminderAt; t.taskDate = taskDate; t.completedAt = completedAt; t.deletedAt = deletedAt;
        t.repeat = repeat; t.note = note; t.substeps = substeps;
        return t;
    }
}
