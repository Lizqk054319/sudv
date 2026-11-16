package pl.holovis.mixin.extra;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.holovis.KosaTag;

/** Zapas dla Kosa Nametag: gdyby gracz używał domyślnego Entity.getDisplayName. */
@Mixin(Entity.class)
public abstract class EntityDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void holovis$tag(CallbackInfoReturnable<Text> cir) {
        Object self = this;
        if (self instanceof PlayerEntity p) {
            Text r = KosaTag.decorate(p, cir.getReturnValue());
            if (r != cir.getReturnValue()) cir.setReturnValue(r);
        }
    }
}
