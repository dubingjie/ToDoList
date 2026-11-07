package com.example.todolist;

import androidx.room.*;
import java.util.List;

@Dao
public interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY completedAt IS NOT NULL, priority DESC, createdAt DESC") List<Task> all();
    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC") List<Task> trash();
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY completedAt IS NOT NULL, priority DESC, createdAt DESC") androidx.lifecycle.LiveData<List<Task>> observeAll();
    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC") androidx.lifecycle.LiveData<List<Task>> observeTrash();
    @Query("UPDATE tasks SET deletedAt = :time WHERE id = :id AND deletedAt IS NULL") void moveToTrash(long id, long time);
    @Query("UPDATE tasks SET deletedAt = NULL WHERE id = :id") void restore(long id);
    @Query("SELECT * FROM tasks WHERE id = :id") Task find(long id);
    @Insert(onConflict = OnConflictStrategy.REPLACE) long save(Task task);
    @Delete void delete(Task task);
    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL") void emptyTrash();
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertAll(List<Task> tasks);
}
