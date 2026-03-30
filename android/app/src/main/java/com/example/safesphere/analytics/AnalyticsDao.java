package com.example.safesphere.analytics;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AnalyticsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertEvent(AnalyticsEvent event);

    /** Fetch up to 100 unsynced events that are ready to retry (nextRetryMs <= now). */
    @Query("SELECT * FROM analytics_events WHERE synced = 0 " +
           "AND nextRetryMs <= :nowMs ORDER BY clientTsMs ASC LIMIT 100")
    List<AnalyticsEvent> getPendingEvents(long nowMs);

    @Query("UPDATE analytics_events SET synced = 1 WHERE eventId IN (:ids)")
    void markSynced(List<String> ids);

    @Query("UPDATE analytics_events SET " +
           "retryCount = retryCount + 1, nextRetryMs = :nextRetryMs " +
           "WHERE eventId IN (:ids)")
    void markRetry(List<String> ids, long nextRetryMs);

    @Query("SELECT COUNT(*) FROM analytics_events WHERE synced = 0")
    int countPending();

    /** Housekeeping: delete synced events older than 7 days. */
    @Query("DELETE FROM analytics_events WHERE synced = 1 AND createdAtMs < :cutoffMs")
    int deleteSyncedOlderThan(long cutoffMs);

    /** Backpressure: evict oldest non-critical events when queue is too large. */
    @Query("DELETE FROM analytics_events WHERE eventId IN (" +
           "SELECT eventId FROM analytics_events WHERE synced = 0 " +
           "AND eventType NOT IN ('trigger_source','sms_sent','call_connected','call_attempt')" +
           " ORDER BY clientTsMs ASC LIMIT :count)")
    void evictOldestNonCritical(int count);

    /** Delete all events (called on forced logout). */
    @Query("DELETE FROM analytics_events")
    void deleteAll();
}
