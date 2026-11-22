package com.android.support;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

/**
 * Android Keystore (AES-256-GCM) backed storage for session token & sensitive prefs.
 * Never persists passwords or plaintext session metadata.
 */
public final class SecureTokenStore {

    private static final String PREF_KEY = "KEY";
    private static final String LEGACY_PREF_SAVE = "save";

    private static final String KEY_ENC_TOKEN = "enc_tok";
    private static final String KEY_ENC_DEVICE_ID = "enc_did";
    private static final String KEY_ENC_AW_SESSION = "enc_aws";
    private static final String KEY_WRAPPED_AES = "enc_wk";

    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String AES_KEY_ALIAS = "modxlab_aes_gcm_v1";
    private static final String RSA_KEY_ALIAS = "modxlab_rsa_wrap_v1";
    private static final String PREFIX_GCM = "g1:";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;

    private SecureTokenStore() { }

    public static void purgeLegacyPlaintext(Context ctx) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        try {
            SharedPreferences legacySave = app.getSharedPreferences(LEGACY_PREF_SAVE, Context.MODE_PRIVATE);
            if (!legacySave.getAll().isEmpty()) {
                legacySave.edit().clear().apply();
            }
        } catch (Throwable ignored) { }

        try {
            SharedPreferences prefs = app.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
            if (prefs.contains("token") || prefs.contains("User") || prefs.contains("Status")
                    || prefs.contains("expiry") || prefs.contains("device_id")
                    || prefs.contains("aw_session_cookie")) {
                prefs.edit()
                        .remove("token")
                        .remove("User")
                        .remove("Status")
                        .remove("expiry")
                        .remove("device_id")
                        .remove("aw_session_cookie")
                        .apply();
            }
        } catch (Throwable ignored) { }
    }

    public static void saveToken(Context ctx, String token) {
        if (ctx == null || token == null || token.isEmpty()) return;
        putEncrypted(ctx, KEY_ENC_TOKEN, token);
    }

    public static String loadToken(Context ctx) {
        return getEncrypted(ctx, KEY_ENC_TOKEN);
    }

    public static void clearToken(Context ctx) {
        if (ctx == null) return;
        prefs(ctx).edit().remove(KEY_ENC_TOKEN).apply();
    }

    public static void saveDeviceId(Context ctx, String deviceId) {
        if (ctx == null || deviceId == null || deviceId.isEmpty()) return;
        putEncrypted(ctx, KEY_ENC_DEVICE_ID, deviceId);
    }

    public static String loadDeviceId(Context ctx) {
        return getEncrypted(ctx, KEY_ENC_DEVICE_ID);
    }

    public static void saveAppwriteSession(Context ctx, String cookie) {
        if (ctx == null || cookie == null || cookie.isEmpty()) return;
        putEncrypted(ctx, KEY_ENC_AW_SESSION, cookie);
    }

    public static String loadAppwriteSession(Context ctx) {
        return getEncrypted(ctx, KEY_ENC_AW_SESSION);
    }

    public static void clearAppwriteSession(Context ctx) {
        if (ctx == null) return;
        prefs(ctx).edit().remove(KEY_ENC_AW_SESSION).apply();
    }

    public static String extractUserFromToken(String token) {
        if (token == null) return "";
        int dot = token.indexOf('.');
        if (dot <= 0) return "";
        try {
            byte[] decoded = Base64.decode(token.substring(0, dot), Base64.URL_SAFE | Base64.NO_WRAP);
            JSONObject obj = new JSONObject(new String(decoded, "UTF-8"));
            return obj.optString("u", "").trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        return app.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
    }

    private static synchronized void putEncrypted(Context ctx, String prefKey, String plaintext) {
        try {
            SecretKey key = getOrCreateAesKey(ctx);
            if (key == null) return;

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv;
            byte[] encrypted;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cipher.init(Cipher.ENCRYPT_MODE, key);
                iv = cipher.getIV();
                encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));
            } else {
                iv = new byte[GCM_IV_LEN];
                new SecureRandom().nextBytes(iv);
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
                encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));
            }

            if (iv == null || iv.length != GCM_IV_LEN) return;
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            String encoded = PREFIX_GCM + Base64.encodeToString(combined, Base64.NO_WRAP);
            prefs(ctx).edit().putString(prefKey, encoded).apply();
        } catch (Throwable ignored) { }
    }

    private static synchronized String getEncrypted(Context ctx, String prefKey) {
        try {
            String stored = prefs(ctx).getString(prefKey, "");
            if (stored == null || !stored.startsWith(PREFIX_GCM)) return "";

            byte[] combined = Base64.decode(stored.substring(PREFIX_GCM.length()), Base64.NO_WRAP);
            if (combined == null || combined.length <= GCM_IV_LEN) return "";

            byte[] iv = new byte[GCM_IV_LEN];
            byte[] cipherBytes = new byte[combined.length - GCM_IV_LEN];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LEN);
            System.arraycopy(combined, GCM_IV_LEN, cipherBytes, 0, cipherBytes.length);

            SecretKey key = getOrCreateAesKey(ctx);
            if (key == null) return "";

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    @SuppressLint("NewApi")
    private static SecretKey getOrCreateAesKey(Context ctx) {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER);
            ks.load(null);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ks.containsAlias(AES_KEY_ALIAS)) {
                    KeyStore.Entry entry = ks.getEntry(AES_KEY_ALIAS, null);
                    if (entry instanceof KeyStore.SecretKeyEntry) {
                        return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
                    }
                }
                KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                        AES_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build();
                kg.init(spec);
                return kg.generateKey();
            }

            // API 21-22: wrap a random 256-bit AES key using an AndroidKeyStore RSA keypair
            if (!ks.containsAlias(RSA_KEY_ALIAS)) {
                Calendar start = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                end.add(Calendar.YEAR, 25);
                Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
                @SuppressWarnings("deprecation")
                KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(app)
                        .setAlias(RSA_KEY_ALIAS)
                        .setSubject(new X500Principal("CN=" + RSA_KEY_ALIAS))
                        .setSerialNumber(BigInteger.ONE)
                        .setStartDate(start.getTime())
                        .setEndDate(end.getTime())
                        .build();
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", KEYSTORE_PROVIDER);
                kpg.initialize(spec);
                KeyPair unused = kpg.generateKeyPair();
            }

            KeyStore.PrivateKeyEntry privEntry = (KeyStore.PrivateKeyEntry) ks.getEntry(RSA_KEY_ALIAS, null);
            if (privEntry == null) return null;

            SharedPreferences p = prefs(ctx);
            String wrappedB64 = p.getString(KEY_WRAPPED_AES, "");
            if (wrappedB64 != null && !wrappedB64.isEmpty()) {
                byte[] wrapped = Base64.decode(wrappedB64, Base64.NO_WRAP);
                Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                rsa.init(Cipher.DECRYPT_MODE, privEntry.getPrivateKey());
                byte[] rawAes = rsa.doFinal(wrapped);
                return new SecretKeySpec(rawAes, "AES");
            }

            byte[] rawAes = new byte[32];
            new SecureRandom().nextBytes(rawAes);
            Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsa.init(Cipher.ENCRYPT_MODE, privEntry.getCertificate().getPublicKey());
            byte[] wrapped = rsa.doFinal(rawAes);
            p.edit().putString(KEY_WRAPPED_AES, Base64.encodeToString(wrapped, Base64.NO_WRAP)).apply();
            return new SecretKeySpec(rawAes, "AES");
        } catch (Throwable t) {
            return null;
        }
    }
}
