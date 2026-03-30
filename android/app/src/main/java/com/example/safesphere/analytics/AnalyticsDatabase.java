package com.example.safesphere.analytics;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {AnalyticsEvent.class}, version = 1, exportSchema = false)
public abstract class AnalyticsDatabase extends RoomDatabase {

    private static volatile AnalyticsDatabase INSTANCE;

    public abstract AnalyticsDao analyticsDao();

    public static AnalyticsDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AnalyticsDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AnalyticsDatabase.class,
                                    "safesphere_analytics.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static void destroyInstance() {
        if (INSTANCE != null && INSTANCE.isOpen()) {
            INSTANCE.close();
        }
        INSTANCE = null;
    }
}
