package ai.arena.nauru.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import ai.arena.nauru.game.GameState;
import ai.arena.nauru.input.InputState;
import ai.arena.nauru.world.NauruWorld;

/** Multi-touch mobile controls and diegetic island map. No bitmap UI assets are required. */
public final class GameHudView extends View {
    private static final int ROLE_CAMERA = 1;
    private static final int ROLE_JOYSTICK = 2;
    private static final int ROLE_PRIMARY = 3;
    private static final int ROLE_SECONDARY = 4;
    private static final int ROLE_ACTION = 5;
    private static final int ROLE_AUX = 6;
    private static final int ROLE_QUALITY = 7;

    private final GameState state;
    private final InputState input;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final SparseArray<Integer> roles = new SparseArray<>();
    private final SparseArray<PointF> previous = new SparseArray<>();
    private final float density;
    private final long startedAt = SystemClock.uptimeMillis();
    private float joystickX, joystickY, joystickRadius;
    private float primaryX, primaryY, secondaryX, secondaryY, actionX, actionY, auxX, auxY;
    private float lastPinchDistance;

    public GameHudView(Context context, GameState state, InputState input) {
        super(context);
        this.state = state;
        this.input = input;
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        setKeepScreenOn(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        joystickX = 112 * density;
        joystickY = h - 104 * density;
        joystickRadius = 68 * density;
        primaryX = w - 82 * density;
        primaryY = h - 78 * density;
        secondaryX = w - 204 * density;
        secondaryY = h - 67 * density;
        actionX = w - 72 * density;
        actionY = h - 207 * density;
        auxX = w - 188 * density;
        auxY = h - 175 * density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawTopBar(canvas);
        drawMiniMap(canvas);
        drawControls(canvas);
        drawNotification(canvas);
        drawOpeningTitle(canvas);
        postInvalidateOnAnimation();
    }

    private void drawTopBar(Canvas canvas) {
        float w = getWidth();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(145, 4, 20, 26));
        canvas.drawRoundRect(w * .5f - 190 * density, 13 * density,
                w * .5f + 190 * density, 65 * density, 18 * density, 18 * density, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(15 * density);
        paint.setColor(Color.rgb(235, 245, 237));
        canvas.drawText(state.locationName().toUpperCase(), w * .5f, 35 * density, paint);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        paint.setTextSize(11 * density);
        paint.setColor(Color.rgb(102, 226, 201));
        canvas.drawText("NAURU  •  " + state.clockText() + "  •  " + compassText(state.cameraYaw),
                w * .5f, 54 * density, paint);

        // Graphics/streaming preset is intentionally available in-game for low-end devices.
        float right = w - 12 * density;
        paint.setColor(Color.argb(150, 5, 24, 30));
        canvas.drawRoundRect(right - 121 * density, 14 * density, right, 54 * density,
                12 * density, 12 * density, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(10 * density);
        paint.setColor(Color.rgb(221, 235, 229));
        canvas.drawText(state.qualityName().toUpperCase() + "  •  " + Math.round(state.fps) + " FPS",
                right - 60 * density, 38 * density, paint);

        if (state.driving) {
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
            paint.setTextSize(32 * density);
            paint.setColor(Color.WHITE);
            canvas.drawText(String.valueOf(Math.round(state.vehicle.speedKph())), right, 92 * density, paint);
            paint.setTextSize(10 * density);
            paint.setColor(Color.rgb(103, 226, 201));
            canvas.drawText("KM/H  •  REEF RUNNER", right, 108 * density, paint);
        }
    }

    private void drawMiniMap(Canvas canvas) {
        float cx = 82 * density, cy = 76 * density, radius = 62 * density;
        int save = canvas.save();
        path.reset(); path.addCircle(cx, cy, radius, Path.Direction.CW);
        canvas.clipPath(path);
        paint.setColor(Color.argb(210, 8, 57, 72));
        canvas.drawCircle(cx, cy, radius, paint);

        float mapScale = radius / 3420f;
        path.reset();
        float[] p = new float[2];
        for (int i = 0; i <= 96; i++) {
            float theta = (float) (i * Math.PI * 2 / 96.0);
            NauruWorld.coastPoint(theta, 1f, p);
            float px = cx + p[0] * mapScale, py = cy - p[1] * mapScale;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        paint.setColor(Color.rgb(82, 125, 73));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);

        path.reset();
        for (int i = 0; i <= 96; i++) {
            float theta = (float) (i * Math.PI * 2 / 96.0);
            NauruWorld.coastPoint(theta, .875f, p);
            float px = cx + p[0] * mapScale, py = cy - p[1] * mapScale;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f * density);
        paint.setColor(Color.rgb(221, 211, 176));
        canvas.drawPath(path, paint);

        float[] a = new float[2], b = new float[2];
        NauruWorld.localToWorld(-1080, -2440, a);
        NauruWorld.localToWorld(1080, -2440, b);
        paint.setStrokeWidth(3f * density);
        paint.setColor(Color.rgb(225, 231, 224));
        canvas.drawLine(cx + a[0] * mapScale, cy - a[1] * mapScale,
                cx + b[0] * mapScale, cy - b[1] * mapScale, paint);
        paint.setStyle(Paint.Style.FILL);

        drawMapMarker(canvas, cx + state.vehicle.x * mapScale, cy - state.vehicle.z * mapScale,
                state.vehicle.yaw, Color.rgb(255, 171, 61), 4.5f * density);
        drawMapMarker(canvas, cx + state.focusX() * mapScale, cy - state.focusZ() * mapScale,
                state.driving ? state.vehicle.yaw : state.player.yaw, Color.WHITE, 5.2f * density);
        canvas.restoreToCount(save);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(Color.argb(210, 196, 225, 217));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(9 * density);
        paint.setColor(Color.WHITE);
        canvas.drawText("N", cx, cy - radius + 11 * density, paint);
    }

    private void drawMapMarker(Canvas canvas, float x, float y, float yaw, int color, float size) {
        float sx = (float) Math.sin(yaw), sy = -(float) Math.cos(yaw);
        float rx = -sy, ry = sx;
        path.reset();
        path.moveTo(x + sx * size * 1.4f, y + sy * size * 1.4f);
        path.lineTo(x - sx * size + rx * size * .75f, y - sy * size + ry * size * .75f);
        path.lineTo(x - sx * size - rx * size * .75f, y - sy * size - ry * size * .75f);
        path.close();
        paint.setColor(color); paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
    }

    private void drawControls(Canvas canvas) {
        float mx = input.moveX, my = input.moveY;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(72, 220, 245, 238));
        canvas.drawCircle(joystickX, joystickY, joystickRadius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(Color.argb(120, 205, 239, 230));
        canvas.drawCircle(joystickX, joystickY, joystickRadius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(185, 86, 226, 195));
        canvas.drawCircle(joystickX + mx * joystickRadius * .62f,
                joystickY - my * joystickRadius * .62f, 27 * density, paint);

        if (state.driving) {
            drawButton(canvas, primaryX, primaryY, 49 * density, "GO", input.throttle > 0, Color.rgb(63, 215, 161));
            drawButton(canvas, secondaryX, secondaryY, 42 * density, "BRAKE", input.brake > 0, Color.rgb(240, 120, 78));
            drawButton(canvas, auxX, auxY, 35 * density, "DRIFT", input.handbrake, Color.rgb(244, 180, 73));
            drawButton(canvas, actionX, actionY, 37 * density, "EXIT", false, Color.rgb(72, 151, 174));
        } else {
            drawButton(canvas, primaryX, primaryY, 46 * density, "JUMP", false, Color.rgb(63, 215, 161));
            drawButton(canvas, secondaryX, secondaryY, 39 * density, "RUN", input.sprint, Color.rgb(244, 180, 73));
            if (state.isNearVehicle()) {
                drawButton(canvas, actionX, actionY, 39 * density, "ENTER", false, Color.rgb(255, 174, 61));
            } else {
                drawButton(canvas, actionX, actionY, 35 * density, "CAR", false, Color.rgb(72, 151, 174));
            }
        }

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(9 * density);
        paint.setColor(Color.argb(150, 235, 245, 241));
        canvas.drawText("SWIPE TO LOOK  •  PINCH TO ZOOM", 20 * density, getHeight() - 12 * density, paint);
    }

    private void drawButton(Canvas canvas, float x, float y, float radius, String label,
                            boolean active, int accent) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? withAlpha(accent, 220) : Color.argb(105, 5, 24, 30));
        canvas.drawCircle(x, y, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(withAlpha(accent, active ? 255 : 190));
        canvas.drawCircle(x, y, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize((label.length() > 4 ? 9 : 11) * density);
        paint.setColor(Color.WHITE);
        canvas.drawText(label, x, y + 4 * density, paint);
    }

    private void drawNotification(Canvas canvas) {
        if (state.notificationSeconds <= 0) return;
        float cx = getWidth() * .5f, y = getHeight() - 48 * density;
        paint.setTextSize(11 * density);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        float textWidth = paint.measureText(state.notification);
        paint.setColor(Color.argb(175, 4, 20, 26));
        canvas.drawRoundRect(cx - textWidth / 2 - 20 * density, y - 20 * density,
                cx + textWidth / 2 + 20 * density, y + 11 * density, 12 * density, 12 * density, paint);
        paint.setColor(Color.rgb(238, 244, 231));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(state.notification, cx, y, paint);
    }

    private void drawOpeningTitle(Canvas canvas) {
        float seconds = (SystemClock.uptimeMillis() - startedAt) / 1000f;
        if (seconds > 4.8f) return;
        float alpha = seconds < .7f ? seconds / .7f : seconds > 3.8f ? (4.8f - seconds) : 1f;
        alpha = Math.max(0, Math.min(1, alpha));
        float cx = getWidth() * .5f, cy = getHeight() * .42f;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(31 * density);
        paint.setColor(Color.argb((int) (245 * alpha), 243, 244, 228));
        canvas.drawText("NAURU", cx, cy, paint);
        paint.setTextSize(12 * density);
        paint.setLetterSpacing(.18f);
        paint.setColor(Color.argb((int) (230 * alpha), 85, 226, 195));
        canvas.drawText("THE COUNTRYSIDE", cx, cy + 24 * density, paint);
        paint.setLetterSpacing(0);
        paint.setTextSize(9 * density);
        paint.setColor(Color.argb((int) (190 * alpha), 235, 240, 230));
        canvas.drawText("PLAYABLE GEOGRAPHIC PROTOTYPE  •  1 WORLD UNIT = 1 METRE", cx, cy + 46 * density, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int id = event.getPointerId(actionIndex);
            beginPointer(id, event.getX(actionIndex), event.getY(actionIndex));
        } else if (action == MotionEvent.ACTION_MOVE) {
            updatePointers(event);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) {
            int id = event.getPointerId(actionIndex);
            endPointer(id);
            if (action == MotionEvent.ACTION_CANCEL) clearAllPointers();
        }
        return true;
    }

    private void beginPointer(int id, float x, float y) {
        int role;
        if (x > getWidth() - 145 * density && y < 68 * density) {
            role = ROLE_QUALITY; input.queueQualityChange();
        } else if (distance(x, y, actionX, actionY) < 55 * density) {
            role = ROLE_ACTION; input.queueAction();
        } else if (distance(x, y, primaryX, primaryY) < 62 * density) {
            role = ROLE_PRIMARY;
            if (state.driving) input.throttle = 1; else input.queueJump();
        } else if (distance(x, y, secondaryX, secondaryY) < 57 * density) {
            role = ROLE_SECONDARY;
            if (state.driving) input.brake = 1; else input.sprint = true;
        } else if (state.driving && distance(x, y, auxX, auxY) < 50 * density) {
            role = ROLE_AUX; input.handbrake = true;
        } else if (x < getWidth() * .42f && y > getHeight() * .38f) {
            role = ROLE_JOYSTICK; updateJoystick(x, y);
        } else {
            role = ROLE_CAMERA;
        }
        roles.put(id, role);
        previous.put(id, new PointF(x, y));
        lastPinchDistance = 0;
    }

    private void updatePointers(MotionEvent event) {
        int cameraCount = 0;
        PointF cameraA = null, cameraB = null;
        for (int i = 0; i < event.getPointerCount(); i++) {
            int id = event.getPointerId(i);
            Integer roleValue = roles.get(id);
            if (roleValue == null) continue;
            int role = roleValue;
            float x = event.getX(i), y = event.getY(i);
            PointF old = previous.get(id);
            if (role == ROLE_JOYSTICK) updateJoystick(x, y);
            if (role == ROLE_CAMERA) {
                cameraCount++;
                if (cameraA == null) cameraA = new PointF(x, y); else if (cameraB == null) cameraB = new PointF(x, y);
                if (cameraCount == 1 && old != null) input.addCameraDelta(x - old.x, y - old.y);
            }
            if (old != null) old.set(x, y);
        }
        if (cameraA != null && cameraB != null) {
            float pinch = distance(cameraA.x, cameraA.y, cameraB.x, cameraB.y);
            if (lastPinchDistance > 0) input.addZoom(-(pinch - lastPinchDistance));
            lastPinchDistance = pinch;
        } else lastPinchDistance = 0;
    }

    private void updateJoystick(float x, float y) {
        float dx = x - joystickX, dy = y - joystickY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length > joystickRadius) { dx *= joystickRadius / length; dy *= joystickRadius / length; }
        input.moveX = dx / joystickRadius;
        input.moveY = -dy / joystickRadius;
    }

    private void endPointer(int id) {
        Integer roleValue = roles.get(id);
        if (roleValue != null) {
            switch (roleValue) {
                case ROLE_JOYSTICK: input.moveX = input.moveY = 0; break;
                case ROLE_PRIMARY: input.throttle = 0; break;
                case ROLE_SECONDARY: input.brake = 0; input.sprint = false; break;
                case ROLE_AUX: input.handbrake = false; break;
            }
        }
        roles.remove(id); previous.remove(id); lastPinchDistance = 0;
    }

    private void clearAllPointers() {
        roles.clear(); previous.clear(); input.clearContinuous(); lastPinchDistance = 0;
    }

    private static float distance(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x1 - x0, y1 - y0);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static String compassText(float yaw) {
        float degrees = yaw * 180f / (float) Math.PI;
        degrees %= 360; if (degrees < 0) degrees += 360;
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[Math.round(degrees / 45f) & 7];
    }
}
