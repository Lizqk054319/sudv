package pl.holovis;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

/** Wiadomości moda na czacie: [Kosa(fioletowe)Visuals(czarne)] treść. */
public final class Chat {
    private Chat() {}

    private static MutableText part(String s, int rgb) {
        return Text.literal(s).setStyle(Style.EMPTY.withColor(rgb));
    }

    public static MutableText prefix() {
        return part("[", 0xB8C0D4)
                .append(part("Kosa", 0xA05CFF))
                .append(part("Visuals", 0x000000))
                .append(part("] ", 0xB8C0D4));
    }

    public static Text msg(String s) {
        return prefix().append(part(s, 0xE8EEF8));
    }
}
