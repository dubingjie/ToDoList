package com.example.todolist;

import android.content.Context;
import androidx.room.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Task.class, ReviewRecord.class}, version = 8, exportSchema = true)
public abstract class TodoDb extends RoomDatabase {
    public abstract TaskDao tasks();
    public abstract ReviewDao reviews();
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
    public static final androidx.room.migration.Migration MIGRATION_3_4 = new androidx.room.migration.Migration(3, 4) {
        @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN taskDate INTEGER");
            // Existing unfinished rows belong to the day this upgrade occurs;
            // they should not silently roll over into future days.
            db.execSQL("UPDATE tasks SET taskDate = CAST(strftime('%s','now','start of day') AS INTEGER) * 1000 WHERE completedAt IS NULL");
        }
    };
    public static TodoDb get(Context context) {
        if (instance == null) synchronized (TodoDb.class) {
            if (instance == null) instance = Room.databaseBuilder(context.getApplicationContext(), TodoDb.class, "todo.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build();
        }
        return instance;
    }
    public static final androidx.room.migration.Migration MIGRATION_7_8 = new androidx.room.migration.Migration(7, 8) {
        @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE reviews ADD COLUMN mood INTEGER NOT NULL DEFAULT -1");
        }
    };
    public static final androidx.room.migration.Migration MIGRATION_6_7 = new androidx.room.migration.Migration(6, 7) {
        @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN repeat TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE tasks ADD COLUMN note TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE tasks ADD COLUMN substeps TEXT DEFAULT '[]'");
        }
    };
    public static final androidx.room.migration.Migration MIGRATION_4_5 = new androidx.room.migration.Migration(4, 5) {
        @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS reviews (id TEXT NOT NULL PRIMARY KEY, goals TEXT, answers TEXT, finished INTEGER NOT NULL, updatedAt INTEGER NOT NULL)");
        }
    };
    public static final androidx.room.migration.Migration MIGRATION_5_6 = new androidx.room.migration.Migration(5, 6) {
        @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN estimatedMinutes INTEGER");
            db.execSQL("ALTER TABLE tasks ADD COLUMN reminderAt INTEGER");
            // Preserve legacy reminder precedence; duration is deliberately not guessed.
            db.execSQL("UPDATE tasks SET reminderAt = CASE WHEN startAt IS NOT NULL THEN startAt WHEN dueAt IS NOT NULL THEN dueAt - 900000 ELSE NULL END");
        }
    };
}
