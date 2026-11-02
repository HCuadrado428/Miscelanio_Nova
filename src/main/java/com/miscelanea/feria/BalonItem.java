package com.miscelanea.feria;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Balón de canastas: mantén clic derecho para coger fuerza y suelta para tirar. Los balones de una máquina van
 * marcados con ella y con la partida: al tirarlos se gastan (vuelven rodando a la bandeja), si se sueltan al suelo
 * vuelven a la máquina y al acabar la partida desaparecen del inventario. Así no se pueden duplicar.
 * <p>Un balón sin marcar (del inventario creativo) es un balón de juguete: se tira, bota y no se gasta.
 */
public class BalonItem extends Item {
    public BalonItem(Properties p) {
        super(p);
    }

    /** Balón nuevo de la bandeja de una máquina, para la partida en curso. */
    public static ItemStack nuevo(MaquinaAltaBlockEntity be) {
        ItemStack st = new ItemStack(ModItems.BALON.get());
        CompoundTag t = st.getOrCreateTag();
        t.putLong("maq", be.getBlockPos().asLong());
        t.putLong("juego", be.inicio);
        return st;
    }

    static boolean marcado(ItemStack st) {
        return st.hasTag() && st.getTag().contains("maq");
    }

    /** ¿Es un balón de la máquina en esa posición? */
    static boolean deMaquina(ItemStack st, BlockPos pos) {
        return marcado(st) && st.getTag().getLong("maq") == pos.asLong();
    }

    /** Máquina y partida de un balón marcado, si siguen en marcha (y, si se da jugador, si es el suyo). */
    @Nullable
    static MaquinaAltaBlockEntity maquina(ItemStack st, Level l, @Nullable Player p) {
        if (!marcado(st)) return null;
        BlockPos pos = BlockPos.of(st.getTag().getLong("maq"));
        if (!l.isLoaded(pos) || !(l.getBlockEntity(pos) instanceof MaquinaAltaBlockEntity be)) return null;
        if (be.tipo() != MaquinaAltaBlock.CANASTA || !be.activo || be.inicio != st.getTag().getLong("juego")) return null;
        if (p != null && !p.getUUID().equals(be.jugador)) return null;
        return be;
    }

    @Override
    public int getUseDuration(ItemStack st) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack st) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public void releaseUsing(ItemStack st, Level l, LivingEntity e, int quedan) {
        if (!(e instanceof Player p) || l.isClientSide) return;
        float fuerza = Math.min(1, (getUseDuration(st) - quedan) / 20F);
        if (fuerza < 0.1F) return;
        MaquinaAltaBlockEntity be = maquina(st, l, p);
        if (marcado(st) && be == null) {
            // la partida ya no existe: el balón se queda en la máquina
            st.setCount(0);
            return;
        }
        Vec3 mira = p.getLookAngle();
        Vec3 sale = p.getEyePosition().add(mira.scale(0.3)).subtract(0, 0.1, 0);
        BalonEntity b = new BalonEntity(ModEntities.BALON.get(), l);
        b.setPos(sale.x, sale.y - BalonEntity.CENTRO, sale.z);
        // tiro suave en parábola: el aro está cerca y la jaula tiene techo, hay que medir la fuerza y el ángulo
        b.setDeltaMovement(mira.scale(0.12 + 0.3 * fuerza));
        if (be != null) {
            double[] a = MaquinaAltaBlockEntity.aro(l.getGameTime() - be.inicio);
            Vec3 aro = be.mundo(a[0], a[1], a[2]);
            b.preparar(be, be.inicio, Math.hypot(aro.x - p.getX(), aro.z - p.getZ()));
            // el balón sale de la mano: ya no está en el inventario (también en creativo, para no duplicarlo)
            st.shrink(1);
        }
        l.addFreshEntity(b);
        p.getCooldowns().addCooldown(this, 8);
    }

    /** Un balón de una partida que ya acabó (o de otro jugador) desaparece del inventario. */
    @Override
    public void inventoryTick(ItemStack st, Level l, Entity e, int slot, boolean enMano) {
        if (l.isClientSide || !marcado(st)) return;
        if (!(e instanceof Player p) || maquina(st, l, p) == null) st.setCount(0);
    }

    /** Soltado al suelo: vuelve a la bandeja de su máquina (nadie más puede cogerlo). */
    @Override
    public boolean onEntityItemUpdate(ItemStack st, ItemEntity ent) {
        if (!marcado(st)) return false;
        if (!ent.level().isClientSide) {
            MaquinaAltaBlockEntity be = maquina(st, ent.level(), null);
            if (be != null) be.devolverBalon(st.getTag().getLong("juego"));
            ent.discard();
        }
        return true;
    }
}
