package com.miscelanea.voz;

import net.minecraft.util.RandomSource;

/** Tartamudeo en el chat: "ho-ho-hola, ¿q-q-qué tal?". La voz la tartamudea el cliente ({@code ProcTartamudeo}). */
public final class Tartamudeo {
    private Tartamudeo() {}

    private static final String VOCALES = "aeiouáéíóúüAEIOUÁÉÍÓÚÜ";

    public static String tartamudear(String texto, int amp, RandomSource r) {
        float prob = Math.min(0.9F, 0.38F + 0.22F * amp);
        StringBuilder out = new StringBuilder(texto.length() * 2);
        int i = 0, n = texto.length();
        while (i < n) {
            char c = texto.charAt(i);
            if (!Character.isLetter(c)) {
                out.append(c);
                i++;
                continue;
            }
            int j = i;
            while (j < n && Character.isLetter(texto.charAt(j))) j++;
            String palabra = texto.substring(i, j);
            i = j;
            if (palabra.length() < 2 || r.nextFloat() > prob) {
                out.append(palabra);
                continue;
            }
            String silaba = silaba(palabra);
            // a veces solo la primera letra ("p-p-por"), a veces la sílaba entera ("ho-ho-hola")
            if (r.nextFloat() < 0.4F || silaba.length() > 3) silaba = palabra.substring(0, 1);
            int veces = 1 + r.nextInt(2 + Math.min(amp, 2));
            for (int k = 0; k < veces; k++) out.append(silaba).append('-');
            out.append(palabra);
        }
        return out.toString();
    }

    /** Primera sílaba, a ojo: hasta la primera vocal (incluidas vocales seguidas). */
    static String silaba(String p) {
        int i = 0, n = p.length();
        while (i < n && VOCALES.indexOf(p.charAt(i)) < 0) i++;
        while (i < n && VOCALES.indexOf(p.charAt(i)) >= 0) i++;
        if (i >= n) return p.substring(0, Math.min(2, n));
        return p.substring(0, i);
    }
}
