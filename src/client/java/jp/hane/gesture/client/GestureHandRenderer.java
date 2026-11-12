package jp.hane.gesture.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.hane.gesture.core.HandPose;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Draws an articulated hand model (palm + 5 fingers x 3 segments) whose every
 * joint follows the tracked real hand. Hand space: origin at the wrist,
 * +Y toward the fingers, +X from index side to pinky side, +Z = back of hand.
 * Curling rotates each joint around X toward the palm (-Z).
 * Emitted through the 26.x SubmitNode custom-geometry pipeline.
 */
public final class GestureHandRenderer {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("hanegesture", "textures/hand.png");
    private static final int FULL_BRIGHT = 15728880; // 0xF000F0

    public static RenderType renderType() {
        return RenderTypes.entitySolid(TEXTURE);
    }

    // finger definitions: base x, base y, width, segment lengths (3)
    private static final double[][] FINGERS = {
        {-0.078, 0.055, 0.034, 0.055, 0.045, 0.040}, // thumb
        {-0.056, 0.180, 0.030, 0.072, 0.048, 0.038}, // index
        {-0.019, 0.196, 0.031, 0.082, 0.054, 0.040}, // middle
        {0.019, 0.186, 0.030, 0.076, 0.050, 0.038},  // ring
        {0.054, 0.162, 0.026, 0.058, 0.040, 0.032}   // pinky
    };

    /** Tiny matrix stack on top of JOML (the submit callback gets a fixed pose). */
    private static final class Mat {
        final Matrix4f p;
        final Matrix3f n;
        Mat(Matrix4f p, Matrix3f n) { this.p = p; this.n = n; }
        Mat copy() { return new Mat(new Matrix4f(p), new Matrix3f(n)); }
        void translate(double x, double y, double z) { p.translate((float) x, (float) y, (float) z); }
        void rotate(Quaternionf q) {
            p.rotate(q);
            n.mul(new Matrix3f().set(q));
        }
        void rotateX(double rad) { rotate(new Quaternionf().rotateX((float) rad)); }
    }

    public static void emit(PoseStack.Pose pose, VertexConsumer vc, HandPose hand, boolean leftHand) {
        Deque<Mat> stack = new ArrayDeque<>();
        Mat root = new Mat(new Matrix4f(pose.pose()), new Matrix3f(pose.normal()));
        stack.push(root);

        float side = leftHand ? -1f : 1f;
        root.translate(0.60 * side, -0.58, -0.92);
        root.rotate(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(-96), (float) Math.toRadians(8 * side), (float) Math.toRadians(-6 * side)));

        // Wrist orientation from the tracked hand.
        double roll = clamp(Math.atan2(hand.middleDir.x(), -hand.middleDir.y()), -1.0, 1.0);
        double pitch = clamp(hand.middleDir.z() * 1.1, -0.8, 0.8);
        root.rotate(new Quaternionf().rotationXYZ((float) pitch, 0f, (float) -roll));

        float mirror = leftHand ? -1f : 1f;

        // Palm and wrist stub.
        box(stack.peek(), vc, -0.085, 0.0, -0.024, 0.085, 0.20, 0.024, 0.99f, 0.80f, 0.62f);
        box(stack.peek(), vc, -0.070, -0.30, -0.026, 0.070, 0.005, 0.026, 0.97f, 0.78f, 0.60f);

        for (int f = 0; f < 5; f++) {
            double[] def = FINGERS[f];
            Mat m = root.copy();
            m.translate(def[0] * mirror, def[1], 0);
            if (f == 0) {
                // Thumb sticks out to the side, tilted toward the viewer.
                m.rotate(new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(-18), 0f, (float) Math.toRadians(52) * mirror));
            }
            double w = def[2];
            double tint = 1.0;
            for (int j = 0; j < 3; j++) {
                double len = def[3 + j];
                m.rotateX(-hand.flex[f][j]);
                float r = (float) (0.99 * tint), g = (float) (0.80 * tint), b = (float) (0.62 * tint);
                box(m, vc, -w / 2, 0, -w * 0.42, w / 2, len, w * 0.42, r, g, b);
                m.translate(0, len, 0);
                w *= 0.88;
                tint *= 0.97;
            }
        }
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private static void box(Mat m, VertexConsumer vc,
                            double x0, double y0, double z0, double x1, double y1, double z1,
                            float r, float g, float b) {
        float[][][] faces = {
            {{0, 0, 1}, {(float) x0, (float) y0, (float) z1}, {(float) x1, (float) y0, (float) z1}, {(float) x1, (float) y1, (float) z1}, {(float) x0, (float) y1, (float) z1}},
            {{0, 0, -1}, {(float) x1, (float) y0, (float) z0}, {(float) x0, (float) y0, (float) z0}, {(float) x0, (float) y1, (float) z0}, {(float) x1, (float) y1, (float) z0}},
            {{0, 1, 0}, {(float) x0, (float) y1, (float) z0}, {(float) x0, (float) y1, (float) z1}, {(float) x1, (float) y1, (float) z1}, {(float) x1, (float) y1, (float) z0}},
            {{0, -1, 0}, {(float) x0, (float) y0, (float) z1}, {(float) x0, (float) y0, (float) z0}, {(float) x1, (float) y0, (float) z0}, {(float) x1, (float) y0, (float) z1}},
            {{1, 0, 0}, {(float) x1, (float) y0, (float) z1}, {(float) x1, (float) y0, (float) z0}, {(float) x1, (float) y1, (float) z0}, {(float) x1, (float) y1, (float) z1}},
            {{-1, 0, 0}, {(float) x0, (float) y0, (float) z0}, {(float) x0, (float) y0, (float) z1}, {(float) x0, (float) y1, (float) z1}, {(float) x0, (float) y1, (float) z0}}
        };
        for (float[][] face : faces) {
            Vector3f n = new Vector3f(face[0][0], face[0][1], face[0][2]);
            m.n.transform(n);
            n.normalize();
            float[][] corners = {face[1], face[2], face[3], face[4]};
            float[][] uvs = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
            for (int k = 0; k < 4; k++) {
                Vector3f v = new Vector3f(corners[k][0], corners[k][1], corners[k][2]);
                m.p.transformPosition(v);
                vc.addVertex(v.x, v.y, v.z)
                  .setColor(r, g, b, 1.0f)
                  .setUv(uvs[k][0], uvs[k][1])
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(FULL_BRIGHT)
                  .setNormal(n.x, n.y, n.z);
            }
        }
    }
}
