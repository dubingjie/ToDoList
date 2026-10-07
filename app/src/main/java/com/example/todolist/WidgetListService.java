package com.example.todolist;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.util.Collections;
import java.util.List;

/** Android 8–11 fallback for the same full, scrollable task collection. */
public final class WidgetListService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) { return new Factory(getApplicationContext()); }
    private static final class Factory implements RemoteViewsFactory {
        private final Context context;
        private List<Task> tasks = Collections.emptyList();
        Factory(Context context) { this.context = context; }
        @Override public void onCreate() {}
        @Override public void onDataSetChanged() { tasks = TodoWidget.visibleTasks(TodoDb.get(context).tasks().all()); }
        @Override public void onDestroy() { tasks = Collections.emptyList(); }
        @Override public int getCount() { return tasks.size(); }
        @Override public RemoteViews getViewAt(int position) { return position < 0 || position >= tasks.size() ? null : TodoWidget.item(context, tasks.get(position)); }
        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) { return tasks.get(position).id; }
        @Override public boolean hasStableIds() { return true; }
    }
}
