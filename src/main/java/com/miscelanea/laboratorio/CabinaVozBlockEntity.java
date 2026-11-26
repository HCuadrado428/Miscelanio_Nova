package com.miscelanea.laboratorio;

import com.miscelanea.tortura.qte.QteTipo;
import com.miscelanea.voz.VozRegistro;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * LA CABINA DE VOZ. Con Simple Voice Chat, al sujeto le distorsionan la voz mientras habla: helio (suave), tartamudeo y
 * ventilador (intenso) y borrachera con todo (extremo). El QTE es de círculo ("mantén el tono"). Si desafina, durante un
 * minuto tiene todos los efectos de voz de su nivel.
 */
public class CabinaVozBlockEntity extends EstacionBlockEntity {
    public CabinaVozBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.VOZ_BE.get(), p, s);
    }

    @Override protected String clave() { return "voz"; }
    @Override protected Item muestra() { return LabRegistro.MUESTRA_FRIO.get(); }
    @Override protected int hastaQte() { return new int[]{240, 200, 160}[Math.min(nivel, 2)]; }
    @Override protected QteTipo tipoQte() { return QteTipo.CIRCULO; }
    @Override protected Component tituloQte() { return Component.translatable("qte.miscelanea.voz"); }
    @Override protected String resultadoCastigo() { return "desafino"; }

    private void poner(LivingEntity v, int ticks) {
        v.addEffect(new MobEffectInstance(VozRegistro.HELIO.get(), ticks, 0, false, false, true));
        if (nivel >= 1) {
            v.addEffect(new MobEffectInstance(VozRegistro.TARTAMUDEO.get(), ticks, 0, false, false, true));
            v.addEffect(new MobEffectInstance(VozRegistro.VENTILADOR.get(), ticks, 0, false, false, true));
        }
        if (nivel >= 2) v.addEffect(new MobEffectInstance(VozRegistro.BORRACHERA.get(), ticks, 0, false, false, true));
    }

    @Override
    protected void efectos(ServerLevel sl, LivingEntity v) {
        poner(v, 60);
    }

    @Override
    protected void castigo(ServerLevel sl, LivingEntity v) {
        poner(v, 1200);
        if (v instanceof ServerPlayer vp) vp.displayClientMessage(Component.translatable("laboratorio.miscelanea.voz.castigo").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    @Override
    protected void lecturasExtra(Map<String, String> m) {
        m.put("efecto", new String[]{"helio", "tartamudeo", "borrachera"}[Math.min(nivel, 2)]);
    }
}
