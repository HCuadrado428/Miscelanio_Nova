package com.miscelanea.laboratorio;

import com.miscelanea.glitch.GlitchRegistro;
import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * LA CÁMARA DE CONTENCIÓN DE GLITCH. Se arranca con una muestra de glitch en la mano (se gasta): el sujeto queda con el
 * efecto de glitch y se le ve temblar y saltar de sitio dentro de la cabina, con partículas y chasquidos. El QTE es de
 * secuencia ("estabilízate"). Si lo consigue, el científico se lleva un suero de glitch; si no, el glitch le hace daño.
 * Lecturas: estabilidad (según los saltos) y saltos.
 */
public class ContencionBlockEntity extends EstacionBlockEntity {
    private int saltos;

    public ContencionBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.CONTENCION_BE.get(), p, s);
    }

    @Override protected String clave() { return "contencion"; }
    @Override protected Item muestra() { return LabRegistro.MUESTRA_MIEDO.get(); }
    @Override protected int hastaQte() { return new int[]{260, 200, 150}[Math.min(nivel, 2)]; }
    @Override protected QteTipo tipoQte() { return QteTipo.SECUENCIA; }
    @Override protected Component tituloQte() { return Component.translatable("qte.miscelanea.contencion"); }
    @Override protected String resultadoCastigo() { return "inestable"; }

    @Override
    protected boolean puedeArrancar(ServerLevel sl, Player p) {
        ItemStack mano = p.getMainHandItem();
        if (!mano.is(GlitchRegistro.MUESTRA.get())) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.contencion.sin_muestra").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!p.getAbilities().instabuild) mano.shrink(1);
        return true;
    }

    @Override
    protected void alArrancar(ServerLevel sl) {
        saltos = 0;
    }

    /** Desplazamiento del sujeto (de -0.2 a 0.2 bloques), distinto cada tres ticks y el mismo en servidor y cliente. */
    private Vec3 temblor() {
        if (!encendida || level == null) return Vec3.ZERO;
        RandomSource r = RandomSource.create(level.getGameTime() / 3L * 7919L + worldPosition.asLong());
        double a = 0.1 + 0.06 * nivel;
        return new Vec3((r.nextDouble() - 0.5) * 2 * a, (r.nextDouble() - 0.3) * a, (r.nextDouble() - 0.5) * 2 * a);
    }

    @Override
    public Vec3 sitioVictima(LivingEntity v) {
        Vec3 t = temblor();
        return new Vec3(worldPosition.getX() + 0.5 + t.x, worldPosition.getY() + 0.05 + Math.max(0, t.y), worldPosition.getZ() + 0.5 + t.z);
    }

    @Nullable
    @Override
    public Vec3 sitioMovil(LivingEntity v) {
        return sitioVictima(v);
    }

    @Override
    protected void cadaTick(ServerLevel sl, LivingEntity v) {
        if (sl.getGameTime() % 3 == 0) saltos++;
        if (sl.getGameTime() % 4 == 0) {
            Vec3 c = centro();
            sl.sendParticles(GlitchRegistro.PARTICULA.get(), c.x, c.y + 0.9, c.z, 3, 0.3, 0.5, 0.3, 0.0);
        }
        if (sl.getGameTime() % 30 == 0)
            sl.playSound(null, worldPosition, (sl.random.nextBoolean() ? GlitchRegistro.CRUJIDO : GlitchRegistro.SISEO).get(), SoundSource.BLOCKS, 1.0F, 0.8F + sl.random.nextFloat() * 0.5F);
    }

    @Override
    protected void efectos(ServerLevel sl, LivingEntity v) {
        v.addEffect(new MobEffectInstance(GlitchRegistro.EFECTO.get(), 60, 0, false, false, true));
    }

    @Override
    protected void castigo(ServerLevel sl, LivingEntity v) {
        v.addEffect(new MobEffectInstance(GlitchRegistro.EFECTO.get(), 300, 0));
        Aparatos.danar(v, GlitchRegistro.DANO, 2F + 2F * Math.min(nivel, 2), captor);
        if (v instanceof ServerPlayer vp) vp.displayClientMessage(Component.translatable("laboratorio.miscelanea.contencion.castigo").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    @Override
    protected void cerrarFicha(ServerLevel sl, String resultado) {
        super.cerrarFicha(sl, resultado);
        if (resultado.equals("aguanto") && captor != null) {
            Player c = sl.getServer().getPlayerList().getPlayer(captor);
            if (c != null) {
                ItemStack suero = Sueros.crear(9, false);   // suero de glitch
                if (!c.getInventory().add(suero)) c.drop(suero, false);
                c.displayClientMessage(Component.translatable("laboratorio.miscelanea.contencion.suero").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
    }

    @Override
    protected void lecturasExtra(Map<String, String> m) {
        m.put("estabilidad", Math.max(0, 100 - saltos / 3) + " %");
        m.put("saltos", String.valueOf(saltos));
    }
}
