package com.chire.touchpadapp;

import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class WebSocketManager {
    private static final String TAG = "WebSocketManager";
    private WebSocket webSocket;
    private WebSocketListener listener;
    private String serverUrl = "wss://echo.websocket.org";
    private OkHttpClient client;
    private boolean isReconnecting = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 3000;

    private Thread pingThread;
    private volatile boolean keepPinging = false;

    public interface MessageListener {
        void onMessageReceived(String message);
        void onConnectionStatusChanged(boolean isConnected);
        void onConnectionError(String errorMessage); // 新增错误回调
    }

    private MessageListener messageListener;

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    public void setServerUrl(String url) {
        this.serverUrl = url;
    }

    // 创建信任所有证书的客户端（仅用于调试）
    private OkHttpClient createUnsafeOkHttpClient() {
        try {
            // 创建信任所有证书的TrustManager
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            // 安装信任所有证书的SSLContext
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true) // 信任所有主机名
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void connect() {
        if (webSocket != null) {
            closeConnection();
        }

        try {
            // 创建安全的HTTP客户端
            if (serverUrl.startsWith("wss://")) {
                // 生产环境应使用正确的证书验证
                // 这里为调试目的使用信任所有证书的客户端
                client = createUnsafeOkHttpClient();
            } else {
                client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .build();
            }

            Request request = new Request.Builder()
                    .url(serverUrl)
                    .addHeader("Sec-WebSocket-Protocol", "chat") // 添加协议头
                    .build();

            listener = new WebSocketListener() {
                @Override
                public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                    super.onOpen(webSocket, response);
                    Log.d(TAG, "WebSocket connected");
                    reconnectAttempts = 0;
                    isReconnecting = false;

                    if (messageListener != null) {
                        messageListener.onConnectionStatusChanged(true);
                    }

                    // 启动心跳线程
                    startPingThread();
                }

                @Override
                public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                    super.onMessage(webSocket, text);
                    Log.d(TAG, "Received message: " + text);
                    if (messageListener != null) {
                        messageListener.onMessageReceived(text);
                    }
                }

                @Override
                public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                    super.onClosing(webSocket, code, reason);
                    Log.d(TAG, "Closing: " + code + " / " + reason);
                    if (messageListener != null) {
                        messageListener.onConnectionStatusChanged(false);
                    }

                    // 尝试重新连接
                    if (code != 1000 && !isReconnecting) { // 1000是正常关闭
                        scheduleReconnect();
                    }
                }

                @Override
                public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                    super.onFailure(webSocket, t, response);
                    String errorMsg = "WebSocket error: " + t.getMessage();
                    Log.e(TAG, errorMsg);

                    if (messageListener != null) {
                        messageListener.onConnectionStatusChanged(false);
                        messageListener.onConnectionError(errorMsg);
                    }

                    // 尝试重新连接
                    if (!isReconnecting) {
                        scheduleReconnect();
                    }
                }
            };

            webSocket = client.newWebSocket(request, listener);
        } catch (Exception e) {
            String errorMsg = "Connection failed: " + e.getMessage();
            Log.e(TAG, errorMsg);
            if (messageListener != null) {
                messageListener.onConnectionError(errorMsg);
            }
        }
    }

    private void startPingThread() {
        keepPinging = true;
        pingThread = new Thread(() -> {
            while (keepPinging && webSocket != null) {
                try {
                    Thread.sleep(15000); // 每15秒发送一次心跳
                    if (webSocket != null) {
                        webSocket.send("ping"); // 发送心跳消息
                        Log.d(TAG, "Sent ping");
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Ping thread interrupted");
                } catch (Exception e) {
                    Log.e(TAG, "Ping failed: " + e.getMessage());
                }
            }
        });
        pingThread.start();
    }

    private void stopPingThread() {
        keepPinging = false;
        if (pingThread != null) {
            pingThread.interrupt();
            pingThread = null;
        }
    }

    private void scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached");
            return;
        }

        isReconnecting = true;
        reconnectAttempts++;

        // 延迟后重新连接
        new Thread(() -> {
            try {
                Log.d(TAG, "Reconnecting in " + RECONNECT_DELAY_MS + "ms (attempt " + reconnectAttempts + ")");
                Thread.sleep(RECONNECT_DELAY_MS);
                connect();
            } catch (InterruptedException e) {
                Log.e(TAG, "Reconnect interrupted");
            }
        }).start();
    }

    public void sendMessage(String message) {
        if (webSocket != null) {
            try {
                webSocket.send(message);
                Log.d(TAG, "Sent message: " + message);
            } catch (Exception e) {
                Log.e(TAG, "Send message failed: " + e.getMessage());
                if (messageListener != null) {
                    messageListener.onConnectionError("Send failed: " + e.getMessage());
                }
            }
        } else {
            Log.e(TAG, "WebSocket is not connected");
            if (messageListener != null) {
                messageListener.onConnectionError("Not connected to server");
            }
        }
    }

    public void closeConnection() {
        if (webSocket != null) {
            try {
                webSocket.close(1000, "Normal closure");
            } catch (Exception e) {
                Log.e(TAG, "Close connection error: " + e.getMessage());
            }
            webSocket = null;
        }
        stopPingThread();
    }

    public boolean isConnected() {
        return webSocket != null;
    }
}