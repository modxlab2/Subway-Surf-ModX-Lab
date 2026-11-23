package com.android.support;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.security.KeyStore;
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
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * PinnedHttp — Certificate-pinned HTTPS client with system trust-chain validation.
 */
public final class PinnedHttp {

    private static final boolean ENFORCE_PINS = true;

    // Endpoint-specific SPKI SHA-256 pins (Base64): intermediate CA + leaf backup pin.
    private static final String[] FIREBASE_PINS = {
            "yDu9og255NN5GEf+Bwa9rTrqFQ0EydZ0r1FCh9TdAW4=",
            "GCcSISJfxoi/efNtgJJf9PIBy99dfmaA59y/dH2niKs="
    };

    private static final String[] APPWRITE_PINS = {
            "oW7smChMJRcnzTObF7K+HzInReAPTxB/L1h6eZTmw9Q=",
            "5GonF/tvwb2QR5Xx1FH50B8Ek/Lc15olTW82TEkdwTs="
    };

    private static volatile SSLSocketFactory sFirebaseFactory = null;
    private static volatile SSLSocketFactory sAppwriteFactory = null;
    private static final Object sLock = new Object();
    private static final ThreadLocal<String> sLastError = new ThreadLocal<String>();

    private PinnedHttp() { }

    public static String getLastError() {
        String err = sLastError.get();
        return (err != null && !err.isEmpty()) ? err : "network";
    }

    private static String classifyException(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            String msg = cur.getMessage();
            if (msg != null && (msg.contains("SPKI pin") || msg.contains("Empty certificate chain"))) {
                return "tls_pin";
            }
            if (cur instanceof java.net.UnknownHostException) {
                return "no_internet";
            }
            if (cur instanceof java.net.ConnectException) {
                return "connect_error";
            }
            if (cur instanceof java.net.SocketTimeoutException) {
                return "timeout";
            }
            if (cur instanceof SecurityException) {
                return "no_permission";
            }
            if (cur instanceof CertificateException || cur instanceof javax.net.ssl.SSLException) {
                return "tls_error";
            }
            cur = cur.getCause();
        }
        return "network";
    }

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
        sLastError.remove();
        if (urlStr == null || urlStr.isEmpty()) {
            sLastError.set("network");
            return null;
        }

        HttpsURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                sLastError.set("network");
                return null;
            }

            SSLSocketFactory factory = getFactoryForHost(url.getHost());
            if (factory == null) {
                sLastError.set("tls_error");
                return null;
            }

            conn = (HttpsURLConnection) url.openConnection();
            conn.setSSLSocketFactory(factory);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "ModXLab/1.0");

            int code = conn.getResponseCode();
            if (code != 200) {
                sLastError.set("http_" + code);
                return null;
            }

            return readStream(conn.getInputStream());
        } catch (Exception e) {
            String reason = classifyException(e);
            sLastError.set(reason);
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diag(301, "HTTP GET " + reason + ": " + describeThrowable(e));
            }
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Exception ignored) { }
        }
    }

    /**
     * Performs a pinned HTTPS POST with a JSON body.
     */
    public static HttpResponse postJson(String urlStr, String jsonBody, Map<String, String> headers) {
        sLastError.remove();
        if (urlStr == null || urlStr.isEmpty()) {
            sLastError.set("network");
            return null;
        }

        HttpsURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                sLastError.set("network");
                return null;
            }

            SSLSocketFactory factory = getFactoryForHost(url.getHost());
            if (factory == null) {
                sLastError.set("tls_error");
                return null;
            }

            conn = (HttpsURLConnection) url.openConnection();
            conn.setSSLSocketFactory(factory);
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
            String reason = classifyException(e);
            sLastError.set(reason);
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diag(301, "HTTP POST " + reason + ": " + describeThrowable(e));
            }
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

    private static SSLSocketFactory getFactoryForHost(String host) {
        if (host == null) return null;
        String lower = host.toLowerCase();
        boolean isAppwrite = lower.endsWith(".appwrite.io") || lower.equals("appwrite.io");
        if (isAppwrite) {
            if (sAppwriteFactory != null) return sAppwriteFactory;
            synchronized (sLock) {
                if (sAppwriteFactory == null) {
                    sAppwriteFactory = buildFactory(APPWRITE_PINS);
                }
                return sAppwriteFactory;
            }
        }
        if (sFirebaseFactory != null) return sFirebaseFactory;
        synchronized (sLock) {
            if (sFirebaseFactory == null) {
                sFirebaseFactory = buildFactory(FIREBASE_PINS);
            }
            return sFirebaseFactory;
        }
    }

    private static String describeThrowable(Throwable t) {
        if (t == null) return "null";
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth++ < 4) {
            if (depth > 1) sb.append(" <- ");
            sb.append(cur.getClass().getSimpleName());
            if (cur.getMessage() != null) {
                sb.append('(').append(cur.getMessage()).append(')');
            }
            cur = cur.getCause();
        }
        return sb.toString();
    }

    private static SSLSocketFactory buildFactory(String[] pins) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            boolean initialized = false;
            try {
                KeyStore caStore = KeyStore.getInstance("AndroidCAStore");
                caStore.load(null, null);
                KeyStore systemOnly = KeyStore.getInstance(KeyStore.getDefaultType());
                systemOnly.load(null, null);
                int count = 0;
                java.util.Enumeration<String> aliases = caStore.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    if (alias != null && alias.startsWith("system:")) {
                        java.security.cert.Certificate cert = caStore.getCertificate(alias);
                        if (cert != null) {
                            systemOnly.setCertificateEntry(alias, cert);
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    tmf.init(systemOnly);
                    initialized = true;
                }
            } catch (Exception ignored) { }
            if (!initialized) {
                tmf.init((KeyStore) null);
            }

            X509TrustManager systemTm = null;
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    systemTm = (X509TrustManager) tm;
                    break;
                }
            }
            if (systemTm == null) {
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diag(301, "buildFactory: no X509TrustManager");
                }
                return null;
            }

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null,
                    new TrustManager[]{ new PinTrustManager(systemTm, pins) },
                    new SecureRandom());
            return ctx.getSocketFactory();
        } catch (Exception e) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diag(301, "buildFactory failed: " + describeThrowable(e));
            }
            return null;
        }
    }

    private static final class PinTrustManager implements X509TrustManager {
        private final X509TrustManager delegate;
        private final String[] pins;

        PinTrustManager(X509TrustManager delegate, String[] pins) {
            this.delegate = delegate;
            this.pins = pins;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
            if (!ENFORCE_PINS) return;
            if (chain == null || chain.length == 0 || pins == null || pins.length == 0) {
                throw new CertificateException("Empty certificate chain or pin set");
            }
            for (X509Certificate cert : chain) {
                String spki = spkiSha256Base64(cert);
                if (spki == null) continue;
                for (String pin : pins) {
                    if (isValidPin(pin) && pin.equals(spki)) {
                        return;
                    }
                }
            }
            throw new CertificateException("No matching endpoint SPKI pin in chain");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }

        private static boolean isValidPin(String pin) {
            return pin != null && !pin.isEmpty() && !pin.startsWith("__") && pin.length() >= 40;
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