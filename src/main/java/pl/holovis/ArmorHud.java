package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Armor Status: cztery kafelki (hełm, napierśnik, spodnie, buty) z własnymi ikonkami w kolorze materiału,
 * procentem i paskiem wytrzymałości. Bez ikon z Minecrafta.
 */
public final class ArmorHud {
    private ArmorHud() {}

    private static final int TW = 34, TH = 42, GAP = 3;

    private static final String[][] SHAPES = {
            {   // hełm
                    "....####....",
                    "..########..",
                    ".##########.",
                    "############",
                    "####....####",
                    "###......###",
                    "###......###",
                    "##........##"},
            {   // napierśnik
                    "###......###",
                    "####....####",
                    "############",
                    "############",
                    ".##########.",
                    "..########..",
                    "..########..",
                    "..########..",
                    "..########.."},
            {   // spodnie
                    "############",
                    "############",
                    "####....####",
                    "###......###",
                    "###......###",
                    "###......###",
                    "###......###",
                    "###......###"},
            {   // buty
                    "###......###",
                    "###......###",
                    "###......###",
                    "####....####",
                    "#####..#####",
                    "#####..#####"},
            {   // miecz
                    "..........##",
                    ".........###",
                    "........###.",
                    ".......###..",
                    "..#...###...",
                    "..##.###....",
                    "...####.....",
                    "...###......",
                    "..##.##.....",
                    ".##.........",
                    "##.........."}};

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static int material(ItemStack st) {
        if (st == null || st.isEmpty()) return 0x5E6678;
        String p = Registries.ITEM.getId(st.getItem()).getPath();
        if (p.startsWith("netherite")) return 0x8A7486;
        if (p.startsWith("diamond")) return 0x4FE3E0;
        if (p.startsWith("iron")) return 0xDCE1E8;
        if (p.startsWith("golden")) return 0xF5C542;
        if (p.startsWith("chainmail")) return 0xA4ABB6;
        if (p.startsWith("leather")) return 0xB07A48;
        if (p.startsWith("turtle")) return 0x5CC86A;
        if (p.startsWith("stone")) return 0x9AA0A8;
        if (p.startsWith("wooden")) return 0xA87B4F;
        return HoloConfig.get().guiColor.rgb();
    }

    private static int count() { return HoloConfig.get().armorHands ? 5 : 4; }

    public static boolean visible() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        for (EquipmentSlot s : SLOTS) if (!mc.player.getEquippedStack(s).isEmpty()) return true;
        return false;
    }

    public static int[] size() {
        int n = count();
        return new int[]{n * TW + (n - 1) * GAP, TH};
    }

    /** Kolor wg wytrzymałości: zielony -> żółty -> czerwony. */
    private static int dur(float f) {
        return f > 0.5f ? Gfx.mix(0xFFD23C, 0x4CE07A, (f - 0.5f) * 2f) : Gfx.mix(0xFF3C4E, 0xFFD23C, f * 2f);
    }

    private static void shape(DrawContext c, String[] rows, int x, int y, int rgb, float a) {
        for (int r = 0; r < rows.length; r++) {
            int col = Gfx.mix(Gfx.mix(rgb, 0xFFFFFF, 0.25f), Gfx.mix(rgb, 0x000000, 0.35f), r / (float) Math.max(1, rows.length - 1));
            String row = rows[r];
            for (int i = 0; i < row.length(); i++) {
                if (row.charAt(i) != '#') continue;
                c.fill(x + i, y + r, x + i + 1, y + r + 1, Gfx.argb(col, a));
            }
        }
    }

    public static void draw(DrawContext ctx, int x, int y, boolean preview) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int n = count();
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            ItemStack st = ItemStack.EMPTY;
            if (mc.player != null) st = i < 4 ? mc.player.getEquippedStack(SLOTS[i]) : mc.player.getMainHandStack();
            float f;
            boolean has = !st.isEmpty();
            int max = has ? st.getMaxDamage() : 0;
            if (preview && !has) { has = true; f = new float[]{1f, 0.62f, 0.27f, 0.09f, 0.8f}[i]; }
            else f = max > 0 ? 1f - st.getDamage() / (float) max : 1f;
            int mat = preview && st.isEmpty() ? new int[]{0x4FE3E0, 0x8A7486, 0xDCE1E8, 0xF5C542, 0x4FE3E0}[i] : material(st);
            int dc = dur(f);
            int tx = x + i * (TW + GAP);

            Gfx.rrect(ctx, tx, y, TW, TH, 7, 0xC80B0E15);
            if (has) Gfx.rboxV(ctx, tx, y, TW, 22, 7, Gfx.argb(mat, 0.22f), Gfx.argb(mat, 0f));
            String[] sh = SHAPES[i];
            int sx = tx + (TW - 12) / 2, sy = y + 4 + (11 - sh.length) / 2;
            shape(ctx, sh, sx, sy, mat, has ? 1f : 0.18f);

            if (has) {
                boolean low = f < 0.15f && (now / 350) % 2 == 0;
                String pct = (max > 0 || preview) ? Math.round(f * 100) + "%" : "∞";
                ctx.drawText(tr, pct, tx + (TW - tr.getWidth(pct)) / 2, y + 18, low ? 0xFFFF5A5A : 0xFFF2F5FA, true);
                int bx = tx + 4, bw = TW - 8, by = y + TH - 7;
                Gfx.rrect(ctx, bx, by, bw, 3, 1, 0x30FFFFFF);
                Gfx.rrect(ctx, bx, by, Math.max(2, (int) (bw * f)), 3, 1, 0xFF000000 | dc);
            } else {
                ctx.drawText(tr, "-", tx + (TW - tr.getWidth("-")) / 2, y + 18, 0xFF4E5668, false);
            }
        }
    }
}
