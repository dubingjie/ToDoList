package com.example.todolist;

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
    /** Calendar day this pending item belongs to; null keeps legacy items visible today. */
    public Long taskDate;
    public Long completedAt;
    public Long deletedAt;
    public Task copy() {
        Task t = new Task();
        t.id = id; t.title = title; t.tag = tag; t.priority = priority;
        t.createdAt = createdAt; t.dueAt = dueAt; t.startAt = startAt; t.taskDate = taskDate; t.completedAt = completedAt; t.deletedAt = deletedAt;
        return t;
    }
}
