package jp.hane.gesture.core;

/**
 * Turns a continuous hand pose into game control states.
 * Not fixed animations: every value follows the real hand, every frame.
 */
public final class GestureControls {
    /** Closed fist (or near) = hold attack. */
    public boolean attack;
    /** Pinching thumb+index = hold use (right-click). */
    public boolean use;
    /** Quick forward jab = one attack pulse / swing trigger. */
    public boolean jabPulse;

    private double prevWristZ = Double.NaN;
    private long prevTimeMs = 0;
    private long lastJabMs = 0;

    public void update(HandPose pose, long nowMs) {
        if (pose == null) {
            attack = false; use = false; jabPulse = false;
            prevWristZ = Double.NaN;
            return;
        }
        attack = pose.openness < 0.38 || "グー".equals(pose.gesture);
        use = pose.pinch < 0.38 && pose.openness > 0.45;
        jabPulse = false;
        double z = pose.points[0].z();
        if (!Double.isNaN(prevWristZ) && prevTimeMs > 0) {
            double dt = Math.max(1, nowMs - prevTimeMs) / 1000.0;
            double vz = (z - prevWristZ) / dt; // MediaPipe z: smaller = closer to camera
            if (vz < -1.6 && nowMs - lastJabMs > 450) {
                jabPulse = true;
                lastJabMs = nowMs;
            }
        }
        prevWristZ = z;
        prevTimeMs = nowMs;
    }
}
