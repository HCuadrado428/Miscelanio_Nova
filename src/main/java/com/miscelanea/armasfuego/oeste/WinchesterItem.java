package com.miscelanea.armasfuego.oeste;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.logros.LogrosMiscelanea;
import com.miscelanea.registry.ModEnchantments;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Winchester 1873 de palanca, «el arma que conquistó el Oeste». Usa las mismas balas que el revólver (como el de
 * verdad con el Colt), y además las de plata (el doble contra los no muertos: para hombres lobo y vampiros, como en
 * Castlevania) y las de dinamita (estallan donde dan, sin romper bloques, como en Red Dead). Tras cada disparo baja y
 * sube la palanca (a caballo, girándola con una mano) y se recarga bala a bala por la portilla. Encantamientos:
 * Repetición (palanca más rápida), Dead Eye (a caballo, recto y más fuerte) y Carga rápida.
 */
public class WinchesterItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("winchester_disparo");

    public WinchesterItem(Properties p) {
        super(p);
    }

    @Override
    public int capacidad(ItemStack s) {
        return EquilibrioArmas.winchesterCapacidad();
    }

    @Override
    public int ticksRecarga(ItemStack s) {
        return Math.max(2, Math.round(EquilibrioArmas.winchesterRecarga() * (1 - 0.25F * nivel(ModEnchantments.WINCHESTER_CARGA_RAPIDA, s))));
    }

    @Override
    public boolean recargaPorBala() {
        return true;
    }

    @Override
    protected boolean acepta(ItemStack m) {
        return m.is(ModItems.REVOLVER_BALA.get()) || m.is(ModItems.BALA_PLATA.get()) || m.is(ModItems.BALA_DINAMITA.get());
    }

    @Override
    protected Item municionNormal() {
        return ModItems.REVOLVER_BALA.get();
    }

    @Override
    protected SoundEvent sonidoRecarga() {
        return ModSounds.WINCHESTER_CARGAR.get();
    }

    /** Ticks de la palanca: a caballo, con el giro, un 30 % menos; Repetición, un 15 % menos por nivel. */
    public static int palanca(Player p, ItemStack s) {
        float t = EquilibrioArmas.winchesterPalanca() * (1 - 0.15F * nivel(ModEnchantments.WINCHESTER_REPETICION, s));
        if (p.isPassenger()) t *= 0.7F;
        return Math.max(2, Math.round(t));
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return palanca(p, s);
    }

    @Override
    protected void disparar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        double alcance = EquilibrioArmas.winchesterAlcance();
        Item tipo = tipoActual(s);
        boolean deadEye = p.isPassenger() && nivel(ModEnchantments.WINCHESTER_DEAD_EYE, s) > 0;
        float dano = EquilibrioArmas.winchesterDano() * (deadEye ? 1.25F : 1F);
        var t = tiro(p, dano, alcance, alcance * 0.5, 0.7F, deadEye ? 0 : 0.005, 0.2F, DANO, false,
                tipo == ModItems.BALA_PLATA.get() ? ParticleTypes.END_ROD : null);
        if (tipo == ModItems.BALA_PLATA.get()) {
            // plata: otro tanto contra los no muertos (con el brillo de la plata)
            if (t.entidad() != null && t.entidad().getMobType() == MobType.UNDEAD) {
                Balistica.herir(t.entidad(), p, dano, 0.2F, DANO, true);
                l.sendParticles(ParticleTypes.END_ROD, t.pos().x, t.pos().y, t.pos().z, 10, 0.2, 0.3, 0.2, 0.05);
                if (!t.entidad().isAlive()) LogrosMiscelanea.dar(p, "winchester_plata");
            }
        } else if (tipo == ModItems.BALA_DINAMITA.get()) {
            dinamita(p, t.pos(), t.entidad());
        }
        efectos(l, p, boca(p, 1.0, 0.25, 0.2), ModSounds.WINCHESTER_DISPARO.get(), 4.0F, 0.95F, 3, false);
        // la palanca: el casquillo sale por arriba a mitad del recorrido
        casquillo(l, p, boca(p, 0.35, 0.22, 0.12));
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.WINCHESTER_PALANCA.get(), SoundSource.PLAYERS, 0.8F, p.isPassenger() ? 1.15F : 1.0F);
        LogrosMiscelanea.dar(p, "winchester_disparo");
        if (p.getVehicle() instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) LogrosMiscelanea.dar(p, "winchester_giro");
    }

    /** Bala de dinamita: un estallido pequeño donde da (sin romper bloques). */
    static void dinamita(ServerPlayer p, Vec3 q, LivingEntity directo) {
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, q.x, q.y, q.z, 4, 0.2, 0.2, 0.2, 0.02);
        l.playSound(null, q.x, q.y, q.z, ModSounds.CANON_DISPARO.get(), SoundSource.PLAYERS, 1.2F, 1.7F);
        for (LivingEntity v : l.getEntitiesOfClass(LivingEntity.class, new AABB(q, q).inflate(2.2), e -> e.isAlive() && e != p && e != directo)) {
            double d = v.position().distanceTo(q);
            if (d > 2.2) continue;
            Balistica.herir(v, p, (float) (4 * (1 - d / 2.2)), 0F, DANO, false);
            Vec3 emp = v.position().subtract(q).normalize().scale(0.4);
            v.push(emp.x, 0.2, emp.z);
            v.hurtMarked = true;
        }
    }
}
