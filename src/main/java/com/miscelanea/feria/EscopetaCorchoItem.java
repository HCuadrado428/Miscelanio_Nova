package com.miscelanea.feria;

import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Escopeta de corcho de feria: clic derecho dispara un tapón (inofensivo) hacia donde miras. */
public class EscopetaCorchoItem extends Item {
    public EscopetaCorchoItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        p.getCooldowns().addCooldown(this, 7);
        Vec3 ojo = p.getEyePosition(), dir = p.getLookAngle();
        if (l.isClientSide) {
            for (int i = 1; i <= 8; i++) {
                Vec3 q = ojo.add(dir.scale(i * 0.6));
                l.addParticle(ParticleTypes.SMOKE, q.x, q.y - 0.1, q.z, 0, 0, 0);
            }
        } else {
            l.playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.FE_CORCHO.get(), SoundSource.PLAYERS, 0.8F, 0.9F + l.random.nextFloat() * 0.2F);
            CasetaTiroBlockEntity be = CasetaTiroBlockEntity.buscar(l, p);
            if (be != null) be.disparo(p, ojo, dir);
        }
        return InteractionResultHolder.sidedSuccess(st, l.isClientSide);
    }
}
