package com.miscelanea.laboratorio;

import com.miscelanea.estatica.EstaticaRegistro;
import com.miscelanea.glitch.GlitchRegistro;
import com.miscelanea.glitch.GlitchTemporalBlock;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El escáner de anomalías: un Geiger para lo raro. Con él en la mano crepita más cuanto más cerca haya algo con glitch,
 * con estática o un fantasma (seres con esos efectos y bloques corruptos). Clic derecho = medir y apuntar la ficha.
 */
public class EscanerItem extends Item {
    public static final double RADIO = 24;

    public EscanerItem(Properties p) {
        super(p);
    }

    private static boolean anomalo(LivingEntity e) {
        return e.hasEffect(GlitchRegistro.EFECTO.get()) || e.hasEffect(EstaticaRegistro.EFECTO.get()) || e.hasEffect(EfectosRegistro.FANTASMA.get());
    }

    /** Intensidad (0 a 1) de lo que hay alrededor de {@code quien}: 1 pegado a algo raro, 0 si no hay nada a {@link #RADIO} bloques. */
    public static double intensidad(Level l, Entity quien) {
        double mejor = RADIO;
        int n = 0;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, quien.getBoundingBox().inflate(RADIO), x -> x != quien && anomalo(x))) {
            mejor = Math.min(mejor, e.distanceTo(quien));
            n++;
        }
        BlockPos c = quien.blockPosition();
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-10, -5, -10), c.offset(10, 5, 10))) {
            if (l.getBlockState(b).getBlock() instanceof GlitchTemporalBlock) {
                mejor = Math.min(mejor, Math.sqrt(b.distSqr(c)));
                n++;
            }
        }
        return n == 0 ? 0 : Math.max(0.05, 1 - mejor / RADIO);
    }

    @Override
    public void inventoryTick(ItemStack st, Level l, Entity e, int slot, boolean sel) {
        if (l.isClientSide || !(e instanceof ServerPlayer p) || !sel) return;
        if (p.tickCount % 4 != 0) return;
        // la intensidad se recalcula cada medio segundo (recorre un cubo de bloques)
        long t = l.getGameTime();
        CompoundTagCache c = CompoundTagCache.de(st);
        if (t % 10 == 0) c.intensidad = intensidad(l, p);
        if (c.intensidad > 0 && l.random.nextFloat() < 0.15 + c.intensidad * 0.85)
            p.playNotifySound(GlitchRegistro.CRUJIDO.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.25F + (float) c.intensidad * 0.6F, 0.8F + l.random.nextFloat() * 0.6F);
        if (t % 10 == 0) {
            int barras = (int) Math.round(c.intensidad * 10);
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.escaner.barra", "█".repeat(barras) + "░".repeat(10 - barras))
                    .withStyle(barras >= 7 ? ChatFormatting.RED : barras >= 3 ? ChatFormatting.YELLOW : ChatFormatting.GREEN), true);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
        if (!l.isClientSide) {
            double i = intensidad(l, p);
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.escaner.medida", Math.round(i * 100)).withStyle(ChatFormatting.AQUA), true);
            Map<String, String> lecturas = new LinkedHashMap<>();
            lecturas.put("anomalia", Math.round(i * 100) + " %");
            Experimentos.anotar(p, "escaner", null, 0, lecturas);
        }
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(h), l.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.translatable("item.miscelanea.escaner_anomalias.desc").withStyle(ChatFormatting.GRAY));
    }

    /** La última medida, guardada en el objeto entre tick y tick (no se guarda en disco). */
    private static final class CompoundTagCache {
        double intensidad;
        private static final java.util.WeakHashMap<ItemStack, CompoundTagCache> MAPA = new java.util.WeakHashMap<>();

        static CompoundTagCache de(ItemStack s) {
            return MAPA.computeIfAbsent(s, k -> new CompoundTagCache());
        }
    }
}
