package pl.holovis;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.Identifier;

/** Totem POP: podmienia dźwięk zbicia totemu (swojego i cudzego) na wybrany. */
public final class TotemPop {
    private TotemPop() {}

    private static boolean own;

    /** @return true, gdy oryginalny dźwięk ma zostać wyciszony */
    public static boolean onSound(SoundInstance s) {
        if (own || s == null) return false;
        HoloConfig c = HoloConfig.get();
        if (!c.totemSound.enabled) return false;
        Identifier id = s.getId();
        if (id == null || !"minecraft".equals(id.getNamespace()) || !"item.totem.use".equals(id.getPath())) return false;
        play();
        return true;
    }

    public static void play() {
        own = true;
        try {
            HoloSounds.play(HoloConfig.get().totemSound);
        } finally {
            own = false;
        }
    }
}
