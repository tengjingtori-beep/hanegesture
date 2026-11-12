package jp.hane.gesture.core;

/** Thread-safe holder for the latest pose received from the AI app. */
public final class HandState {
    private volatile HandPose latest;
    private volatile long lastSeenMs = 0;
    private volatile boolean handPresent = false;
    private final GestureControls controls = new GestureControls();

    public void onMessage(String json) {
        long now = System.currentTimeMillis();
        if (json != null && json.contains("\"nohand\"")) {
            handPresent = false;
            controls.update(null, now);
            return;
        }
        HandPose pose = HandPose.fromJson(json, now);
        if (pose != null) {
            latest = pose;
            lastSeenMs = now;
            handPresent = true;
            controls.update(pose, now);
        }
    }

    /** The latest pose if it is fresh (default window 300 ms). */
    public HandPose freshPose(long nowMs) {
        if (!handPresent) return null;
        if (nowMs - lastSeenMs > 300) return null;
        return latest;
    }

    public boolean isHandPresent() { return handPresent; }
    public GestureControls controls() { return controls; }
}
