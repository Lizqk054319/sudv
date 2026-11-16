package pl.holovis.mixin.extra;

import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.holovis.AspectRatio;

/** Aspect Ratio (wersja dla FOV jako float - Minecraft 1.21.2+). */
@Mixin(GameRenderer.class)
public abstract class AspectMixin {

    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), require = 0)
    private void holovis$aspect(float fov, CallbackInfoReturnable<Matrix4f> cir) {
        pl.holovis.Hooks.projection = true;
        AspectRatio.apply(cir.getReturnValue());
    }
}
