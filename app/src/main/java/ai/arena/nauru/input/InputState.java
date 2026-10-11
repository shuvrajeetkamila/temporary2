package ai.arena.nauru.input;

/** Thread-safe bridge from Android's UI thread to the OpenGL simulation thread. */
public final class InputState {
    public volatile float moveX;
    public volatile float moveY;
    public volatile float throttle;
    public volatile float brake;
    public volatile boolean sprint;
    public volatile boolean handbrake;

    private boolean jumpQueued;
    private boolean actionQueued;
    private boolean qualityQueued;
    private float cameraDx;
    private float cameraDy;
    private float zoom;

    public synchronized void queueJump() { jumpQueued = true; }
    public synchronized void queueAction() { actionQueued = true; }
    public synchronized void queueQualityChange() { qualityQueued = true; }

    public synchronized boolean consumeJump() {
        boolean result = jumpQueued; jumpQueued = false; return result;
    }

    public synchronized boolean consumeAction() {
        boolean result = actionQueued; actionQueued = false; return result;
    }

    public synchronized boolean consumeQualityChange() {
        boolean result = qualityQueued; qualityQueued = false; return result;
    }

    public synchronized void addCameraDelta(float dx, float dy) {
        cameraDx += dx; cameraDy += dy;
    }

    public synchronized float[] consumeCameraDelta() {
        float[] result = {cameraDx, cameraDy, zoom};
        cameraDx = 0; cameraDy = 0; zoom = 0;
        return result;
    }

    public synchronized void addZoom(float amount) { zoom += amount; }

    public void clearContinuous() {
        moveX = moveY = throttle = brake = 0;
        sprint = handbrake = false;
    }
}
