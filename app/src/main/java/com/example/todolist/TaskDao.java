package com.example.todolist;

import androidx.room.*;
import java.util.List;

@Dao
public interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY completedAt IS NOT NULL, priority DESC, dueAt IS NULL, dueAt ASC, createdAt DESC") List<Task> all();
    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC") List<Task> trash();
    @Query("UPDATE tasks SET deletedAt = :time WHERE id = :id AND deletedAt IS NULL") void moveToTrash(long id, long time);
    @Query("UPDATE tasks SET deletedAt = NULL WHERE id = :id") void restore(long id);
    @Query("SELECT * FROM tasks WHERE id = :id") Task find(long id);
    @Insert(onConflict = OnConflictStrategy.REPLACE) long save(Task task);
    @Delete void delete(Task task);
    @Insert void insertAll(List<Task> tasks);
}
