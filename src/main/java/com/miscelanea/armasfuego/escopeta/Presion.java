package com.miscelanea.armasfuego.escopeta;

import com.miscelanea.registry.ModEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Estado de la escopeta de presión guardado en el objeto (NBT): la presión del depósito, la tolva de cartuchos, el
 * óxido del cobre y si ha reventado. Todo son lecturas y escrituras sueltas para que valgan en el cliente (modelo, HUD)
 * y en el servidor.
 * <p>
 * La presión va de 0 al tope ({@link #tope}): 100 de serie y un 10 % más por nivel de Compresor. Las zonas del
 * manómetro son fracciones del tope (verde hasta la mitad, amarilla hasta el 80 % y roja hasta arriba); la fuerza del
 * disparo sale de la presión de verdad (100 = fuerza 1), así que con Compresor pega más.
 */
public final class Presion {
    private Presion() {}

    public static final String PRESION = "Presion", DISPAROS = "Disparos", TOLVA = "Tolva", TOLVA_N = "TolvaN",
            OXIDO = "Oxido", ENCERADA = "Encerada", REVENTADA = "Reventada", FUGA = "Fuga";

    /** Lo que cabe en la tolva (de un solo tipo). */
    public static final int TOLVA_MAX = 16;
    /** Por debajo de esto no sale nada al disparar (solo un "pff"). */
    public static final float MINIMA = 8;
    /** Ticks que se queda humeando tras reventar. */
    public static final int HUMEO = 100;

    public static final float ZONA_AMARILLA = 0.5F, ZONA_ROJA = 0.8F;

    private static CompoundTag tag(ItemStack s) {
        return s.getTag();
    }

    public static float presion(ItemStack s) {
        CompoundTag t = tag(s);
        return t == null ? 0 : t.getFloat(PRESION);
    }

    public static void ponerPresion(ItemStack s, float p) {
        s.getOrCreateTag().putFloat(PRESION, Math.max(0, p));
    }

    public static float tope(ItemStack s) {
        return 100F * (1 + 0.10F * nivel(s, Encantamiento.COMPRESOR));
    }

    /** Fracción del manómetro (0..1). */
    public static float fraccion(ItemStack s) {
        return Math.min(1, presion(s) / tope(s));
    }

    public static int nivel(ItemStack s, Encantamiento e) {
        return switch (e) {
            case COMPRESOR -> EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ESCOPETA_COMPRESOR.get(), s);
            case VALVULA -> EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ESCOPETA_VALVULA.get(), s);
            case DOBLE_CAMARA -> EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ESCOPETA_DOBLE_CAMARA.get(), s);
            case FUELLE -> EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ESCOPETA_FUELLE.get(), s);
            case RECICLAJE -> EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ESCOPETA_RECICLAJE.get(), s);
        };
    }

    /** Disparos que da una bombeada (dos con Doble cámara). */
    public static int disparosPorBombeada(ItemStack s) {
        return nivel(s, Encantamiento.DOBLE_CAMARA) > 0 ? 2 : 1;
    }

    public static int disparosHechos(ItemStack s) {
        CompoundTag t = tag(s);
        return t == null ? 0 : t.getInt(DISPAROS);
    }

    /** Lo que queda de disparos con la presión de ahora (0 si no hay presión). */
    public static int disparosQuedan(ItemStack s) {
        return presion(s) < MINIMA ? 0 : Math.max(0, disparosPorBombeada(s) - disparosHechos(s));
    }

    // ---------------------------------------------------------------- tolva

    public static TipoCartucho tolva(ItemStack s) {
        CompoundTag t = tag(s);
        if (t == null || t.getInt(TOLVA_N) <= 0) return null;
        return TipoCartucho.porId(t.getString(TOLVA));
    }

    public static int tolvaN(ItemStack s) {
        CompoundTag t = tag(s);
        return t == null ? 0 : t.getInt(TOLVA_N);
    }

    public static void ponerTolva(ItemStack s, TipoCartucho tipo, int n) {
        CompoundTag t = s.getOrCreateTag();
        if (tipo == null || n <= 0) {
            t.remove(TOLVA);
            t.remove(TOLVA_N);
        } else {
            t.putString(TOLVA, tipo.id);
            t.putInt(TOLVA_N, Math.min(TOLVA_MAX, n));
        }
    }

    // ---------------------------------------------------------------- cobre

    /** 0 normal, 1 expuesto, 2 erosionado, 3 oxidado. */
    public static int oxido(ItemStack s) {
        CompoundTag t = tag(s);
        return t == null ? 0 : Math.max(0, Math.min(3, t.getInt(OXIDO)));
    }

    public static void ponerOxido(ItemStack s, int o) {
        s.getOrCreateTag().putInt(OXIDO, Math.max(0, Math.min(3, o)));
    }

    public static boolean encerada(ItemStack s) {
        return com.miscelanea.armasfuego.comun.Cobre.encerado(s);
    }

    /** Ticks que le quedan humeando tras reventar (se guarda el tick del mundo en que acaba, para no tocar el NBT cada tick). */
    public static int reventada(ItemStack s, net.minecraft.world.level.Level level) {
        CompoundTag t = tag(s);
        if (t == null || level == null || !t.contains(REVENTADA)) return 0;
        return (int) Math.max(0, t.getLong(REVENTADA) - level.getGameTime());
    }

    /** Zona del manómetro: 0 vacío, 1 verde, 2 amarilla, 3 roja. */
    public static int zona(ItemStack s) {
        float p = presion(s);
        if (p < 0.5F) return 0;
        float f = fraccion(s);
        return f < ZONA_AMARILLA ? 1 : f < ZONA_ROJA ? 2 : 3;
    }

    /** Posición de la aguja en el modelo 2D (0 reposo .. 4 roja). */
    public static int agujaModelo(ItemStack s) {
        float p = presion(s);
        if (p < 0.5F) return 0;
        float f = fraccion(s);
        return f < 0.3F ? 1 : f < ZONA_AMARILLA ? 2 : f < ZONA_ROJA ? 3 : 4;
    }

    public enum Encantamiento {COMPRESOR, VALVULA, DOBLE_CAMARA, FUELLE, RECICLAJE}
}
