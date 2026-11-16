package pl.holovis.mixin.extra;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.holovis.HoloConfig;
import pl.holovis.Knives;

/**
 * Swing Animations (pozycja i rozmiar ręki / miecza) oraz Knife (nóż z CS2 zamiast miecza).
 * Ten mixin jest w osobnej, nieobowiązkowej konfiguracji: gdyby coś się nie zgadzało, gra i tak się uruchomi.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("HEAD"), require = 0)
    private void holovis$swingHead(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vcp,
                                   ClientPlayerEntity player, int light, CallbackInfo ci) {
        matrices.push();
        pl.holovis.Hooks.handFrame = true;
        HoloConfig c = HoloConfig.get();
        if (!c.swingOn) return;
        if (c.swingOnlySwords && !Knives.isSword(player.getMainHandStack())) return;
        matrices.translate(c.swingX, c.swingY, c.swingZ);
        float s = Math.max(0.2f, c.swingScale);
        if (Math.abs(s - 1f) > 0.002f) {
            // skalowanie względem typowego położenia ręki, żeby przedmiot nie "uciekał" z ekranu
            matrices.translate(0.45f, -0.4f, -0.7f);
            matrices.scale(s, s, s);
            matrices.translate(-0.45f, 0.4f, 0.7f);
        }
    }

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("RETURN"), require = 0)
    private void holovis$swingReturn(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vcp,
                                     ClientPlayerEntity player, int light, CallbackInfo ci) {
        matrices.pop();
    }

    /** Knife (główny sposób): podmieniamy cały przedmiot w ręce, z tym samym ruchem co w grze. */
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void holovis$knifeHand(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                                   ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vcp,
                                   int light, CallbackInfo ci) {
        pl.holovis.Hooks.heldItem = true;
        if (!Knives.replaces(item) || player.isUsingItem() || player.isUsingSpyglass()) return;
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        Knives.drawHand(matrices, vcp, arm == Arm.RIGHT, swingProgress, equipProgress);
        ci.cancel();
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void holovis$knife(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
                               MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        pl.holovis.Hooks.heldItem = true;
        if (!mode.isFirstPerson() || !Knives.replaces(stack)) return;
        Knives.drawFirstPerson(matrices, vcp, leftHanded);
        ci.cancel();
    }
}
