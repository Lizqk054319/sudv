package pl.holovis.mixin.extra;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.holovis.HoloConfig;

/** Hurt Cam: kamera nie trzęsie się po otrzymaniu obrażeń (tryb "bez trzęsienia"). */
@Mixin(GameRenderer.class)
public abstract class HurtCamMixin {

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void holovis$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        pl.holovis.Hooks.hurtCam = true;
        HoloConfig c = HoloConfig.get();
        if (c.hurtCamOn && c.hurtCamMode == 0) ci.cancel();
    }
}
