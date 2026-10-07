package com.example.todolist;

import androidx.room.*;

@Dao
public interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE id = :id") ReviewRecord find(String id);
    @Query("SELECT * FROM reviews WHERE id LIKE :prefix || '%' ORDER BY substr(id, 3) DESC") java.util.List<ReviewRecord> list(String prefix);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void save(ReviewRecord record);
    @Query("UPDATE reviews SET answers = '[]', finished = 0, updatedAt = 0 WHERE id = :id") void clearContent(String id);
}
