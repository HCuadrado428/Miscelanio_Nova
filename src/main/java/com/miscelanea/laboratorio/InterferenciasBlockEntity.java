package com.miscelanea.laboratorio;

import com.miscelanea.estatica.EstaticaRegistro;
import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * LA SALA DE INTERFERENCIAS. Con lo del Hombre de estática: la señal se rompe, el sujeto oye ruidos que no están
 * (los de la estática, solo él) y tiene el efecto de estática; en intenso y extremo, además, miedo. El QTE es el de cortar
 * cables ("encuentra la señal"): si falla, la estática le dura un buen rato y, en el extremo, se hace daño.
 */
public class InterferenciasBlockEntity extends EstacionBlockEntity {
    public static final ResourceKey<DamageType> DANO = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(com.miscelanea.Miscelanea.MOD_ID, "interferencias"));
    private static final List<Supplier<SoundEvent>> RUIDOS = List.of(EstaticaRegistro.CRUJIDO, EstaticaRegistro.PARPADEO, EstaticaRegistro.TENTACULO,
            EstaticaRegistro.IMITACION, EstaticaRegistro.CHISPA_SONIDO, EstaticaRegistro.GOLPE);

    private int ruidos, proximoRuido;

    public InterferenciasBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.INTERFERENCIAS_BE.get(), p, s);
    }

    @Override protected String clave() { return "interferencias"; }
    @Override protected Item muestra() { return LabRegistro.MUESTRA_MIEDO.get(); }
    @Override protected int hastaQte() { return new int[]{260, 200, 150}[Math.min(nivel, 2)]; }
    @Override protected QteTipo tipoQte() { return QteTipo.CORTAR_CABLES; }
    @Override protected Component tituloQte() { return Component.translatable("qte.miscelanea.interferencias"); }
    @Override protected String resultadoCastigo() { return "estatica"; }

    @Override
    protected void alArrancar(ServerLevel sl) {
        ruidos = 0;
        proximoRuido = 40;
    }

    @Override
    protected void cadaTick(ServerLevel sl, LivingEntity v) {
        if (--proximoRuido > 0) return;
        ruidos++;
        proximoRuido = nivel == 2 ? 20 + sl.random.nextInt(30) : nivel == 1 ? 40 + sl.random.nextInt(50) : 70 + sl.random.nextInt(90);
        if (v instanceof ServerPlayer vp) vp.playNotifySound(RUIDOS.get(sl.random.nextInt(RUIDOS.size())).get(), SoundSource.AMBIENT, 0.9F, 0.8F + sl.random.nextFloat() * 0.4F);
    }

    @Override
    protected void efectos(ServerLevel sl, LivingEntity v) {
        v.addEffect(new MobEffectInstance(EstaticaRegistro.EFECTO.get(), 60, 0, false, false, true));
        if (nivel >= 1) v.addEffect(new MobEffectInstance(EfectosRegistro.MIEDO.get(), 60, nivel - 1, false, false, true));
    }

    @Override
    protected void castigo(ServerLevel sl, LivingEntity v) {
        v.addEffect(new MobEffectInstance(EstaticaRegistro.EFECTO.get(), 600, 0));
        v.addEffect(new MobEffectInstance(EfectosRegistro.MIEDO.get(), 300, nivel));
        if (nivel == 2 && LabConfig.danoExtremo() > 0) Aparatos.danar(v, DANO, LabConfig.danoExtremo() / 3F, captor);
        if (v instanceof ServerPlayer vp) vp.displayClientMessage(Component.translatable("laboratorio.miscelanea.interferencias.castigo").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    @Override
    protected void lecturasExtra(Map<String, String> m) {
        m.put("ruidos", String.valueOf(ruidos));
    }
}
