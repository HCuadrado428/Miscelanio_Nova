package com.miscelanea.laboratorio;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.aparatos.AparatosRed;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Servidor → cliente: lo que enseña el visor de constantes de las gafas de seguridad. Se mide en el servidor (como el
 * pulsómetro) y se manda cada medio segundo a quien lleve las gafas: el cliente no sabe qué efectos tienen los demás (solo
 * los que tenían cuando empezó a verlos, y ahí se quedaban para siempre) ni si están atados.
 */
public class VisorPacket {
    /** {id de la entidad, pulso, miedo en %} de cada sujeto, del más cercano al más lejano. */
    public final List<int[]> sujetos;

    public VisorPacket(List<int[]> sujetos) {
        this.sujetos = sujetos;
    }

    public VisorPacket(FriendlyByteBuf b) {
        int n = Math.min(4, b.readVarInt());
        sujetos = new ArrayList<>(n);
        for (int i = 0; i < n; i++) sujetos.add(new int[]{b.readVarInt(), b.readVarInt(), b.readVarInt()});
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(sujetos.size());
        for (int[] s : sujetos) {
            b.writeVarInt(s[0]);
            b.writeVarInt(Math.max(0, s[1]));
            b.writeVarInt(Math.max(0, s[2]));
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.laboratorio.VisorConstantes.recibir(sujetos));
    }

    /** ¿Lo enseña el visor? Metido en un aparato, asustado o con un suero encima. */
    static boolean sujeto(LivingEntity e) {
        if (Aparatos.en(e) || Experimentos.miedo(e) > 0.1) return true;
        for (int i = 0; i < Sueros.TIPOS; i++) if (e.hasEffect(Sueros.efecto(i))) return true;
        return false;
    }

    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
    public static final class Envio {
        private Envio() {}

        @SubscribeEvent
        public static void tic(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
            if (!p.getItemBySlot(EquipmentSlot.HEAD).is(LabRegistro.GAFAS.get())) return;
            List<LivingEntity> cerca = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16),
                    x -> x != p && x.isAlive() && !x.isInvisible() && sujeto(x));
            cerca.sort(Comparator.comparingDouble(x -> x.distanceToSqr(p)));
            List<int[]> l = new ArrayList<>(4);
            for (LivingEntity x : cerca) {
                if (l.size() >= 4) break;
                l.add(new int[]{x.getId(), Experimentos.pulso(x), (int) Math.round(Experimentos.miedo(x) * 100)});
            }
            AparatosRed.a(p, new VisorPacket(l));
        }
    }
}
