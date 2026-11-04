package com.miscelanea.bromas;

import com.miscelanea.ConfigServidor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Poción de controles cambiados en vuelo. Al chocar se rompe (cristales y salpicadura verde del juego) y a los jugadores
 * que pilla cerca les pone el efecto de controles cambiados: el que recibe el botellazo, entero; los demás, menos cuanto
 * más lejos (como las pociones arrojadizas normales).
 */
public class PocionControlesEntity extends ThrowableItemProjectile {
    public static final double RADIO = 4.0;

    public PocionControlesEntity(EntityType<? extends PocionControlesEntity> t, Level l) {
        super(t, l);
    }

    public PocionControlesEntity(Level l, LivingEntity quien) {
        super(BromasRegistro.POCION_LANZADA.get(), quien, l);
    }

    @Override
    protected Item getDefaultItem() {
        return BromasRegistro.POCION_CONTROLES.get();
    }

    @Override
    protected float getGravity() {
        return 0.05F;
    }

    @Override
    protected void onHit(HitResult r) {
        super.onHit(r);
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        Vec3 p = r.getLocation();
        sl.levelEvent(2002, BlockPos.containing(p), BromasRegistro.COLOR_CONTROLES);
        Entity directo = r instanceof EntityHitResult eh ? eh.getEntity() : null;
        int total = ConfigServidor.bromaControlesTicks();
        int n = 0;
        if (ConfigServidor.bromaControles()) {
            for (ServerPlayer v : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(p, p).inflate(RADIO, RADIO * 0.5, RADIO))) {
                if (v.isSpectator() || !v.isAffectedByPotions()) continue;
                double d = v.distanceToSqr(p);
                if (d >= RADIO * RADIO && v != directo) continue;
                double k = v == directo ? 1.0 : 1.0 - Math.sqrt(d) / RADIO;
                int t = (int) Math.max(100, total * k);
                v.addEffect(new MobEffectInstance(BromasRegistro.CONTROLES.get(), t, 0, false, false, true), getOwner());
                v.displayClientMessage(Component.translatable("msg.miscelanea.broma.controles.victima").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                n++;
            }
        }
        if (n > 0 && getOwner() instanceof ServerPlayer autor)
            autor.displayClientMessage(Component.translatable("msg.miscelanea.broma.controles.autor", n).withStyle(ChatFormatting.GOLD), true);
        discard();
    }
}
