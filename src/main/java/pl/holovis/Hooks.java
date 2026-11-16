package pl.holovis;

/** Diagnostyka (/kosa diag): czy poszczególne zaczepy w kodzie Minecrafta naprawdę się wykonują. */
public final class Hooks {
    private Hooks() {}

    public static volatile boolean handFrame, heldItem, hurtCam, projection, options, sound;

    public static String report() {
        return "Ręka/Swing: " + ok(handFrame) + ", Knife: " + ok(heldItem) + ", Hurt Cam: " + (hurtCam ? "OK" : (options ? "OK (zapas)" : "BRAK"))
                + ", Aspect Ratio: " + ok(projection) + ", FullBright: " + ok(options) + ", Totem POP: " + ok(sound);
    }

    private static String ok(boolean b) { return b ? "OK" : "BRAK"; }
}
