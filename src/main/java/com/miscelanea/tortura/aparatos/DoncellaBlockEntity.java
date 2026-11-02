package com.miscelanea.tortura.aparatos;

import com.miscelanea.armas.TemblorPacket;
import com.miscelanea.tortura.heridas.Parte;
import com.miscelanea.tortura.heridas.Tipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Doncella de hierro. Las puertas (0 abiertas .. 1 cerradas) solo se cierran mientras el captor MANTIENE clic derecho
 * sobre ella (cada repetición del clic, cada 4 ticks, las empuja {@link #PASO}); al soltar se quedan donde estén.
 * Sin nadie dentro, un clic las abre o las cierra del todo (de adorno).
 * <p>
 * Dentro, la víctima (jugador) juega APARTARSE DE LOS PINCHOS: mantener su marca dentro de una zona segura que se mueve
 * y que se estrecha según se cierran las puertas (cliente: JuegoDoncella). Cada 10 ticks manda cuántos ha pasado fuera;
 * el tiempo fuera = daño y cortes en la skin. Si deja de informar, cuenta como fuera. Con las puertas cerradas del todo
 * no hay hueco: los pinchos entran. Los mobs "juegan" al azar.
 */
public class DoncellaBlockEntity extends AparatoBlockEntity {
    public static final float PASO = 0.032F;
    private float puertas, objetivo;
    private int fueraAcum;
    private long ultimoInforme;
    private boolean juego;
    private long semilla = 1;
    /** Cliente: apertura que se dibuja (se acerca suave a la del servidor). */
    public float vis, visO;

    public DoncellaBlockEntity(BlockPos p, BlockState s) {
        super(AparatosRegistro.DONCELLA_BE.get(), p, s);
    }

    @Override
    public int tipo() {
        return AparatosVictimas.DONCELLA;
    }

    public float puertas() {
        return puertas;
    }

    @Override
    public Vec3 sitioVictima(LivingEntity v) {
        return centro().add(0, 1 / 16.0, 0);
    }

    @Override
    public boolean sentado() {
        return false;
    }

    @Override
    public float giroCabeza() {
        return 60F;
    }

    /** Ancho (medio) de la zona segura del minijuego según lo cerradas que estén las puertas (0..1 de la barra). */
    public static float zonaMedia(float puertas) {
        return 0.24F - 0.17F * Math.min(1F, puertas);
    }

    @Override
    protected void usarExtra(Player p, boolean anexo, BlockHitResult hit) {
        if (!ocupado()) {
            objetivo = puertas > 0.5F ? 0F : 1F;
            sonido(AparatosRegistro.DONCELLA_CHIRRIDO.get(), 0.8F, 0.9F + p.getRandom().nextFloat() * 0.2F);
            marcar();
            return;
        }
        if (!puedeManejar(p)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.aparato.no_tuyo").withStyle(ChatFormatting.RED), true);
            return;
        }
        cerrar(p);
    }

    private void cerrar(Player p) {
        if (puertas >= 1F) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.doncella.cerrada").withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        float antes = puertas;
        puertas = Math.min(1F, puertas + PASO);
        objetivo = puertas;
        if (level != null && level.getGameTime() % 8 < 4)
            sonido(AparatosRegistro.DONCELLA_CHIRRIDO.get(), 0.55F, 0.65F + puertas * 0.35F + p.getRandom().nextFloat() * 0.1F);
        if (antes < 1F && puertas >= 1F) portazo();
        p.displayClientMessage(Component.translatable("msg.miscelanea.doncella.cerrando", Math.round(puertas * 100)).withStyle(ChatFormatting.GOLD), true);
        marcar();
    }

    private void portazo() {
        sonido(AparatosRegistro.DONCELLA_CIERRE.get(), 1.3F, 0.9F);
        if (level instanceof ServerLevel sl) TemblorPacket.enviar(sl, centro().add(0, 1, 0), 10, 1.2F, 8);
    }

    @Override
    protected void tickAparato(ServerLevel sl) {
        if (!ocupado() && Math.abs(puertas - objetivo) > 1.0E-4F) {
            float antes = puertas;
            puertas = puertas < objetivo ? Math.min(objetivo, puertas + 0.06F) : Math.max(objetivo, puertas - 0.06F);
            if (antes < 1F && puertas >= 1F) portazo();
            marcar();
        }
    }

    @Override
    protected void tickVictima(ServerLevel sl, LivingEntity v) {
        ServerPlayer sp = v instanceof ServerPlayer x ? x : null;
        if (sp != null) {
            boolean deberia = puertas >= 0.2F;
            if (deberia && !juego) empezarJuego(sp);
            else if (!deberia && juego) pararJuego(sp);
        }
        if (puertas < 0.2F || sl.getGameTime() % 10 != 0) return;
        int fuera;
        if (sp != null) {
            fuera = Math.min(10, fueraAcum);
            fueraAcum = 0;
            if (sl.getGameTime() - ultimoInforme > 30) fuera = 10;   // sin informes: no se aparta
        } else {
            fuera = v.getRandom().nextFloat() < 0.2F + 0.6F * puertas ? 3 + v.getRandom().nextInt(8) : 0;
        }
        if (puertas >= 0.98F) fuera = 10;
        if (fuera <= 0) return;
        float dano = fuera / 10F * (0.5F + 2F * puertas * puertas);
        Aparatos.danar(v, AparatosRegistro.DANO_DONCELLA, dano, captor);
        if (fuera >= 4) {
            Parte parte = Parte.V[v.getRandom().nextInt(Parte.V.length)];
            Aparatos.herida(v, parte, Tipo.CORTE, 1 + (puertas > 0.6F ? 1 : 0) + (puertas > 0.9F ? 1 : 0));
        }
        sl.playSound(null, v.getX(), v.getY() + 1, v.getZ(), AparatosRegistro.DONCELLA_PINCHOS.get(), net.minecraft.sounds.SoundSource.PLAYERS,
                0.5F + fuera * 0.05F, 0.85F + v.getRandom().nextFloat() * 0.3F);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                v.getX(), v.getY() + 1.0, v.getZ(), 2 + fuera, 0.2, 0.45, 0.2, 0.05);
    }

    /** Lo que manda la víctima cada 10 ticks (ya recortado a 0..10). */
    void informe(int fuera) {
        if (level == null) return;
        fueraAcum = Math.min(10, fueraAcum + fuera);
        ultimoInforme = level.getGameTime();
    }

    private void empezarJuego(ServerPlayer sp) {
        juego = true;
        semilla = sp.getRandom().nextLong() | 1L;
        if (level != null) ultimoInforme = level.getGameTime() + 20;   // margen para que arranque en el cliente
        fueraAcum = 0;
        com.miscelanea.tortura.qte.Qte.cancelar(sp);
        AparatosRed.a(sp, new AparatoJuegoPacket(AparatoJuegoPacket.DONCELLA, worldPosition, semilla, 0F));
    }

    private void pararJuego(ServerPlayer sp) {
        juego = false;
        AparatosRed.a(sp, new AparatoJuegoPacket(AparatoJuegoPacket.PARAR, worldPosition, 0, 0F));
    }

    @Override
    protected void alMeter(LivingEntity v, @Nullable Player quien) {
        puertas = 0F;
        objetivo = 0F;
        juego = false;
        sonido(AparatosRegistro.DONCELLA_CHIRRIDO.get(), 0.8F, 1.1F);
    }

    @Override
    protected void alSacar(@Nullable LivingEntity v) {
        if (juego && v instanceof ServerPlayer sp) pararJuego(sp);
        juego = false;
        fueraAcum = 0;
        objetivo = 0F;   // se abre sola
    }

    @Override
    protected void tickVisual() {
        visO = vis;
        vis += (puertas - vis) * 0.35F;
        if (Math.abs(puertas - vis) < 0.001F) vis = puertas;
    }

    @Override
    protected void guardar(CompoundTag t) {
        t.putFloat("Puertas", puertas);
        t.putFloat("Objetivo", objetivo);
    }

    @Override
    protected void cargar(CompoundTag t) {
        puertas = t.getFloat("Puertas");
        objetivo = t.getFloat("Objetivo");
    }
}
