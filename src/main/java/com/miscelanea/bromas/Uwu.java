package com.miscelanea.bromas;

import net.minecraft.util.RandomSource;

/**
 * Traductor al "uwu": r/l → w (respetando mayúsculas), "na/ne/no/nu" → "nya/nye..." a veces, tartamudeo al principio
 * ("h-hola"), y caritas y "nya~" por aquí y por allá. Las direcciones (http...) y las menciones (@...) no se tocan.
 */
public final class Uwu {
    private Uwu() {}

    private static final String[] CARITAS = {"uwu", "owo", ">w<", "^w^", "nya~", "(◕ᴗ◕✿)", "(｡♥‿♥｡)", "(=^･ω･^=)", "ʕ•ᴥ•ʔ",
            "(˘ω˘)", "(⁄ ⁄>⁄ ▽ ⁄<⁄ ⁄)", "(・`ω´・)", "x3", ":3", "(≧◡≦)", "✧･ﾟ: *✧"};

    public static String traducir(String s, RandomSource r) {
        if (s.isBlank()) return s;
        String[] palabras = s.split(" ", -1);
        StringBuilder out = new StringBuilder();
        boolean primera = true;
        for (int i = 0; i < palabras.length; i++) {
            String w = palabras[i];
            if (i > 0) out.append(' ');
            if (w.isEmpty()) continue;
            String low = w.toLowerCase(java.util.Locale.ROOT);
            if (low.startsWith("http") || low.startsWith("www.") || w.startsWith("@")) {
                out.append(w);
                primera = false;
                continue;
            }
            String t = palabra(w, r);
            // tartamudeo: en la primera palabra casi siempre; luego de vez en cuando
            if (Character.isLetter(t.charAt(0)) && t.length() > 1 && (primera ? r.nextFloat() < 0.75F : r.nextFloat() < 0.12F)) {
                String c = t.substring(0, 1);
                t = c + "-" + (r.nextFloat() < 0.3F ? c.toLowerCase(java.util.Locale.ROOT) + "-" : "") + c.toLowerCase(java.util.Locale.ROOT) + t.substring(1);
            }
            primera = false;
            // signos al final de la palabra
            if (t.endsWith("!")) t = t + (r.nextBoolean() ? "! >w<" : "!1 owo");
            else if (t.endsWith("?")) t = t + (r.nextBoolean() ? " owo" : "? ・ω・");
            else if ((t.endsWith(",") || t.endsWith(".")) && r.nextFloat() < 0.35F) t = t + " " + carita(r);
            out.append(t);
        }
        String res = out.toString();
        if (r.nextFloat() < 0.85F) res = res + " " + carita(r);
        if (r.nextFloat() < 0.25F) res = res + " " + carita(r);
        return res.length() > 256 ? res.substring(0, 256) : res;
    }

    private static String carita(RandomSource r) {
        return CARITAS[r.nextInt(CARITAS.length)];
    }

    private static String palabra(String w, RandomSource r) {
        StringBuilder b = new StringBuilder(w.length() + 4);
        boolean nya = r.nextFloat() < 0.6F;
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            char sig = i + 1 < w.length() ? w.charAt(i + 1) : 0;
            switch (c) {
                case 'r', 'l' -> {
                    // "rr" y "ll" → una sola w
                    if (sig == c) i++;
                    b.append('w');
                }
                case 'R', 'L' -> {
                    if (Character.toLowerCase(sig) == Character.toLowerCase(c)) i++;
                    b.append('W');
                }
                case 'n', 'N' -> {
                    b.append(c);
                    if (nya && "aeouáéóú".indexOf(Character.toLowerCase(sig)) >= 0) b.append(Character.isUpperCase(sig) ? 'Y' : 'y');
                }
                case 'ñ' -> b.append("ny");
                case 'Ñ' -> b.append("NY");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
