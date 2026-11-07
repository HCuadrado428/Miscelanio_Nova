package com.miscelanea.voz;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabla GENÉRICA "efecto de poción → voz": qué id de voz (una cadena, ver {@code com.miscelanea.client.voz.Voces})
 * se aplica a quien tiene cada efecto. El servidor la consulta para cualquier MobEffect, sea de este paquete o de
 * otro: para que una poción nueva cambie la voz basta con añadir aquí una línea
 * ({@code VozPorEfecto.poner(new ResourceLocation("miscelanea", "mi_efecto"), "mi_voz")}) y registrar el
 * procesador de esa voz en {@code Voces}. Un efecto puede tener varias voces separadas por comas ("grave,amortiguado").
 * <p>
 * Los ids de otras ramas (gigante, enano, sed_sangre, niebla) ya están apuntados; el efecto
 * fantasma NO cambia la voz (la voz "fantasma" sigue existiendo para ponerla a mano con /miscelanea voz): si el efecto no
 * existe en el juego, la entrada simplemente no se usa.
 */
public final class VozPorEfecto {
    private VozPorEfecto() {}

    private static final Map<ResourceLocation, String> TABLA = new LinkedHashMap<>();

    // ids de voz que entiende el cliente (Voces)
    public static final String HELIO = "helio", TARTAMUDEO = "tartamudeo", VENTILADOR = "ventilador", LENGUA_TRAPO = "lengua_trapo",
            GRAVE = "grave", AGUDO = "agudo", SUSURRO = "susurro", FANTASMA = "fantasma", GUTURAL = "gutural", AMORTIGUADO = "amortiguado", ESTATICA = "estatica";

    /** Entradas por defecto (se cargan al registrar; se pueden sobrescribir después). */
    static void porDefecto() {
        delMod("helio", HELIO);
        delMod("tartamudeo", TARTAMUDEO);
        delMod("ventilador", VENTILADOR);
        delMod("borrachera", LENGUA_TRAPO);
        // efectos de otras ramas
        delMod("gigante", GRAVE);
        delMod("enano", AGUDO);
        delMod("sed_sangre", GUTURAL);
        delMod("niebla", AMORTIGUADO);
        // el hombre de estática: radio rota
        delMod("estatica", ESTATICA);
    }

    private static void delMod(String efectoDelMod, String voz) {
        poner(new ResourceLocation(Miscelanea.MOD_ID, efectoDelMod), voz);
    }

    /** Asocia un efecto (de cualquier mod) a una o varias voces ("grave,amortiguado"). {@code null} lo quita. */
    public static synchronized void poner(ResourceLocation efecto, String voces) {
        if (voces == null || voces.isBlank()) TABLA.remove(efecto);
        else TABLA.put(efecto, voces);
    }

    public static synchronized String vozDe(ResourceLocation efecto) {
        return TABLA.get(efecto);
    }

    /** Voces activas de una entidad, por sus efectos: lista de (id de voz, amplificador), sin repetir ids. */
    public static List<VozEntrada> vocesDe(LivingEntity e) {
        List<VozEntrada> out = new ArrayList<>();
        // voces puestas a mano con /miscelanea voz (duran hasta quitarlas; se guardan con el jugador)
        if (e instanceof net.minecraft.world.entity.player.Player p) {
            for (String v : VozComando.fijas(p)) out.add(new VozEntrada(v, 1));
        }
        for (MobEffectInstance m : e.getActiveEffects()) {
            MobEffect ef = m.getEffect();
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(ef);
            if (id == null) continue;
            String voces = vozDe(id);
            if (voces == null) continue;
            for (String v : voces.split(",")) {
                v = v.trim();
                if (v.isEmpty()) continue;
                boolean ya = false;
                for (VozEntrada ve : out) {
                    if (ve.id().equals(v)) {
                        ya = true;
                        break;
                    }
                }
                if (!ya) out.add(new VozEntrada(v, Math.max(0, Math.min(m.getAmplifier(), 15))));
            }
        }
        return out;
    }
}
