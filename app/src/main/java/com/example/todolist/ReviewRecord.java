package com.example.todolist;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reviews")
public class ReviewRecord {
    @PrimaryKey @NonNull public String id = "";
    public String goals = "[]";
    public String answers = "[]";
    public boolean finished;
    public long updatedAt;
}
