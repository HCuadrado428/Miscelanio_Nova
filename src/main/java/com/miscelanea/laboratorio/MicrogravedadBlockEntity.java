package com.miscelanea.laboratorio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * LA CÁMARA DE MICROGRAVEDAD. El sujeto flota dentro de la cabina (sube y baja despacio, a la deriva y dando vueltas), con
 * lentitud al caer y, en los niveles altos, mareo. El QTE es de alternar (A / D) para "recuperar el control". Si no lo
 * consigue: suave, se marea; intenso, vértigo; extremo, sale despedido hacia arriba y se hace daño al caer.
 */
public class MicrogravedadBlockEntity extends EstacionBlockEntity {
    public static final ResourceKey<DamageType> DANO = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(com.miscelanea.Miscelanea.MOD_ID, "microgravedad"));

    public MicrogravedadBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.MICRO_BE.get(), p, s);
    }

    @Override protected String clave() { return "microgravedad"; }
    @Override protected Item muestra() { return LabRegistro.MUESTRA_MAREO.get(); }
    @Override protected int hastaQte() { return new int[]{260, 200, 140}[Math.min(nivel, 2)]; }
    @Override protected QteTipo tipoQte() { return QteTipo.ALTERNAR; }
    @Override protected Component tituloQte() { return Component.translatable("qte.miscelanea.microgravedad"); }
    @Override protected String resultadoCastigo() { return new String[]{"mareo", "vertigo", "despedido"}[Math.min(nivel, 2)]; }

    /** Dónde flota el sujeto (sube y baja, deriva en círculo). */
    private Vec3 flota() {
        double t = level == null ? 0 : level.getGameTime();
        boolean sube = encendida;
        // dentro de la cápsula (2 bloques de alto) queda poco hueco por encima de la cabeza: flota un palmo y se mece
        double y = sube ? 0.15 + 0.045 * Math.sin(t * 0.07) : 0.1;
        double dx = sube ? 0.07 * Math.cos(t * 0.05) : 0, dz = sube ? 0.07 * Math.sin(t * 0.06) : 0;
        return new Vec3(worldPosition.getX() + 0.5 + dx, worldPosition.getY() + y, worldPosition.getZ() + 0.5 + dz);
    }

    @Override
    public Vec3 sitioVictima(LivingEntity v) {
        return flota();
    }

    @Nullable
    @Override
    public Vec3 sitioMovil(LivingEntity v) {
        return flota();
    }

    /** Da vueltas despacio mientras flota. */
    @Override
    public float yawVictima() {
        return encendida && level != null ? (float) ((level.getGameTime() * 4L) % 360L) : super.yawVictima();
    }

    @Override
    protected void efectos(ServerLevel sl, LivingEntity v) {
        v.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, false));
        if (nivel >= 1) v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, true));
        v.fallDistance = 0;
    }

    @Override
    protected void castigo(ServerLevel sl, LivingEntity v) {
        if (nivel == 0) {
            v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        } else if (nivel == 1) {
            v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0));
            v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 0));
            v.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
        } else {
            sacar(true);
            v.setDeltaMovement(0, 1.6, 0);
            v.hurtMarked = true;
            if (LabConfig.danoExtremo() > 0) Aparatos.danar(v, DANO, LabConfig.danoExtremo() / 2F, captor);
        }
        if (v instanceof ServerPlayer vp)
            vp.displayClientMessage(Component.translatable("laboratorio.miscelanea.microgravedad.castigo" + Math.min(nivel, 2)).withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    @Override
    protected void lecturasExtra(Map<String, String> m) {
        m.put("giros", String.valueOf(aguante * 4 / 360));
    }
}
