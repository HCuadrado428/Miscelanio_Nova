package com.miscelanea.armas;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import com.miscelanea.mixin.LivingEntityGolpeAccessor;
import com.miscelanea.porra.PorraItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Reparto del daño de las armas del mod para que compitan con el equipo de dragón (Ice and Fire) y Protección V.
 * <p>
 * Cada golpe se divide en tres partes (los porcentajes salen de la config, sección [armas]):
 * <ul>
 *   <li>normal: con la fuente de siempre (la armadura y los encantamientos la reducen como siempre);</li>
 *   <li>perforante ({@code miscelanea:arma_perforante}, etiqueta {@code #minecraft:bypasses_armor}): se salta la
 *       armadura y su dureza, pero Protección sí la reduce;</li>
 *   <li>verdadera ({@code miscelanea:arma_verdadera}, {@code bypasses_armor} + {@code bypasses_enchantments}): se salta
 *       la armadura y los encantamientos de protección (Resistencia y la absorción siguen contando).</li>
 * </ul>
 * Las partes extra van en el mismo tick, sin empuje propio y sin romper la ventana de invulnerabilidad.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class DanoArma {
    private DanoArma() {}

    public static final ResourceKey<DamageType> PERFORANTE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "arma_perforante"));
    public static final ResourceKey<DamageType> VERDADERA =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "arma_verdadera"));

    /** Armas con su reparto propio (id de config y valores por defecto: perforación, verdadero). */
    public enum Arma {
        ESCOPETA("escopeta", 0.25, 0.10),
        COHETE("lanzacohetes", 0.35, 0.10),
        BFG("bfg", 0.0, 0.30),
        BFG_RAYO("bfg_rayo", 0.0, 0.15),
        BFG_VORTICE("bfg_vortice", 0.0, 0.20),
        BALLESTA("ballesta", 0.30, 0.05),
        PORRA_DESCARGA("porra_descarga", 0.0, 0.50),
        PORRA_GOLPE("porra_golpe", 0.25, 0.0),
        LANZATINTAS("lanzatintas", 0.30, 0.05),
        RODILLO("rodillo", 0.30, 0.0),
        MAZO("mazo", 0.30, 0.10),
        HACHA("hacha", 0.30, 0.05),
        LLAVE("llave_espada", 0.30, 0.05),
        GRAVEDAD("pistola_gravedad", 0.40, 0.0);

        public final String id;
        public final double perforacionDef, verdaderoDef;

        Arma(String id, double p, double v) {
            this.id = id;
            this.perforacionDef = p;
            this.verdaderoDef = v;
        }

        public float perforacion() { return (float) ConfigServidor.armaPerforacion(this); }
        public float verdadero() { return (float) ConfigServidor.armaVerdadero(this); }
    }

    /** True mientras se aplica una parte extra (para que otros manejadores no la cuenten como golpe nuevo). */
    public static boolean extra;

    private record Pendiente(LivingEntity v, Entity causa, float perforante, float verdadero) {}
    private static final List<Pendiente> PENDIENTES = new ArrayList<>();

    /**
     * Aplica {@code total} con el reparto del arma. La primera parte que haya es el golpe "de verdad" (escudo,
     * invulnerabilidad, empuje...); si no entra, no se aplica nada más. Devuelve si el golpe entró.
     */
    public static boolean golpear(LivingEntity v, DamageSource fuente, float total, Arma a) {
        if (total <= 0) return false;
        float[] r = reparto(a);
        float normal = total * (1 - r[0] - r[1]), perf = total * r[0], verd = total * r[1];
        Entity causa = fuente.getEntity();
        if (normal > 0.001F) {
            if (!v.hurt(fuente, normal)) return false;
        } else if (perf > 0.001F) {
            if (!v.hurt(fuente(v, PERFORANTE, causa), perf)) return false;
            perf = 0;
        } else {
            return v.hurt(fuente(v, VERDADERA, causa), verd);
        }
        aplicarExtras(v, causa, perf, verd);
        return true;
    }

    /**
     * Para golpes que ya están en marcha (LivingHurtEvent): deja en el evento la parte normal y aplica el resto
     * al acabar el tick.
     */
    public static void repartir(LivingHurtEvent e, Arma a) {
        float total = e.getAmount();
        if (total <= 0) return;
        float[] r = reparto(a);
        if (r[0] + r[1] <= 0) return;
        e.setAmount(total * (1 - r[0] - r[1]));
        PENDIENTES.add(new Pendiente(e.getEntity(), e.getSource().getEntity(), total * r[0], total * r[1]));
    }

    private static float[] reparto(Arma a) {
        float p = Math.max(0, a.perforacion()), w = Math.max(0, a.verdadero());
        if (p + w > 1) {
            float s = p + w;
            p /= s;
            w /= s;
        }
        return new float[]{p, w};
    }

    private static DamageSource fuente(LivingEntity v, ResourceKey<DamageType> tipo, Entity causa) {
        return new DamageSource(v.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(tipo), null, causa);
    }

    private static void aplicarExtras(LivingEntity v, Entity causa, float perf, float verd) {
        if (perf > 0.001F) extra(v, fuente(v, PERFORANTE, causa), perf);
        if (verd > 0.001F) extra(v, fuente(v, VERDADERA, causa), verd);
    }

    private static void extra(LivingEntity v, DamageSource src, float cantidad) {
        if (!v.isAlive()) return;
        LivingEntityGolpeAccessor acc = (LivingEntityGolpeAccessor) v;
        int inv = v.invulnerableTime;
        float ultimo = acc.miscelanea$ultimoDano();
        Vec3 mov = v.getDeltaMovement();
        boolean marcado = v.hurtMarked;
        v.invulnerableTime = 0;
        extra = true;
        try {
            v.hurt(src, cantidad);
        } finally {
            extra = false;
        }
        // la ventana de invulnerabilidad sigue siendo la del golpe entero
        v.invulnerableTime = inv;
        acc.miscelanea$ponerUltimoDano(ultimo + cantidad);
        v.setDeltaMovement(mov);
        v.hurtMarked = marcado;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || PENDIENTES.isEmpty()) return;
        List<Pendiente> l = new ArrayList<>(PENDIENTES);
        PENDIENTES.clear();
        for (Pendiente p : l) aplicarExtras(p.v, p.causa, p.perforante, p.verdadero);
    }

    /** Golpes cuerpo a cuerpo de la porra y virotes de la ballesta de repetición (los demás se reparten donde se hacen). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void herido(LivingHurtEvent e) {
        if (extra || e.getEntity().level().isClientSide) return;
        DamageSource s = e.getSource();
        Entity directo = s.getDirectEntity();
        if (directo instanceof AbstractArrow f && f.getTags().contains(Ballesta.ETIQUETA)) {
            repartir(e, Arma.BALLESTA);
        } else if (directo instanceof Player p && s.is(DamageTypes.PLAYER_ATTACK) && p.getMainHandItem().getItem() instanceof PorraItem) {
            e.setAmount(e.getAmount() * ConfigServidor.porraGolpeMult());
            repartir(e, Arma.PORRA_GOLPE);
        }
    }
}
