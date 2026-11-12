package jp.hane.gesture.client;

import com.mojang.blaze3d.platform.InputConstants;
import jp.hane.gesture.core.HandPose;
import jp.hane.gesture.core.HandState;
import jp.hane.gesture.core.WsServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * HaneGesture client entry point (Minecraft 26.3 / Fabric).
 * Starts the local WebSocket server the AI app connects to, and every tick
 * drives the attack/use keys from the live hand pose (no canned animations).
 */
public final class HaneGestureClient implements ClientModInitializer {
    public static final int PORT = 8765;
    public static final HandState STATE = new HandState();
    public static volatile boolean enabled = true;

    private static KeyMapping toggleKey;
    private static boolean weForcedKeys = false;

    @Override
    public void onInitializeClient() {
        WsServer server = new WsServer(PORT, STATE::onMessage);
        server.start();
        System.out.println("[HaneGesture] WebSocket server started on port " + PORT
                + " - open the gesture AI app and press connect.");

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.hanegesture.toggle", InputConstants.KEY_F8, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (toggleKey.consumeClick()) {
                enabled = !enabled;
            }
            driveInputs(client);
        });
    }

    private static void driveInputs(Minecraft client) {
        long now = System.currentTimeMillis();
        HandPose pose = enabled ? STATE.freshPose(now) : null;
        if (pose == null) {
            if (weForcedKeys) {
                client.options.keyAttack.setDown(false);
                client.options.keyUse.setDown(false);
                weForcedKeys = false;
            }
            return;
        }
        boolean attack = STATE.controls().attack || STATE.controls().jabPulse;
        boolean use = STATE.controls().use;
        client.options.keyAttack.setDown(attack);
        client.options.keyUse.setDown(use);
        weForcedKeys = attack || use;
    }
}
