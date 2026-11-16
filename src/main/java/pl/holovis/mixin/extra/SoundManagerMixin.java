package pl.holovis.mixin.extra;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.holovis.TotemPop;

/** Totem POP: zamiast dźwięku zbicia totemu gra wybrany dźwięk. */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void holovis$totem(SoundInstance sound, CallbackInfo ci) {
        pl.holovis.Hooks.sound = true;
        if (TotemPop.onSound(sound)) ci.cancel();
    }
}
