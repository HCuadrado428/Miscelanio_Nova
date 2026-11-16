package com.miscelanea.armasfuego.selva;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Cerbatana de cobre, el arma de los templos de la jungla. Un solo dardo cada vez, se recarga en un momento y no hace
 * ruido: ni el sculk ni el Warden se enteran. Poco daño, pero los dardos llevan lo suyo: el normal (hierro), el de
 * veneno (la rana) y el somnífero, que deja al blanco lento y flojo y le hace olvidar quién le ha dado.
 */
public class CerbatanaItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("cerbatana_disparo");

    public CerbatanaItem(Properties p) {
        super(p);
    }

    @Override
    public int capacidad(ItemStack s) {
        return 1;
    }

    @Override
    public int ticksRecarga(ItemStack s) {
        return 14;
    }

    @Override
    protected boolean acepta(ItemStack m) {
        return m.is(ModItems.DARDO_CERBATANA.get()) || m.is(ModItems.DARDO_VENENO.get()) || m.is(ModItems.DARDO_SOMNIFERO.get());
    }

    @Override
    protected Item municionNormal() {
        return ModItems.DARDO_CERBATANA.get();
    }

    @Override
    protected boolean llenaDeGolpe() {
        return true;
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return 10;
    }

    @Override
    protected void disparar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        Item tipo = tipoActual(s);
        float dano = EquilibrioArmas.cerbatanaDano();
        if (tipo == ModItems.DARDO_VENENO.get()) dano *= 0.7F;
        else if (tipo == ModItems.DARDO_SOMNIFERO.get()) dano *= 0.35F;
        var t = tiro(p, dano, EquilibrioArmas.cerbatanaAlcance(), 20, 0.6F, 0.012, 0F, DANO, false, null);
        if (t.entidad() != null) {
            if (tipo == ModItems.DARDO_VENENO.get()) {
                t.entidad().addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0), p);
            } else if (tipo == ModItems.DARDO_SOMNIFERO.get()) {
                t.entidad().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 3), p);
                t.entidad().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0), p);
                if (t.entidad() instanceof Mob m) {
                    m.setTarget(null);
                    m.getNavigation().stop();
                }
            }
        }
        // un soplido y poco más; sin gameEvent, así que el sculk no lo nota
        var q = boca(p, 0.7, 0.25, 0.22);
        l.sendParticles(ParticleTypes.SMOKE, q.x, q.y, q.z, 3, 0.02, 0.02, 0.02, 0.005);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.SILENCIADOR_DISPARO.get(), SoundSource.PLAYERS, 0.5F, 1.5F + l.random.nextFloat() * 0.15F);
    }
}
