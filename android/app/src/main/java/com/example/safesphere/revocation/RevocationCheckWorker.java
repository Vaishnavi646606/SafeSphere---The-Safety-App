package com.example.safesphere.revocation;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.safesphere.Prefs;
import com.example.safesphere.network.NetworkConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * WorkManager Periodic Worker — polls server for revocation status and admin messages.
 * Runs every 15 minutes when network is available.
 */
public class RevocationCheckWorker extends Worker {

    private static final String TAG = "RevocationCheckWorker";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public RevocationCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        String userId = Prefs.getUserId(ctx);
        if (userId == null || userId.isEmpty()) return Result.success();

        try {
            String url = NetworkConfig.BASE_URL + "revocation/check?user_id=" + userId;
            Request req = new Request.Builder().url(url).get().build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return Result.retry();

                JSONObject json = new JSONObject(resp.body().string());
                boolean isRevoked = json.optBoolean("is_revoked", false);

                if (isRevoked) {
                    int version = json.optInt("revocation_version", 1);
                    String message = json.optString("message", null);
                    RevocationHandler.handleRevocation(ctx, version, message);
                    return Result.success();
                }

                // Update local revocation version
                int serverVersion = json.optInt("revocation_version", 0);
                Prefs.setRevocationVersion(ctx, serverVersion);

                // Process pending admin messages
                if (json.has("pending_messages")) {
                    JSONArray msgs = json.getJSONArray("pending_messages");
                    for (int i = 0; i < msgs.length(); i++) {
                        JSONObject msg = msgs.getJSONObject(i);
                        Prefs.addPendingAdminMessage(ctx,
                                msg.optString("subject", "Notice"),
                                msg.optString("body", ""),
                                msg.optBoolean("is_critical", false));
                    }
                }

                return Result.success();
            }

        } catch (IOException e) {
            Log.w(TAG, "Network error (will retry): " + e.getMessage());
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
            return Result.retry();
        }
    }
}
