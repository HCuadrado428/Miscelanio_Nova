package com.miscelanea.armasfuego.oeste;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.logros.LogrosMiscelanea;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Thompson «Tommy gun» de los años 20 con cargador de tambor. Manteniendo el clic derecho dispara en ráfaga: cuanto
 * más larga, más sube y más dispersa (el retroceso se nota en la vista). El tambor se cambia entero (agachado +
 * mantener clic derecho con un cargador de tambor encima). Se guarda en un estuche de violín, claro.
 */
public class ThompsonItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("thompson_disparo");
    /** Balas seguidas de la ráfaga actual (sube la dispersión). */
    public static final String RAFAGA = "Rafaga";

    public ThompsonItem(Properties p) {
        super(p);
    }

    @Override
    public int capacidad(ItemStack s) {
        // Tambor de Chicago: 25 más por nivel
        return EquilibrioArmas.thompsonCapacidad() + 25 * nivel(com.miscelanea.registry.ModEnchantments.THOMPSON_CHICAGO, s);
    }

    @Override
    public int ticksRecarga(ItemStack s) {
        return EquilibrioArmas.thompsonRecarga();
    }

    @Override
    protected boolean acepta(ItemStack m) {
        return m.is(ModItems.CARGADOR_THOMPSON.get()) || m.is(ModItems.TAMBOR_TRAZADOR.get()) || m.is(ModItems.TAMBOR_AGUA.get()) || m.is(ModItems.TAMBOR_TINTA.get());
    }

    @Override
    protected Item municionNormal() {
        return ModItems.CARGADOR_THOMPSON.get();
    }

    /** Se cambia el tambor entero (las balas que quedaban en el viejo se pierden: realismo medio). */
    @Override
    protected int balasPorObjeto(ItemStack s) {
        ponerBalas(s, 0);
        return capacidad(s);
    }

    @Override
    protected boolean cambiaEntera() {
        return true;
    }

    @Override
    protected int usosPorFase() {
        return 300;
    }

    /** Compensador: menos subida y menos dispersión en la ráfaga (0..1). */
    public static float compensador(ItemStack s) {
        return 1 - 0.25F * nivel(com.miscelanea.registry.ModEnchantments.THOMPSON_COMPENSADOR, s);
    }

    @Override
    protected void empezarRecarga(ServerPlayer p, ItemStack s) {
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.THOMPSON_TAMBOR.get(), SoundSource.PLAYERS, 0.8F, 0.8F);
    }


    @Override
    protected SoundEvent sonidoRecarga() {
        return ModSounds.THOMPSON_TAMBOR.get();
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return EquilibrioArmas.thompsonCadencia();
    }

    @Override
    protected float probAtasco(Player p, ItemStack s) {
        return EquilibrioArmas.encasquilladoBase() * 0.4F;   // por bala (tira muchas)
    }

    @Override
    protected InteractionResultHolder<ItemStack> apretarGatillo(Level level, Player p, ItemStack s) {
        CompoundTag t = s.getOrCreateTag();
        t.putInt(MODO, MODO_RAFAGA);
        t.putInt(RAFAGA, 0);
        p.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        return InteractionResultHolder.consume(s);
    }

    @Override
    protected void rafaga(Level level, Player p, ItemStack s, int t) {
        int cad = EquilibrioArmas.thompsonCadencia();
        // Gatillo fácil: un 25 % más de balas por nivel (se van acumulando los ticks)
        CompoundTag tg = s.getOrCreateTag();
        float acum = (t == 0 ? cad : tg.getFloat("Acum")) + 1 + 0.25F * nivel(com.miscelanea.registry.ModEnchantments.THOMPSON_GATILLO, s);
        if (acum < cad + 1) {
            tg.putFloat("Acum", acum);
            return;
        }
        acum -= cad;
        tg.putFloat("Acum", acum);
        if (balas(s) <= 0 || atascada(s)) {
            intentar(level, p, s);   // clic
            p.stopUsingItem();
            return;
        }
        intentar(level, p, s);
    }

    public static int rafagaN(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt(RAFAGA) : 0;
    }

    @Override
    protected void disparar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        int n = rafagaN(s) + 1;
        s.getOrCreateTag().putInt(RAFAGA, n);
        double disp = Math.min(0.09, 0.02 + 0.004 * n * compensador(s));
        double alcance = EquilibrioArmas.thompsonAlcance();
        Item tipo = tipoActual(s);
        boolean agua = tipo == ModItems.TAMBOR_AGUA.get(), trazadora = tipo == ModItems.TAMBOR_TRAZADOR.get(), tinta = tipo == ModItems.TAMBOR_TINTA.get();
        // el tambor de agua (Super Soaker): no hace daño salvo a lo que no aguanta el agua, y apaga el fuego
        float dano = agua ? 0F : EquilibrioArmas.thompsonDano() * (tinta ? 0.5F : 1F);
        var t = tiro(p, dano, alcance, 15, 0.6F, disp, 0.15F, DANO, true,
                agua ? net.minecraft.core.particles.ParticleTypes.SPLASH : tinta ? net.minecraft.core.particles.ParticleTypes.SQUID_INK
                        : trazadora || n % 3 == 0 ? net.minecraft.core.particles.ParticleTypes.CRIT : null);
        if (t.entidad() != null) {
            var v = t.entidad();
            if (trazadora) v.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 60, 0, false, false), p);
            if (agua) {
                boolean ardia = v.isOnFire();
                v.clearFire();
                if (v.isSensitiveToWater()) {
                    com.miscelanea.armasfuego.comun.Balistica.herir(v, p, 2.5F, 0F, DANO, true);
                    LogrosMiscelanea.dar(p, "thompson_agua");
                }
                if (ardia) LogrosMiscelanea.dar(p, "thompson_agua");
                v.push(p.getViewVector(1F).x * 0.08, 0.02, p.getViewVector(1F).z * 0.08);
                v.hurtMarked = true;
            }
        }
        if (agua) l.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, t.pos().x, t.pos().y, t.pos().z, 6, 0.15, 0.15, 0.15, 0.1);
        if (tinta) {
            // el tambor de tinta (Splatoon): ciega un momento y mancha de negro lo que se pueda teñir
            l.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK, t.pos().x, t.pos().y, t.pos().z, 4, 0.1, 0.1, 0.1, 0.05);
            if (t.entidad() != null) {
                t.entidad().addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 40, 0), p);
                LogrosMiscelanea.dar(p, "thompson_tinta");
            } else if (t.bloque() != null) {
                var bp = t.bloque().getBlockPos();
                var nuevo = com.miscelanea.armasfuego.rifle.Pintura.tenido(l.getBlockState(bp), net.minecraft.world.item.DyeColor.BLACK.getId());
                if (nuevo != null && p.mayBuild() && l.mayInteract(p, bp)) l.setBlock(bp, nuevo, 3);
            }
        }
        efectos(l, p, boca(p, 1.0, 0.25, 0.2), ModSounds.THOMPSON_DISPARO.get(), 3.0F, 0.95F, n % 2, true);
        LogrosMiscelanea.dar(p, "thompson_disparo");
        if (n >= EquilibrioArmas.thompsonCapacidad()) LogrosMiscelanea.dar(p, "thompson_tambor");
    }
}
