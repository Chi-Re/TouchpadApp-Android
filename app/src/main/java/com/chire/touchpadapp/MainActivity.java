package com.chire.touchpadapp;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleDetector;
    private float scaleFactor = 1.0f;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化手势检测器
        gestureDetector = new GestureDetector(this, new GestureListener());
        scaleDetector = new ScaleGestureDetector(this, new ScaleListener());

        View touchArea = findViewById(R.id.touch_area);
        touchArea.setOnTouchListener((v, event) -> {
            // 传递给手势检测器
            gestureDetector.onTouchEvent(event);
            scaleDetector.onTouchEvent(event);

            // 处理多点触控
            handleMultiTouch(event);

            return true;
        });
    }

    private void handleMultiTouch(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
                Log.d("TOUCH", "新手指按下，当前点数: " + pointerCount);
                break;

            case MotionEvent.ACTION_POINTER_UP:
                Log.d("TOUCH", "手指抬起，剩余点数: " + (pointerCount - 1));
                break;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            // 按下事件
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            // 单击
            Log.d("GESTURE", "单击: (" + e.getX() + ", " + e.getY() + ")");
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // 双击
            Log.d("GESTURE", "双击: (" + e.getX() + ", " + e.getY() + ")");
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            // 长按
            Log.d("GESTURE", "长按: (" + e.getX() + ", " + e.getY() + ")");
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            // 滚动（滑动）
            Log.d("GESTURE", "滑动: X方向距离=" + distanceX + ", Y方向距离=" + distanceY);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // 快速滑动（抛掷）
            Log.d("GESTURE", "快速滑动: X速度=" + velocityX + ", Y速度=" + velocityY);
            return true;
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
    }
}
