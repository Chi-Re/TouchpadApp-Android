package com.chire.touchpadapp;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements WebSocketManager.MessageListener {

    private WebSocketManager webSocketManager;
    private TextView connectionStatus, messagesView;
    private EditText serverUrlInput;
    private Button connectButton, clearButton, sendButton;

    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleDetector;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化WebSocket管理器
        webSocketManager = new WebSocketManager();
        webSocketManager.setMessageListener(this);

        gestureDetector = new GestureDetector(this, new GestureListener());
        scaleDetector = new ScaleGestureDetector(this, new ScaleListener());

        // 初始化UI组件
        //drawingView = findViewById(R.id.drawing_view);

        View touchArea = findViewById(R.id.touch_area);
        touchArea.setOnTouchListener((v, event) -> {
            // 传递给手势检测器
            gestureDetector.onTouchEvent(event);
            scaleDetector.onTouchEvent(event);

            return true;
        });

        connectionStatus = findViewById(R.id.connection_status);
        messagesView = findViewById(R.id.messages_view);
        serverUrlInput = findViewById(R.id.server_url);
        connectButton = findViewById(R.id.connect_button);
        clearButton = findViewById(R.id.clear_button);
        sendButton = findViewById(R.id.send_button);

        // 设置初始状态
        updateConnectionStatus(false);
        sendButton.setEnabled(false);

        // 连接按钮点击事件
        connectButton.setOnClickListener(v -> {
            if (webSocketManager.isConnected()) {
                webSocketManager.closeConnection();
                updateConnectionStatus(false);
                addMessage("已断开服务器连接");
            } else {
                String serverUrl = serverUrlInput.getText().toString().trim();
                if (serverUrl.isEmpty()) {
                    Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 添加协议前缀检查
                if (!serverUrl.startsWith("ws://") && !serverUrl.startsWith("wss://")) {
                    Toast.makeText(this, "URL必须以ws://或wss://开头", Toast.LENGTH_SHORT).show();
                    return;
                }

                webSocketManager.setServerUrl(serverUrl);
                webSocketManager.connect();
                addMessage("正在连接服务器: " + serverUrl);
            }
        });
    }

    // 手势监听器
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true; // 必须返回true才能接收后续事件
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            Log.d("GESTURE", "单击: (" + e.getX() + ", " + e.getY() + ")");
            webSocketManager.sendMessage("单指单击 "+e.getX()+" "+e.getY());
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            Log.d("GESTURE", "双击: (" + e.getX() + ", " + e.getY() + ")");
            webSocketManager.sendMessage("单指双击 "+e.getX()+" "+e.getY());
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            Log.d("GESTURE", "滑动: X=" + distanceX + ", Y=" + distanceY);
            webSocketManager.sendMessage("单指滑动 "+distanceX+" "+distanceX);
            return true;
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            Log.d("SCALE", "缩放比例: " + scaleFactor);
            return true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webSocketManager.closeConnection();
    }

    @Override
    public void onMessageReceived(String message) {
        runOnUiThread(() -> {
            addMessage("收到: " + message);

            // 如果是绘图数据回显，尝试解析
            try {
                JSONObject json = new JSONObject(message);
                if (json.has("type") && "drawing_data".equals(json.getString("type"))) {
                    JSONArray points = json.getJSONArray("points");
                    addMessage("收到绘图数据: " + points.length()/2 + "个点");
                }
            } catch (JSONException e) {
                // 不是JSON或解析失败，不做处理
            }
        });
    }

    @Override
    public void onConnectionStatusChanged(boolean isConnected) {
        runOnUiThread(() -> {
            updateConnectionStatus(isConnected);
            sendButton.setEnabled(isConnected);

            if (isConnected) {
                addMessage("服务器连接成功");
            } else {
                addMessage("服务器连接断开");
            }
        });
    }

    private void updateConnectionStatus(boolean isConnected) {
        if (isConnected) {
            connectionStatus.setText("已连接");
            connectionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            connectButton.setText("断开连接");
            connectButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.colorAccent));
        } else {
            connectionStatus.setText("未连接");
            connectionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            connectButton.setText("连接服务器");
            connectButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.colorPrimary));
        }
    }

    private void addMessage(String message) {
        String time = String.format(Locale.getDefault(), "[%tT] ", System.currentTimeMillis());
        String currentText = messagesView.getText().toString();

        if (!currentText.isEmpty()) {
            currentText += "\n";
        }

        messagesView.setText(currentText + time + message);

        // 自动滚动到底部
        messagesView.post(() -> {
            int scrollAmount = messagesView.getLayout().getLineTop(messagesView.getLineCount())
                    - messagesView.getHeight();
            if (scrollAmount > 0) {
                messagesView.scrollTo(0, scrollAmount);
            } else {
                messagesView.scrollTo(0, 0);
            }
        });
    }


    @Override
    public void onConnectionError(String error) {
        runOnUiThread(() -> {
            addMessage("连接错误: " + error);
            Toast.makeText(this, "连接错误: " + error, Toast.LENGTH_LONG).show();
            updateConnectionStatus(false);
        });
    }
}
