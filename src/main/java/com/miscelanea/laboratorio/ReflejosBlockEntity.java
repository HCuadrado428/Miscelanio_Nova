package com.miscelanea.laboratorio;

import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.aparatos.AparatosRegistro;
import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * EL TEST DE REFLEJOS. Una serie de esquivas (A / D) seguidas: cinco, siete o nueve según el nivel. Cada fallo es una
 * descarga (1, 2 o 3 puntos de daño). Si acierta la mitad o más, termina bien; si no, "no tiene reflejos". Los mobs no
 * pueden pulsar, así que acierta o falla al azar.
 */
public class ReflejosBlockEntity extends EstacionBlockEntity {
    private int ronda, aciertos, descargas;
    private boolean enQte;
    private int siguienteEn;

    public ReflejosBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.REFLEJOS_BE.get(), p, s);
    }

    private int total() { return 5 + 2 * Math.min(nivel, 2); }

    @Override protected String clave() { return "reflejos"; }
    @Override protected Item muestra() { return LabRegistro.MUESTRA_CALOR.get(); }
    @Override protected int hastaQte() { return 0; }
    @Override protected Component tituloQte() { return Component.translatable("qte.miscelanea.reflejos"); }
    @Override protected String resultadoCastigo() { return "fallos"; }
    @Override protected void efectos(ServerLevel sl, LivingEntity v) {}
    @Override protected void castigo(ServerLevel sl, LivingEntity v) {}

    @Override
    protected void alArrancar(ServerLevel sl) {
        ronda = aciertos = descargas = 0;
        enQte = false;
        siguienteEn = 40;
    }

    private void descarga(ServerLevel sl, LivingEntity v) {
        descargas++;
        Aparatos.danar(v, AparatosRegistro.DANO_SILLA, 1F + Math.min(nivel, 2), captor);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, v.getX(), v.getY() + 1.0, v.getZ(), 14, 0.25, 0.4, 0.25, 0.1);
        sl.playSound(null, v.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.3F, 1.7F);
    }

    @Override
    protected void tickVictima(ServerLevel sl, LivingEntity v) {
        if (!encendida || terminado) return;
        tiempo++;
        aguante++;
        if (enQte || tiempo < siguienteEn) return;
        if (ronda >= total()) {
            // fin de la serie
            if (aciertos >= (total() + 1) / 2) terminarBien(sl);
            else fallar(sl, v);
            return;
        }
        if (v instanceof ServerPlayer vp) {
            if (Qte.activo(vp)) return;
            enQte = true;
            Qte.iniciar(vp, QteTipo.ESQUIVA, QteParams.de(0.3F + 0.2F * nivel).titulo(Component.translatable("qte.miscelanea.reflejos")), (pl, r) -> {
                if (r.exito()) aciertos++;
                else descarga(sl, v);
                ronda++;
                enQte = false;
                siguienteEn = tiempo + 25;
            });
        } else {
            // un mob: sin manos, acierta la mitad de las veces
            if (sl.random.nextBoolean()) aciertos++;
            else descarga(sl, v);
            ronda++;
            siguienteEn = tiempo + 20;
        }
    }

    @Override
    protected void lecturasExtra(Map<String, String> m) {
        m.put("aciertos", aciertos + "/" + total());
        m.put("descargas", String.valueOf(descargas));
    }

    @Override
    protected void guardarExtra(CompoundTag t) {
        t.putInt("Ronda", ronda);
    }

    @Override
    protected void cargarExtra(CompoundTag t) {
        ronda = t.getInt("Ronda");
    }
}
