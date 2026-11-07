package com.example.todolist;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reviews")
public class ReviewRecord {
    @PrimaryKey @NonNull public String id = "";
    public String goals = "[]";
    public String answers = "[]";
    public boolean finished;
    public long updatedAt;
    /** Mood check-in: -1 unset, 0 😄 / 1 🙂 / 2 😐 / 3 😔 / 4 😤. */
    @ColumnInfo(defaultValue = "-1") public int mood = -1;
}
