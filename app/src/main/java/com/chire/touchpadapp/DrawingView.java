package com.chire.touchpadapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DrawingView extends View {

    private Paint paint;
    private Path currentPath;
    private List<Path> paths = new ArrayList<>();
    private List<Integer> colors = new ArrayList<>();
    private int currentColor = Color.BLUE;

    private List<Float> touchPoints = new ArrayList<>();

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupPaint();
    }

    private void setupPaint() {
        paint = new Paint();
        paint.setColor(currentColor);
        paint.setAntiAlias(true);
        paint.setStrokeWidth(8f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制所有路径
        for (int i = 0; i < paths.size(); i++) {
            paint.setColor(colors.get(i));
            canvas.drawPath(paths.get(i), paint);
        }

        // 绘制当前路径
        if (currentPath != null) {
            paint.setColor(currentColor);
            canvas.drawPath(currentPath, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        // 记录触摸点（用于发送）
        touchPoints.add(x);
        touchPoints.add(y);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startNewPath(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                updateCurrentPath(x, y);
                break;
            case MotionEvent.ACTION_UP:
                finalizeCurrentPath();
                break;
            default:
                return false;
        }

        // 重绘视图
        invalidate();
        return true;
    }

    private void startNewPath(float x, float y) {
        currentPath = new Path();
        currentPath.moveTo(x, y);
    }

    private void updateCurrentPath(float x, float y) {
        if (currentPath != null) {
            currentPath.lineTo(x, y);
        }
    }

    private void finalizeCurrentPath() {
        if (currentPath != null) {
            paths.add(currentPath);
            colors.add(currentColor);
            currentPath = null;
        }
    }

    public void clearDrawing() {
        paths.clear();
        colors.clear();
        currentPath = null;
        touchPoints.clear();
        invalidate();
    }

    public List<Float> getTouchPoints() {
        return new ArrayList<>(touchPoints);
    }
}