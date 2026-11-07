package com.example.todolist;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import java.util.List;

/** Observed task data; UI refreshes itself on any database change. */
public class MainViewModel extends AndroidViewModel {
    public final LiveData<List<Task>> tasks;
    public final LiveData<List<Task>> trashed;
    public MainViewModel(Application app) {
        super(app);
        TaskDao dao = TodoDb.get(app).tasks();
        tasks = dao.observeAll();
        trashed = dao.observeTrash();
    }
}
