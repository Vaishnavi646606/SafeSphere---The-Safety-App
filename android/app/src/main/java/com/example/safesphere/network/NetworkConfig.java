package com.example.safesphere.network;

/**
 * Single source of truth for all backend URLs.
 * REPLACE base_url with your actual Vercel deployment URL.
 */
public final class NetworkConfig {
    private NetworkConfig() {}

    // ⚠️ REPLACE THIS with your actual Vercel domain
    public static final String BASE_URL = "https://YOUR_APP.vercel.app/api/";

    // Supabase anon key — safe in APK because RLS protects data server-side
    public static final String SUPABASE_ANON_KEY = "YOUR_SUPABASE_ANON_KEY";

    // Salt for phone hashing — MUST match PHONE_HASH_SALT in Vercel environment
    public static final String PHONE_HASH_SALT = "SafeSphere2024SecureSalt32Chars!!";

    // Timeouts in seconds
    public static final int CONNECT_TIMEOUT_SEC = 15;
    public static final int READ_TIMEOUT_SEC = 30;
    public static final int WRITE_TIMEOUT_SEC = 30;
}
