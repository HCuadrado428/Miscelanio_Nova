package com.miscelanea.tortura.saw;

import com.miscelanea.tortura.Cautivos;
import com.miscelanea.tortura.LlaveTrampaItem;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.aparatos.Aparatos;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import com.miscelanea.tortura.heridas.Parte;
import com.miscelanea.tortura.heridas.Tipo;
import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteResultado;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * EL POZO DE AGUJAS (Saw II). Un pozo (de cualquier tamaño) relleno de bloques {@link PozoAgujasBlock}.
 * <ul>
 *   <li>El captor esconde una llave de la trampa (la de una trampa concreta o la maestra) con clic derecho a un bloque del
 *       pozo. Si nadie la esconde, al primer registro aparece en un bloque al azar del pozo (la llave de la trampa de quien
 *       busca, si lleva una; si no, una llave oxidada que también abre la {@link SalidaBlock} cerrada).</li>
 *   <li>Quien está DENTRO del pozo registra un bloque (a 2,5 bloques o menos) con clic derecho: un CIRCULO rápido. Fallarlo
 *       es un pinchazo seguro; acertarlo también puede pinchar: cuanto menos espere entre registros, más (de
 *       {@link #PROB_MIN} a {@link #PROB_MAX}).</li>
 *   <li>Pinchazo: veneno, náusea, arañazos/cortes en brazos y piernas, 1 de daño "pozo_agujas" y el chasquido de la aguja.
 *       Desde el {@link #ALUCINAR}º pinchazo, alucinaciones (la toxina del miedo, poco rato). La cuenta se olvida a los 2 min.</li>
 *   <li>Caer al pozo desde más de un bloque también pincha.</li>
 * </ul>
 */
public final class Pozo {
    private Pozo() {}

    public static final float PROB_MIN = 0.12F, PROB_MAX = 0.65F;
    public static final int ALUCINAR = 3, OLVIDO = 2400, MAX_POZO = 512, LLAVE_OXIDADA = 99999;

    /** Clic derecho a un bloque del pozo (servidor). */
    public static void usar(ServerPlayer p, BlockPos pos, ItemStack st) {
        ServerLevel sl = p.serverLevel();
        if (st.getItem() instanceof LlaveTrampaItem) {
            PozoLlaves.de(sl).esconder(pos, st.copyWithCount(1));
            if (!p.getAbilities().instabuild) st.shrink(1);
            sl.playSound(null, pos, SawRegistro.POZO_BUSCAR.get(), SoundSource.BLOCKS, 0.8F, 1.2F);
            p.displayClientMessage(Component.translatable("msg.miscelanea.saw.pozo.escondida").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (!dentro(p)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.saw.pozo.fuera").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (p.position().distanceTo(Vec3.atCenterOf(pos)) > 2.6 || Qte.activo(p)) return;
        CompoundTag d = Saw.leer(p);
        if (buscado(d, pos)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.saw.pozo.ya").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        PozoLlaves llaves = PozoLlaves.de(sl);
        asegurarLlave(sl, llaves, pos);
        long ahora = sl.getGameTime();
        long dt = d.contains("UltimaBusqueda") ? ahora - d.getLong("UltimaBusqueda") : 1000;
        float prisa = Mth.clamp(1F - (dt - 10F) / 60F, 0F, 1F);
        float prob = PROB_MIN + (PROB_MAX - PROB_MIN) * prisa;
        d.putLong("UltimaBusqueda", ahora);
        if (!d.getBoolean("PozoSaludo")) {
            d.putBoolean("PozoSaludo", true);
            Saw.escribir(p, d);
            Saw.saludo(p, "pozo");
        } else Saw.escribir(p, d);
        sl.playSound(null, pos, SawRegistro.POZO_BUSCAR.get(), SoundSource.BLOCKS, 1F, 0.9F + p.getRandom().nextFloat() * 0.2F);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sl.getBlockState(pos)), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                6, 0.25, 0.1, 0.25, 0.05);
        QteParams params = QteParams.de(0.3F + 0.4F * prisa).rondas(1)
                .titulo(Component.translatable("qte.miscelanea.titulo.pozo")).ancla(Vec3.atCenterOf(pos), 4.0);
        Qte.iniciar(p, QteTipo.CIRCULO, params, (j, r) -> resultado(j, pos, r, prob));
    }

    private static void resultado(@Nullable ServerPlayer j, BlockPos pos, QteResultado r, float prob) {
        if (j == null || r == QteResultado.CANCELADO || !j.isAlive()) return;
        ServerLevel sl = j.serverLevel();
        if (!r.exito() || j.getRandom().nextFloat() < prob) pinchazo(j);
        if (!j.isAlive() || !r.exito()) return;
        if (!sl.getBlockState(pos).is(SawRegistro.POZO.get())) return;
        PozoLlaves llaves = PozoLlaves.de(sl);
        ItemStack st = llaves.hay(pos) ? llaves.sacar(pos) : null;
        if (st != null) {
            if (st.isEmpty()) st = llaveAlAzar(j);
            ItemHandlerHelper.giveItemToPlayer(j, st);
            sl.playSound(null, pos, SawRegistro.POZO_LLAVE.get(), SoundSource.PLAYERS, 1.3F, 1F);
            Saw.titulo(j, Component.translatable("msg.miscelanea.saw.pozo.llave").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.translatable("msg.miscelanea.saw.pozo.llave_sub").withStyle(ChatFormatting.GRAY), 40);
            CompoundTag d = Saw.leer(j);
            d.remove("Buscados");
            Saw.escribir(j, d);
            return;
        }
        CompoundTag d = Saw.leer(j);
        ListTag l = d.getList("Buscados", Tag.TAG_LONG);
        if (l.size() >= 256) l.remove(0);
        l.add(LongTag.valueOf(pos.asLong()));
        d.put("Buscados", l);
        Saw.escribir(j, d);
        j.displayClientMessage(Component.translatable("msg.miscelanea.saw.pozo.nada").withStyle(ChatFormatting.GRAY), true);
    }

    /** ¿Está metido en el pozo? (los pies dentro de un bloque del pozo). */
    public static boolean dentro(LivingEntity e) {
        BlockPos b = e.blockPosition();
        return e.level().getBlockState(b).is(SawRegistro.POZO.get()) || e.level().getBlockState(b.below()).is(SawRegistro.POZO.get())
                && e.getY() - b.getY() < 0.05;
    }

    private static boolean buscado(CompoundTag d, BlockPos p) {
        ListTag l = d.getList("Buscados", Tag.TAG_LONG);
        long v = p.asLong();
        for (int i = 0; i < l.size(); i++) if (((LongTag) l.get(i)).getAsLong() == v) return true;
        return false;
    }

    /** Si en todo el pozo (los bloques conectados) no hay ninguna llave, se esconde una al azar. */
    private static void asegurarLlave(ServerLevel sl, PozoLlaves llaves, BlockPos desde) {
        List<BlockPos> pozo = conectados(sl, desde);
        for (BlockPos b : pozo) if (llaves.hay(b)) return;
        llaves.azar(pozo.get(sl.getRandom().nextInt(pozo.size())));
    }

    public static List<BlockPos> conectados(ServerLevel sl, BlockPos desde) {
        List<BlockPos> out = new ArrayList<>();
        Set<Long> vistos = new HashSet<>();
        ArrayDeque<BlockPos> cola = new ArrayDeque<>();
        cola.add(desde);
        vistos.add(desde.asLong());
        while (!cola.isEmpty() && out.size() < MAX_POZO) {
            BlockPos b = cola.poll();
            out.add(b);
            for (Direction dir : Direction.values()) {
                BlockPos n = b.relative(dir);
                if (vistos.add(n.asLong()) && sl.isLoaded(n) && sl.getBlockState(n).is(SawRegistro.POZO.get())) cola.add(n);
            }
        }
        return out;
    }

    /** La llave "al azar": la de su trampa de oso si lleva una; si no, una llave oxidada. */
    private static ItemStack llaveAlAzar(ServerPlayer j) {
        ItemStack k = new ItemStack(TorturaRegistro.LLAVE_TRAMPA.get());
        if (Cautivos.conTrampa(j)) k.getOrCreateTag().putInt("Trampa", Cautivos.leer(j).getInt("TrampaId"));
        else {
            k.getOrCreateTag().putInt("Trampa", LLAVE_OXIDADA);
            k.setHoverName(Component.translatable("item.miscelanea.llave_oxidada").withStyle(s -> s.withItalic(false)));
        }
        return k;
    }

    // ------------------------------------------------------------------ pinchazos
    public static void pinchazo(LivingEntity v) {
        if (!(v.level() instanceof ServerLevel sl) || !v.isAlive()) return;
        if (v instanceof Player pl && (pl.isCreative() || pl.isSpectator())) return;
        CompoundTag d = Saw.leer(v);
        long ahora = sl.getGameTime();
        int n = (ahora - d.getLong("PinchazoT") > OLVIDO ? 0 : d.getInt("Pinchazos")) + 1;
        d.putInt("Pinchazos", n);
        d.putLong("PinchazoT", ahora);
        Saw.escribir(v, d);
        sl.playSound(null, v.getX(), v.getY() + 0.5, v.getZ(), SawRegistro.POZO_PINCHAZO.get(), SoundSource.PLAYERS, 1F, 0.9F + v.getRandom().nextFloat() * 0.25F);
        v.addEffect(new MobEffectInstance(MobEffects.POISON, 60 + 10 * Math.min(n, 6), 0));
        v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120 + 20 * Math.min(n, 6), 0));
        boolean derecha = v.getRandom().nextBoolean();
        Aparatos.herida(v, derecha ? Parte.BRAZO_DER : Parte.BRAZO_IZQ, v.getRandom().nextInt(3) == 0 ? Tipo.CORTE : Tipo.RASGUNO, 1);
        if (v.getRandom().nextBoolean())
            Aparatos.herida(v, v.getRandom().nextBoolean() ? Parte.PIERNA_DER : Parte.PIERNA_IZQ, Tipo.RASGUNO, 1);
        if (n >= ALUCINAR) v.addEffect(new MobEffectInstance(EfectosRegistro.MIEDO.get(), Math.min(320, 100 + 40 * (n - ALUCINAR)), 0));
        Aparatos.danar(v, SawRegistro.DANO_POZO, 1F, null);
        if (v instanceof ServerPlayer sp && v.isAlive())
            sp.displayClientMessage(Component.translatable(n >= ALUCINAR ? "msg.miscelanea.saw.pozo.pinchazo_mal" : "msg.miscelanea.saw.pozo.pinchazo", n)
                    .withStyle(ChatFormatting.DARK_RED), true);
    }
}
