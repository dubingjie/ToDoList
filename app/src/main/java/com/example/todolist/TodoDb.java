package com.example.todolist;

import android.content.Context;
import androidx.room.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Task.class}, version = 3, exportSchema = true)
public abstract class TodoDb extends RoomDatabase {
    public abstract TaskDao tasks();
    private static volatile TodoDb instance;
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
    public static final androidx.room.migration.Migration MIGRATION_1_2 = new androidx.room.migration.Migration(1, 2) {
        @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN deletedAt INTEGER");
        }
    };
    public static final androidx.room.migration.Migration MIGRATION_2_3 = new androidx.room.migration.Migration(2, 3) {
        @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN startAt INTEGER");
        }
    };
    public static TodoDb get(Context context) {
        if (instance == null) synchronized (TodoDb.class) {
            if (instance == null) instance = Room.databaseBuilder(context.getApplicationContext(), TodoDb.class, "todo.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build();
        }
        return instance;
    }
}
