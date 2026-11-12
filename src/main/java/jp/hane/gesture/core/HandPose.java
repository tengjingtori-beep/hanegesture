package jp.hane.gesture.core;

import java.util.List;
import java.util.Map;

/**
 * A single frame of hand tracking: 21 landmarks (MediaPipe layout) converted
 * into per-finger joint flex angles plus summary values the game can use.
 * Landmark indices: 0 wrist; thumb 1-4; index 5-8; middle 9-12; ring 13-16; pinky 17-20.
 */
public final class HandPose {
    public static final int FINGERS = 5;
    public static final int JOINTS = 3; // base (MCP), middle (PIP), tip-side (DIP)

    public final Vec[] points;              // 21 landmarks, camera space (x right, y down, z toward viewer-ish)
    public final double[][] flex;           // flex[finger][joint] radians: 0 = straight, ~1.5 = fully curled
    public final double openness;           // 0 = fist, 1 = fully open
    public final double pinch;              // thumb-tip to index-tip distance / hand size (small = pinching)
    public final double handSize;
    public final Vec palmNormal;            // unit normal of the palm plane
    public final Vec middleDir;             // wrist -> middle-finger MCP, unit
    public final String gesture;            // label from the AI app (may be empty)
    public final double confidence;
    public final String handedness;         // "Left" / "Right" / ""
    public final long receivedAtMs;

    private static final int[][] FINGER_JOINTS = {
        {1, 2, 3, 4},    // thumb: CMC, MCP, IP, TIP
        {5, 6, 7, 8},    // index
        {9, 10, 11, 12}, // middle
        {13, 14, 15, 16},// ring
        {17, 18, 19, 20} // pinky
    };

    public HandPose(Vec[] points, String gesture, double confidence, String handedness, long receivedAtMs) {
        this.points = points;
        this.gesture = gesture == null ? "" : gesture;
        this.confidence = confidence;
        this.handedness = handedness == null ? "" : handedness;
        this.receivedAtMs = receivedAtMs;
        this.handSize = Math.max(1e-6, points[9].sub(points[0]).len());

        Vec palmAxis = points[9].sub(points[0]).norm();
        this.flex = new double[FINGERS][JOINTS];
        double flexSum = 0;
        for (int f = 0; f < FINGERS; f++) {
            int[] j = FINGER_JOINTS[f];
            // base flex: angle between palm axis and the first segment
            Vec seg0 = points[j[1]].sub(points[j[0]]);
            flex[f][0] = (f == 0)
                    ? points[j[1]].sub(points[0]).angleTo(seg0) // thumb base behaves differently; approximation
                    : palmAxis.angleTo(seg0);
            flex[f][1] = points[j[1]].sub(points[j[0]]).angleTo(points[j[2]].sub(points[j[1]]));
            flex[f][2] = points[j[2]].sub(points[j[1]]).angleTo(points[j[3]].sub(points[j[2]]));
            for (int k = 0; k < JOINTS; k++) flexSum += flex[f][k];
        }
        double avgFlex = flexSum / (FINGERS * JOINTS);
        this.openness = clamp01(1.0 - avgFlex / 1.35);
        this.pinch = points[4].sub(points[8]).len() / handSize;
        Vec a = points[5].sub(points[0]);
        Vec b = points[17].sub(points[0]);
        this.palmNormal = a.cross(b).norm();
        this.middleDir = palmAxis;
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    /** Parse one pose message (the JSON the AI app sends). Returns null for other/invalid messages. */
    public static HandPose fromJson(String json, long nowMs) {
        try {
            Map<String, Object> root = MiniJson.asObject(MiniJson.parse(json));
            String type = MiniJson.asString(root.get("type"));
            if ("nohand".equals(type)) return null;
            if (!"pose".equals(type)) return null;
            List<Object> lms = MiniJson.asArray(root.get("landmarks"));
            if (lms.size() != 21) return null;
            Vec[] pts = new Vec[21];
            for (int i = 0; i < 21; i++) {
                List<Object> p = MiniJson.asArray(lms.get(i));
                if (p.size() < 3) return null;
                pts[i] = new Vec(MiniJson.asDouble(p.get(0)), MiniJson.asDouble(p.get(1)), MiniJson.asDouble(p.get(2)));
            }
            return new HandPose(pts,
                    MiniJson.asString(root.get("gesture")),
                    MiniJson.asDouble(root.get("confidence")),
                    MiniJson.asString(root.get("handedness")),
                    nowMs);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isNoHandMarker(String json) {
        return json != null && json.contains("\"nohand\"");
    }
}
