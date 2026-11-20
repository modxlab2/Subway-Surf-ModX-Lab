package com.android.support;

import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * PinnedHttp — HTTPS client.
 *
 * - get(): performs pinned HTTPS GET for Firebase RTDB (/update.json)
 * - postJson(): performs standard system-verified HTTPS POST for Appwrite Functions
 */
public final class PinnedHttp {

    private static final String TAG = "ModXLab_Http";

    // Set to false temporarily if Google rotates certs and pins fail
    private static final boolean ENFORCE_PINS = true;

    private static final String[] PINS = {
            // Google Trust Services Root R1
            "hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=",
            // Google Trust Services Root R2
            "Vfd95BwDeSQo+NUYxVEEIlvkOlWY2SalKK1lPhzOx78=",
            // Google Trust Services Root R3
            "QXnt2YHvdHR3tJYmQIr0Paosp6t/nggsEGD4QJZ3Q0g=",
            // Google Trust Services Root R4
            "mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
            // GlobalSign Root CA - R2 (older Firebase chains)
            "iie1VXtL7HzAMF+/PVPR9xzT80kQxdZeJ+zduCB3uj0=",
    };

    private static volatile SSLSocketFactory sFactory = null;
    private static final Object sLock = new Object();

    private PinnedHttp() { }

    public static final class HttpResponse {
        public final int code;
        public final String body;
        public final List<String> setCookies;

        public HttpResponse(int code, String body, List<String> setCookies) {
            this.code = code;
            this.body = body;
            this.setCookies = setCookies != null ? setCookies : Collections.<String>emptyList();
        }
    }

    /**
     * Performs a pinned HTTPS GET and returns the response body as a String.
     */
    public static String get(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return null;

        HttpsURLConnection conn = null;
        try {
            SSLSocketFactory factory = getFactory();

            URL url = new URL(urlStr);
            if (!"https".equalsIgnoreCase(url.getProtocol())) return null;

            conn = (HttpsURLConnection) url.openConnection();
            if (factory != null) conn.setSSLSocketFactory(factory);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "ModXLab/1.0");

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "HTTP " + code + " for GET");
                return null;
            }

            return readStream(conn.getInputStream());
        } catch (Exception e) {
            Log.e(TAG, "get failed: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Exception ignored) { }
        }
    }

    /**
     * Performs a system-CA-verified HTTPS POST with a JSON body.
     */
    public static HttpResponse postJson(String urlStr, String jsonBody, Map<String, String> headers) {
        if (urlStr == null || urlStr.isEmpty()) return null;

        HttpsURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            if (!"https".equalsIgnoreCase(url.getProtocol())) return null;

            conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "ModXLab/1.0");

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        conn.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
            }

            byte[] payload = (jsonBody != null ? jsonBody : "{}").getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(payload.length);
            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 400)
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            String body = stream != null ? readStream(stream) : "";

            List<String> cookies = null;
            Map<String, List<String>> respHeaders = conn.getHeaderFields();
            if (respHeaders != null) {
                for (Map.Entry<String, List<String>> h : respHeaders.entrySet()) {
                    if (h.getKey() != null && "Set-Cookie".equalsIgnoreCase(h.getKey())) {
                        cookies = h.getValue();
                        break;
                    }
                }
            }
            return new HttpResponse(code, body, cookies);
        } catch (Exception e) {
            Log.e(TAG, "postJson failed: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Exception ignored) { }
        }
    }

    private static String readStream(InputStream is) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private static SSLSocketFactory getFactory() {
        if (sFactory != null) return sFactory;
        synchronized (sLock) {
            if (sFactory != null) return sFactory;
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null,
                        new TrustManager[]{ new PinTrustManager() },
                        new SecureRandom());
                sFactory = ctx.getSocketFactory();
            } catch (Exception e) {
                Log.e(TAG, "SSLContext init failed: " + e.getMessage());
            }
            return sFactory;
        }
    }

    private static final class PinTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            if (!ENFORCE_PINS) return;
            if (chain == null || chain.length == 0) {
                throw new CertificateException("Empty certificate chain");
            }
            for (X509Certificate cert : chain) {
                String spki = spkiSha256Base64(cert);
                if (spki == null) continue;
                for (String pin : PINS) {
                    if (pin.equals(spki)) return;
                }
            }
            throw new CertificateException("No matching pin in certificate chain");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        private static String spkiSha256Base64(X509Certificate cert) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] spki = cert.getPublicKey().getEncoded();
                byte[] hash = md.digest(spki);
                return Base64.encodeToString(hash, Base64.NO_WRAP);
            } catch (Exception e) {
                return null;
            }
        }
    }
}