package com.miscelanea.tortura.saw;

import com.miscelanea.tortura.ObjetoTortura;
import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.heridas.Parte;
import com.miscelanea.tortura.heridas.Tipo;
import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteResultado;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Tenazas oxidadas: clic derecho a un alambre de espino para cortarlo, despacio (minijuego de SERRAR, el vaivén de las
 * tenazas). Si se escapa la mano, arañazo y 1 de daño; si sale bien, el alambre se cae (y suelta el bloque).
 */
public class TenazasItem extends ObjetoTortura {
    public TenazasItem(Properties p) {
        super(p, 2);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (!c.getLevel().getBlockState(c.getClickedPos()).is(SawRegistro.ALAMBRE.get())) return InteractionResult.PASS;
        if (c.getPlayer() instanceof ServerPlayer sp && !Qte.activo(sp)) {
            BlockPos pos = c.getClickedPos();
            sp.serverLevel().playSound(null, pos, SawRegistro.ALAMBRE_RASGAR.get(), SoundSource.BLOCKS, 0.6F, 1.5F);
            QteParams params = QteParams.de(0.35F).titulo(Component.translatable("qte.miscelanea.titulo.tenazas")).ancla(Vec3.atCenterOf(pos), 4.5);
            Qte.iniciar(sp, QteTipo.SERRAR, params, (j, r) -> resultado(j, pos, r));
        }
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }

    private static void resultado(@Nullable ServerPlayer j, BlockPos pos, QteResultado r) {
        if (j == null || r == QteResultado.CANCELADO || !j.isAlive()) return;
        if (!j.serverLevel().getBlockState(pos).is(SawRegistro.ALAMBRE.get())) return;
        if (r.exito()) {
            j.serverLevel().playSound(null, pos, SawRegistro.ALAMBRE_CORTAR.get(), SoundSource.BLOCKS, 1.1F, 1F);
            j.serverLevel().destroyBlock(pos, !j.getAbilities().instabuild, j);
        } else {
            j.serverLevel().playSound(null, pos, SawRegistro.ALAMBRE_RASGAR.get(), SoundSource.BLOCKS, 1F, 1.3F);
            j.displayClientMessage(Component.translatable("msg.miscelanea.saw.tenazas.resbala").withStyle(ChatFormatting.RED), true);
            if (!j.isCreative()) {
                Aparatos.herida(j, j.getMainArm() == HumanoidArm.RIGHT ? Parte.BRAZO_DER : Parte.BRAZO_IZQ, Tipo.RASGUNO, 1);
                Aparatos.danar(j, SawRegistro.DANO_ALAMBRE, 1F, null);
            }
        }
    }
}
