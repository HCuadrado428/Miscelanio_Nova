package com.miscelanea.tortura.aparatos;

import com.miscelanea.tortura.heridas.Parte;
import com.miscelanea.tortura.heridas.Tipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Potro (la mesa de estirar de Dungeon Keeper). La víctima va tumbada boca arriba, atada de muñecas (sobre la cabeza, en
 * la cabecera) y tobillos (en los pies, junto a la manivela).
 * <p>
 * Girar la manivela AL COMPÁS: cada repetición del clic derecho mantenido (cada 4 ticks) es media vuelta, que sube la
 * AGUJA del ritmo; la aguja baja sola. Solo estira cuando se gira con la aguja en la franja verde
 * ({@link #VERDE_MIN}..{@link #VERDE_MAX}); si se pasa de {@link #ROJO} (demasiado deprisa) la víctima se desmaya y se
 * pierde parte de lo ganado. Todo se calcula en el servidor (la aguja no la manda el cliente).
 * <ul>
 *   <li>Jugadores: el estiramiento (tensión) deja moratones en brazos y piernas y hace daño.</li>
 *   <li>Mobs: barra de CONVERSIÓN sobre la cabeza; al 100% se vuelven seguidores del captor ({@link Conversos}).</li>
 * </ul>
 */
public class PotroBlockEntity extends AparatoBlockEntity {
    public static final float VERDE_MIN = 0.45F, VERDE_MAX = 0.75F, ROJO = 0.9F;
    public static final float SUBE = 0.10F, BAJA = 0.010F;
    /**
     * Medidas de la mesa (bloques): altura de las tablas, cuánto se corre el centro de la mesa hacia la cabecera (la mesa
     * mide 2,5 bloques: sobresale medio por los pies) y dónde quedan los pies tumbados respecto al centro.
     * Un JUGADOR tumbado tiene su posición (la de la cámara menos la altura de los ojos) bajo la mesa, para que la cámara
     * quede en su cabeza tumbada: OJOS_ATRAS desde los pies hacia la cabecera y OJOS_ALTO sobre las tablas. El cliente
     * lo tumba al dibujarlo (AparatosCliente) con ALZA de holgura bajo la espalda.
     */
    public static final double SUPERFICIE = 11 / 16.0, CENTRO_ATRAS = 0.25, PIES = 1.105, OJOS_ATRAS = 1.62, OJOS_ALTO = 0.434;
    public static final float ALZA = 0.2F;
    private float tension, aguja, giro, conversion;
    private int desmayo, tramo;
    /** Cliente: valores que se dibujan (suaves). */
    public float tensionVis, tensionVisO, giroVis, giroVisO, agujaVis;

    public PotroBlockEntity(BlockPos p, BlockState s) {
        super(AparatosRegistro.POTRO_BE.get(), p, s);
    }

    @Override
    public int tipo() {
        return AparatosVictimas.POTRO;
    }

    public float tension() {
        return tension;
    }

    public float aguja() {
        return aguja;
    }

    public float conversion() {
        return conversion;
    }

    public boolean desmayado() {
        return desmayo > 0;
    }

    /** Vector de la cabecera a los pies (hacia el frente). */
    public Vec3 eje() {
        Direction f = frente();
        return new Vec3(f.getStepX(), 0, f.getStepZ());
    }

    /** Centro de la mesa, a ras de suelo. */
    public Vec3 centroMesa() {
        return centro().subtract(eje().scale(CENTRO_ATRAS));
    }

    @Override
    public Vec3 sitioVictima(LivingEntity v) {
        Vec3 c = centroMesa(), l = eje();
        double top = worldPosition.getY() + SUPERFICIE;
        if (v instanceof Player) {
            Vec3 ojos = c.add(l.scale(PIES - OJOS_ATRAS));
            return new Vec3(ojos.x, top + OJOS_ALTO - OJOS_ATRAS, ojos.z);
        }
        double h = Math.min(2.2, v.getBbHeight());
        Vec3 pies = c.add(l.scale(h / 2));
        return new Vec3(pies.x, top, pies.z);
    }

    @Override
    public boolean sentado() {
        return false;
    }

    @Override
    public float giroCabeza() {
        return 50F;
    }

    @Override
    protected void usarExtra(Player p, boolean anexo, BlockHitResult hit) {
        if (!ocupado()) {
            girarVacio();
            return;
        }
        if (!puedeManejar(p)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.aparato.no_tuyo").withStyle(ChatFormatting.RED), true);
            return;
        }
        girar(p);
    }

    private void girarVacio() {
        giro = (giro + 24F) % 3600F;
        sonido(AparatosRegistro.POTRO_CARRACA.get(), 0.6F, 1.1F);
        marcar();
    }

    private void girar(Player p) {
        if (!(level instanceof ServerLevel sl)) return;
        LivingEntity v = victimaEntidad();
        if (v == null) return;
        giro = (giro + 24F) % 3600F;
        sonido(AparatosRegistro.POTRO_CARRACA.get(), 0.7F, 0.9F + aguja * 0.4F);
        if (desmayo > 0) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.potro.desmayado", v.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            marcar();
            return;
        }
        boolean verde = aguja >= VERDE_MIN && aguja <= VERDE_MAX;
        aguja = Math.min(1F, aguja + SUBE);
        if (aguja > ROJO) {
            desmayar(sl, v, p);
            marcar();
            return;
        }
        if (verde) estirar(sl, v, p);
        marcar();
    }

    private void estirar(ServerLevel sl, LivingEntity v, Player p) {
        tension = Math.min(1F, tension + 0.02F);
        sl.playSound(null, v.getX(), v.getY() + 0.5, v.getZ(), AparatosRegistro.POTRO_CRUJIDO.get(), SoundSource.BLOCKS,
                0.5F + tension * 0.5F, 0.8F + tension * 0.3F + v.getRandom().nextFloat() * 0.1F);
        if (v instanceof Player) {
            int t = (int) (tension * 10);
            boolean tramoNuevo = t > tramo || (tension >= 1F && ++tramo % 5 == 0);
            if (t > tramo) tramo = t;
            if (tramoNuevo) {
                Parte[] ext = {Parte.BRAZO_DER, Parte.BRAZO_IZQ, Parte.PIERNA_DER, Parte.PIERNA_IZQ};
                Parte parte = ext[v.getRandom().nextInt(ext.length)];
                Aparatos.herida(v, parte, Tipo.MORATON, 1 + (tension > 0.5F ? 1 : 0) + (tension > 0.85F ? 1 : 0));
                Aparatos.danar(v, AparatosRegistro.DANO_POTRO, 0.8F + tension * 2F, captor);
            }
            p.displayClientMessage(Component.translatable("msg.miscelanea.potro.estirando", Math.round(tension * 100)).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        // mobs: conversión (los que tienen más vida aguantan más)
        float resistencia = Mth.clamp(v.getMaxHealth() / 20F, 0.6F, 3F);
        conversion = Math.min(1F, conversion + 0.025F / resistencia);
        if (v.getHealth() > 4F && v.getRandom().nextInt(4) == 0) Aparatos.danar(v, AparatosRegistro.DANO_POTRO, 1F, null);
        sl.sendParticles(ParticleTypes.ENCHANT, v.getX(), v.getY() + 1.0, v.getZ(), 6, 0.4, 0.3, 0.4, 0.4);
        p.displayClientMessage(Component.translatable("msg.miscelanea.potro.convirtiendo", v.getDisplayName(), Math.round(conversion * 100))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        if (conversion >= 1F && v instanceof Mob m && p instanceof ServerPlayer sp) convertir(sl, m, sp);
    }

    private void desmayar(ServerLevel sl, LivingEntity v, Player p) {
        desmayo = 60;
        aguja = 0.3F;
        tension = Math.max(0F, tension - 0.15F);
        tramo = (int) (tension * 10);
        conversion = Math.max(0F, conversion - 0.12F);
        sl.playSound(null, v.getX(), v.getY() + 0.5, v.getZ(), AparatosRegistro.POTRO_CRUJIDO.get(), SoundSource.BLOCKS, 1.2F, 0.55F);
        p.displayClientMessage(Component.translatable("msg.miscelanea.potro.rapido", v.getDisplayName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        if (v instanceof ServerPlayer sp) {
            AparatosRed.a(sp, new AparatoEfectoPacket(sp.getId(), AparatoEfectoPacket.DESMAYO, 60));
            sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false));
            sp.displayClientMessage(Component.translatable("msg.miscelanea.potro.te_desmayas").withStyle(ChatFormatting.DARK_GRAY), true);
        } else {
            sl.sendParticles(ParticleTypes.POOF, v.getX(), v.getY() + 0.6, v.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
        }
    }

    private void convertir(ServerLevel sl, Mob m, ServerPlayer duenno) {
        sacar(false);
        Conversos.convertir(m, duenno);
        sl.playSound(null, m.getX(), m.getY() + 1, m.getZ(), AparatosRegistro.POTRO_CONVERSION.get(), SoundSource.NEUTRAL, 1.2F, 1F);
        sl.sendParticles(ParticleTypes.HEART, m.getX(), m.getY() + m.getBbHeight() + 0.2, m.getZ(), 8, 0.4, 0.2, 0.4, 0.1);
        sl.sendParticles(ParticleTypes.WITCH, m.getX(), m.getY() + 1, m.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
        duenno.displayClientMessage(Component.translatable("msg.miscelanea.potro.convertido", m.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
    }

    @Override
    protected void tickAparato(ServerLevel sl) {
        if (aguja > 0F) {
            aguja = Math.max(0F, aguja - BAJA);
            if (sl.getGameTime() % 4 == 0) marcar();
        }
        if (desmayo > 0 && --desmayo == 0) marcar();
        if (!ocupado() && tension > 0F) {
            tension = Math.max(0F, tension - 0.05F);
            marcar();
        }
    }

    @Override
    protected void tickVictima(ServerLevel sl, LivingEntity v) {
        // estirado al máximo: el cuerpo cruje de vez en cuando
        if (tension > 0.6F && sl.getGameTime() % 50 == 0)
            sl.playSound(null, v.getX(), v.getY() + 0.5, v.getZ(), AparatosRegistro.POTRO_CRUJIDO.get(), SoundSource.BLOCKS, 0.4F, 0.6F);
    }

    @Override
    protected void alMeter(LivingEntity v, @Nullable Player quien) {
        tension = 0F;
        aguja = 0F;
        conversion = 0F;
        desmayo = 0;
        tramo = 0;
    }

    @Override
    protected void alSacar(@Nullable LivingEntity v) {
        conversion = 0F;
        desmayo = 0;
        aguja = 0F;
        tramo = 0;
    }

    @Override
    protected void tickVisual() {
        tensionVisO = tensionVis;
        tensionVis += (tension - tensionVis) * 0.3F;
        giroVisO = giroVis;
        giroVis += (giro - giroVis) * 0.4F;
        if (Math.abs(giro - giroVis) > 720F) giroVis = giroVisO = giro;
        // la aguja baja sola entre sincronizaciones
        agujaVis = Math.abs(aguja - agujaVis) > 0.06F ? aguja : Math.max(0F, agujaVis - BAJA);
        if (aguja > agujaVis) agujaVis = aguja;
    }

    @Override
    protected void guardar(CompoundTag t) {
        t.putFloat("Tension", tension);
        t.putFloat("Aguja", aguja);
        t.putFloat("Giro", giro);
        t.putFloat("Conversion", conversion);
        t.putInt("Desmayo", desmayo);
        t.putInt("Tramo", tramo);
    }

    @Override
    protected void cargar(CompoundTag t) {
        tension = t.getFloat("Tension");
        aguja = t.getFloat("Aguja");
        giro = t.getFloat("Giro");
        conversion = t.getFloat("Conversion");
        desmayo = t.getInt("Desmayo");
        tramo = t.getInt("Tramo");
    }
}
