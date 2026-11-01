package com.example.allaycrit;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Auto jump-crit on allays that hold an item (anything except an amethyst shard).
 * Toggle with R (rebindable). Only acts while the crosshair is on such an allay.
 */
public class AllayCritClient implements ClientModInitializer {

    private static KeyMapping toggleKey;
    private static boolean enabled = false;
    /** True while we are the ones holding the jump key down. */
    private static boolean jumpHeldByUs = false;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("allaycrit", "main"));

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.allaycrit.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(AllayCritClient::onTick);
    }

    private static void releaseJump(Minecraft mc) {
        if (jumpHeldByUs) {
            mc.options.keyJump.setDown(false);
            jumpHeldByUs = false;
        }
    }

    private static void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            jumpHeldByUs = false;
            return;
        }

        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            mc.gui.setOverlayMessage(
                    Component.literal("Allay Crit: " + (enabled ? "\u00a7aON" : "\u00a7cOFF")), false);
            if (!enabled) releaseJump(mc);
        }

        // Always let go of the jump key we pressed last tick (a real tap = one tick).
        releaseJump(mc);

        if (!enabled || mc.screen != null) return;

        // Must be looking at an allay
        if (!(mc.hitResult instanceof EntityHitResult hit) || hit.getType() != HitResult.Type.ENTITY) return;
        if (!(hit.getEntity() instanceof Allay allay) || !allay.isAlive()) return;

        // Must be holding an item that is NOT an amethyst shard
        ItemStack held = allay.getMainHandItem();
        if (held.isEmpty() || held.is(Items.AMETHYST_SHARD)) return;

        // Only act with a fully charged attack so the crit does full damage
        if (player.getAttackStrengthScale(0.5f) < 0.95f) return;

        if (player.onGround()) {
            // Jump phase: tap the real jump key
            mc.options.keyJump.setDown(true);
            jumpHeldByUs = true;
        } else if (canCrit(player)) {
            // Hit phase: falling = vanilla critical hit
            mc.gameMode.attack(player, allay);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    /** Mirrors vanilla's critical-hit conditions (minus sprinting, which cancels crits only in some cases). */
    private static boolean canCrit(LocalPlayer p) {
        return p.fallDistance > 0.0
                && !p.onGround()
                && !p.onClimbable()
                && !p.isInWater()
                && !p.hasEffect(MobEffects.BLINDNESS)
                && !p.isPassenger();
    }
}
