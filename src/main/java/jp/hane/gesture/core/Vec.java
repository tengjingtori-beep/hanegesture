package jp.hane.gesture.core;

/** Simple immutable 3D vector used by the hand-pose math. */
public record Vec(double x, double y, double z) {
    public Vec sub(Vec o) { return new Vec(x - o.x, y - o.y, z - o.z); }
    public Vec add(Vec o) { return new Vec(x + o.x, y + o.y, z + o.z); }
    public Vec scale(double s) { return new Vec(x * s, y * s, z * s); }
    public double dot(Vec o) { return x * o.x + y * o.y + z * o.z; }
    public Vec cross(Vec o) {
        return new Vec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }
    public double len() { return Math.sqrt(dot(this)); }
    public Vec norm() {
        double l = len();
        return l < 1e-9 ? new Vec(0, 0, 0) : scale(1.0 / l);
    }
    /** Angle between this vector and o, in radians (0 = same direction). */
    public double angleTo(Vec o) {
        double d = norm().dot(o.norm());
        d = Math.max(-1.0, Math.min(1.0, d));
        return Math.acos(d);
    }
}
