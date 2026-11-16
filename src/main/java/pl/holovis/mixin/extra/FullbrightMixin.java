package pl.holovis.mixin.extra;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.holovis.HoloConfig;

/** FullBright: podbija wartość jasności (gamma) ponad zwykły limit - jaskinie i noc stają się widoczne. */
@Mixin(SimpleOption.class)
public abstract class FullbrightMixin<T> {

    @SuppressWarnings("unchecked")
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true, require = 0)
    private void holovis$gamma(CallbackInfoReturnable<T> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        pl.holovis.Hooks.options = true;
        HoloConfig c = HoloConfig.get();
        Object self = this;
        // Hurt Cam "bez trzęsienia": siła przechylenia kamery po obrażeniach = 0 (zapas, gdyby drugi zaczep nie zadziałał)
        if (c.hurtCamOn && c.hurtCamMode == 0 && self == mc.options.getDamageTiltStrength()) {
            cir.setReturnValue((T) (Double) 0.0);
            return;
        }
        if (!c.fullbright || c.fullbrightAmount <= 0.001f) return;
        if (self != mc.options.getGamma()) return;
        Object v = cir.getReturnValue();
        if (v instanceof Double d) cir.setReturnValue((T) (Double) (d + c.fullbrightAmount * 16.0));
    }
}
