package pl.holovis;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Test Hurt Cam: przed Tobą pojawia się duch-gracz, macha ręką i "uderza" Cię raz.
 * Obrażenia są tylko wizualne (animacja + dźwięk), nic nie idzie na serwer.
 */
public final class HurtCamTest {
    private HurtCamTest() {}

    private static OtherClientPlayerEntity ghost;
    private static int ticks = -1;

    public static boolean isGhost(Entity e) { return e != null && ghost != null && e == ghost; }

    public static void start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (w == null || p == null) return;
        stop(mc);
        mc.setScreen(null);

        Vec3d look = p.getRotationVec(1f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        Vec3d pos = p.getPos().add(flat.multiply(2.2));
        float yaw = p.getYaw() + 180f;

        OtherClientPlayerEntity g = new OtherClientPlayerEntity(w, new GameProfile(UUID.randomUUID(), "Ghost"));
        g.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
        g.setHeadYaw(yaw);
        g.setBodyYaw(yaw);
        g.setId(2_000_100_000 + (int) (System.nanoTime() % 1000));
        w.addEntity(g);
        ghost = g;
        ticks = 0;
    }

    private static void stop(MinecraftClient mc) {
        if (ghost != null && mc.world != null) {
            try { mc.world.removeEntity(ghost.getId(), Entity.RemovalReason.DISCARDED); } catch (Exception ignored) {}
        }
        ghost = null;
        ticks = -1;
    }

    public static void tick(MinecraftClient mc) {
        if (ticks < 0) return;
        if (ghost == null || mc.world == null || mc.player == null || ghost.getWorld() != mc.world) { stop(mc); return; }
        ticks++;
        if (ticks == 12) ghost.swingHand(Hand.MAIN_HAND);
        if (ticks == 15) {
            ClientPlayerEntity p = mc.player;
            p.maxHurtTime = 10;
            p.hurtTime = 10;
            SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", "entity.player.hurt"));
            if (ev != null) mc.getSoundManager().play(PositionedSoundInstance.master(ev, 1f, 1f));
        }
        if (ticks > 45) stop(mc);
    }
}
