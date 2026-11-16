package pl.holovis.mixin.extra;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.holovis.KosaTag;

/** Kosa Nametag na liście graczy (TAB): neonowe K po lewej stronie nicku. */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void holovis$tag(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (entry == null) return;
        Text r = KosaTag.decorate(KosaTag.isUser(entry.getProfile().getId()), entry.getProfile().getName(), cir.getReturnValue());
        if (r != cir.getReturnValue()) cir.setReturnValue(r);
    }
}
