package com.miscelanea.armasfuego.pistola;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * El estado del revólver va en el NBT del objeto (así el cliente lo tiene para el HUD sin paquetes y viaja con el arma
 * si se la pasas a otro para jugar a la ruleta):
 * <ul>
 *   <li>{@code Tambor}: 6 recámaras con el ordinal de {@link Carga};</li>
 *   <li>{@code Pos}: la recámara que va a disparar;</li>
 *   <li>{@code Girado}: tras girar el tambor no se sabe cuál toca (el HUD no lo enseña) hasta el siguiente disparo;</li>
 *   <li>{@code GiroT}: hora del mundo del último giro (para la animación y para no disparar mientras gira);</li>
 *   <li>{@code Oxido} 0..3, {@code Encerado} y {@code Usos} (disparos desde el último paso de óxido).</li>
 * </ul>
 */
public final class Tambor {
    public static final int N = 6;
    public static final String TAMBOR = "Tambor", POS = "Pos", GIRADO = "Girado", GIRO_T = "GiroT",
            OXIDO = "Oxido", ENCERADO = "Encerado", USOS = "Usos";
    /** Lo que tarda en pararse el tambor tras girarlo (ticks). */
    public static final int GIRO_TICKS = 16;

    private Tambor() {}

    public static int[] recamaras(ItemStack s) {
        int[] r = new int[N];
        CompoundTag t = s.getTag();
        if (t != null && t.contains(TAMBOR)) {
            int[] g = t.getIntArray(TAMBOR);
            System.arraycopy(g, 0, r, 0, Math.min(N, g.length));
        }
        return r;
    }

    public static Carga en(ItemStack s, int i) {
        return Carga.de(recamaras(s)[Math.floorMod(i, N)]);
    }

    public static void poner(ItemStack s, int i, Carga c) {
        int[] r = recamaras(s);
        r[Math.floorMod(i, N)] = c.ordinal();
        s.getOrCreateTag().putIntArray(TAMBOR, r);
    }

    public static int pos(ItemStack s) {
        return s.hasTag() ? Math.floorMod(s.getTag().getInt(POS), N) : 0;
    }

    public static void ponerPos(ItemStack s, int p) {
        s.getOrCreateTag().putInt(POS, Math.floorMod(p, N));
    }

    public static Carga siguiente(ItemStack s) {
        return en(s, pos(s));
    }

    /** Pasa a la recámara siguiente (como al amartillar). */
    public static void avanzar(ItemStack s) {
        ponerPos(s, pos(s) + 1);
    }

    public static int cargadas(ItemStack s) {
        int n = 0;
        for (int c : recamaras(s)) if (c != 0) n++;
        return n;
    }

    /** La primera recámara vacía empezando por la que toca, o -1 si está lleno. */
    public static int primeraVacia(ItemStack s) {
        int[] r = recamaras(s);
        int p = pos(s);
        for (int k = 0; k < N; k++)
            if (r[(p + k) % N] == 0) return (p + k) % N;
        return -1;
    }

    /** La última recámara cargada (yendo hacia atrás desde la anterior a la que toca), o -1 si está vacío. */
    public static int ultimaCargada(ItemStack s) {
        int[] r = recamaras(s);
        int p = pos(s);
        for (int k = 1; k <= N; k++)
            if (r[Math.floorMod(p - k, N)] != 0) return Math.floorMod(p - k, N);
        return -1;
    }

    public static boolean girado(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean(GIRADO);
    }

    public static long giroT(ItemStack s) {
        return s.hasTag() ? s.getTag().getLong(GIRO_T) : -1000;
    }

    public static int oxido(ItemStack s) {
        return s.hasTag() ? Math.max(0, Math.min(3, s.getTag().getInt(OXIDO))) : 0;
    }

    public static boolean encerado(ItemStack s) {
        return com.miscelanea.armasfuego.comun.Cobre.encerado(s);
    }
}
