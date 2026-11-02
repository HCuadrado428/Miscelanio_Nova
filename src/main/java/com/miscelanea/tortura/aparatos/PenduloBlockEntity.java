package com.miscelanea.tortura.aparatos;

import com.miscelanea.armas.TemblorPacket;
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
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * EL PÉNDULO (Poe, "El pozo y el péndulo"; Saw V). La víctima va tumbada boca arriba en la mesa, atada por las muñecas
 * y los tobillos (4 correas), mirando a la cuchilla en media luna que oscila de lado a lado sobre su pecho.
 * <ul>
 *   <li>Captor: clic derecho en la mesa (la palanca de los pies) = en marcha / parar; MANTENER clic derecho en la
 *       cabecera (la manivela) = subir la cuchilla muesca a muesca; rueda del ratón mirándolo = velocidad (1..3).</li>
 *   <li>En marcha, la cuchilla oscila (cada vez más abierta) y baja una muesca del trinquete cada poco: de arriba a la
 *       víctima tarda 50 s a velocidad 1 (32 s a 2, 20 s a 3). Silbido en cada pasada (más fuerte cuanto más baja),
 *       tictac en cada extremo y la carraca en cada muesca.</li>
 *   <li>Víctima (jugador): rondas de SECUENCIA mientras oscila; cada acierto rompe una correa; con las cuatro rotas
 *       rueda fuera de la mesa, libre. Los mobs las rompen de vez en cuando por suerte.</li>
 *   <li>Abajo del todo, cada pasada corta: cortes en el torso, mucho daño y a la tercera, muerte (mensaje propio).</li>
 * </ul>
 * Medidas del modelo (px, mirando al norte) en scratchpad/gen_tortura4a.py: P_PIVOTE, P_HUB, mesa y correas.
 */
public class PenduloBlockEntity extends AparatoBlockEntity {
    // ---- geometría (px del modelo, mirando al norte; los pies hacia -z)
    public static final float PIVOTE_X = 8F, PIVOTE_Y = 58F, PIVOTE_Z = 20F;
    /** Longitud de la vara arriba y abajo del todo; altura del cubo de enganche en el modelo de la cuchilla y del filo bajo él. */
    public static final float L_ARRIBA = 5F, L_ABAJO = 26.5F, HUB_Y = 28F, FILO = 14.9F;
    /** Amplitud (grados) arriba y abajo. */
    public static final float AMP_MIN = 22F, AMP_MAX = 38F;
    public static final int MUESCAS = 20;
    /** Correas: x, z (px) de su centro y escala en x (muñeca derecha, muñeca izquierda, tobillo derecho, tobillo izquierdo). */
    public static final float[][] CORREAS = {{2.4F, 12.5F, 1F}, {13.6F, 12.5F, 1F}, {6.05F, 3.6F, 0.84F}, {9.95F, 3.6F, 0.84F}};
    /** Palanca y manivela: eje (y, z) en px. */
    public static final float PALANCA_Y = 14.5F, PALANCA_Z = -1.25F, MANIVELA_Y = 20F, MANIVELA_Z = 30.75F;
    /**
     * La víctima tumbada (como en el potro): altura de las tablas, cuánto se corre el centro de la mesa hacia la
     * cabecera, dónde quedan los pies respecto al centro, y para un JUGADOR, su posición bajo la mesa para que la cámara
     * quede en su cabeza tumbada (OJOS_ATRAS desde los pies, OJOS_ALTO sobre las tablas). ALZA: holgura bajo la espalda.
     */
    public static final double SUPERFICIE = 11 / 16.0, CENTRO_ATRAS = 0.5, PIES = 0.906, OJOS_ATRAS = 1.62, OJOS_ALTO = 0.434;
    public static final float ALZA = 0.2F;
    private static final float DOS_PI = (float) (Math.PI * 2);
    private static final int[] INTERVALO = {50, 32, 20};
    private static final float[] RITMO = {1F, 1.2F, 1.45F};

    private boolean enMarcha;
    private int velocidad = 1, correas = 0b1111, pasesCorte, reloj, manivela, esperaPalanca;
    private float bajada, fase, ampK;
    // minijuego (no se guarda: al cargar el mundo vuelve a empezar)
    private int sesion, esperaQte;
    private boolean qteActivo;
    /** Cliente: lo que se dibuja (suave). */
    public float faseVis, angVis, angVisO, bajadaVis, bajadaVisO, palancaVis, palancaVisO, manivelaVis, manivelaVisO;

    public PenduloBlockEntity(BlockPos p, BlockState s) {
        super(AparatosRegistro.PENDULO_BE.get(), p, s);
    }

    @Override
    public int tipo() {
        return AparatosVictimas.PENDULO;
    }

    // ------------------------------------------------------------------ consultas
    public boolean enMarcha() {
        return enMarcha;
    }

    public int velocidad() {
        return velocidad;
    }

    public float bajada() {
        return bajada;
    }

    public int correas() {
        return correas;
    }

    public boolean cortando() {
        return pasesCorte > 0;
    }

    public static float longitud(float bajada) {
        return Mth.lerp(bajada, L_ARRIBA, L_ABAJO);
    }

    public static float amplitud(float bajada) {
        return Mth.lerp(bajada, AMP_MIN, AMP_MAX);
    }

    private float omega() {
        return DOS_PI / 50F * RITMO[Mth.clamp(velocidad, 1, 3) - 1];
    }

    private int intervalo() {
        return INTERVALO[Mth.clamp(velocidad, 1, 3) - 1];
    }

    /** Ángulo de la cuchilla (grados) ahora, en el servidor. */
    public float angulo() {
        return amplitud(bajada) * ampK * Mth.sin(fase);
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

    /** Del espacio del modelo (px, mirando al norte) al mundo. */
    public Vec3 mundo(double mx, double my, double mz) {
        Direction f = frente(), der = f.getClockWise(), atras = f.getOpposite();
        double dx = mx / 16 - 0.5, dz = mz / 16 - 0.5;
        return new Vec3(worldPosition.getX() + 0.5 + der.getStepX() * dx + atras.getStepX() * dz, worldPosition.getY() + my / 16,
                worldPosition.getZ() + 0.5 + der.getStepZ() * dx + atras.getStepZ() * dz);
    }

    /** Dónde está ahora el filo de la cuchilla (mundo). */
    public Vec3 filo() {
        double r = longitud(bajada) + FILO, a = Math.toRadians(angulo());
        return mundo(PIVOTE_X + r * Math.sin(a), PIVOTE_Y - r * Math.cos(a), PIVOTE_Z);
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
        return 25F;
    }

    /** Al soltarse rueda fuera de la mesa, por un lado (si no cabe, lo de siempre). */
    @Override
    public Vec3 salida(LivingEntity v) {
        if (level == null) return v.position();
        Direction der = frente().getClockWise();
        Vec3 c = centroMesa();
        for (Direction d : new Direction[]{der, der.getOpposite()}) {
            Vec3 s = c.add(d.getStepX() * 1.2, 0, d.getStepZ() * 1.2);
            if (level.noCollision(v, v.getDimensions(v.getPose()).makeBoundingBox(s))) return s;
        }
        return super.salida(v);
    }

    // ------------------------------------------------------------------ captor
    @Override
    protected void usarExtra(Player p, boolean anexo, BlockHitResult hit) {
        if (ocupado() && !puedeManejar(p)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.aparato.no_tuyo").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (anexo) {
            subir(p);
            return;
        }
        if (esperaPalanca > 0) return;   // mantener el clic no la mueve una y otra vez
        esperaPalanca = 10;
        enMarcha = !enMarcha;
        if (enMarcha && reloj <= 0) reloj = intervalo();
        sonido(AparatosRegistro.PENDULO_PALANCA.get(), 0.9F, enMarcha ? 0.9F : 1.1F);
        p.displayClientMessage(Component.translatable(enMarcha ? "msg.miscelanea.pendulo.marcha" : "msg.miscelanea.pendulo.parado")
                .withStyle(enMarcha ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
        marcar();
    }

    /** Una muesca hacia arriba (cada repetición del clic mantenido en la manivela). */
    private void subir(Player p) {
        if (bajada <= 0F) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.pendulo.arriba").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        bajada = Math.max(0F, bajada - 1F / MUESCAS);
        if (bajada < 0.999F) pasesCorte = 0;
        manivela++;
        reloj = intervalo();
        sonido(AparatosRegistro.PENDULO_CARRACA.get(), 0.7F, 1.25F + p.getRandom().nextFloat() * 0.1F);
        p.displayClientMessage(Component.translatable("msg.miscelanea.pendulo.subiendo", Math.round(bajada * 100)).withStyle(ChatFormatting.GOLD), true);
        marcar();
    }

    public void cambiarVelocidad(Player p, int d) {
        if (ocupado() && !puedeManejar(p)) return;
        int antes = velocidad;
        velocidad = Mth.clamp(velocidad + d, 1, 3);
        if (velocidad == antes) return;
        reloj = Math.min(reloj, intervalo());
        sonido(AparatosRegistro.SILLA_DIAL.get(), 0.6F, 0.8F + velocidad * 0.15F);
        p.displayClientMessage(Component.translatable("msg.miscelanea.pendulo.velocidad", velocidad)
                .withStyle(velocidad >= 3 ? ChatFormatting.RED : ChatFormatting.GOLD), true);
        marcar();
    }

    // ------------------------------------------------------------------ tick
    @Override
    protected void tickAparato(ServerLevel sl) {
        if (esperaPalanca > 0) esperaPalanca--;
        float antes = fase;
        oscilar();
        if (ampK > 0F) {
            if (Mth.floor(fase / Math.PI) != Mth.floor(antes / Math.PI)) pasada(sl);
            if (Mth.floor((fase + Math.PI / 2) / Math.PI) != Mth.floor((antes + Math.PI / 2) / Math.PI)) {
                Vec3 c = mundo(PIVOTE_X, PIVOTE_Y - 2, PIVOTE_Z);
                sl.playSound(null, c.x, c.y, c.z, AparatosRegistro.PENDULO_TICTAC.get(), SoundSource.BLOCKS, 0.45F, 0.95F + sl.random.nextFloat() * 0.1F);
            }
        }
        if (fase >= DOS_PI) fase -= DOS_PI;
        if (enMarcha && bajada < 1F && --reloj <= 0) {
            reloj = intervalo();
            bajada = Math.min(1F, bajada + 1F / MUESCAS);
            if (bajada > 0.999F) bajada = 1F;
            Vec3 c = mundo(PIVOTE_X, PIVOTE_Y, PIVOTE_Z);
            sl.playSound(null, c.x, c.y, c.z, AparatosRegistro.PENDULO_CARRACA.get(), SoundSource.BLOCKS, 0.9F, 0.85F + sl.random.nextFloat() * 0.15F);
            if (bajada >= 1F) avisarCaptor(sl, Component.translatable("msg.miscelanea.pendulo.abajo").withStyle(ChatFormatting.DARK_RED));
            marcar();
        }
    }

    /** Avanza la oscilación (igual en el servidor y, para predecir, en el cliente). */
    private void oscilar() {
        if (enMarcha) ampK = Math.min(1F, ampK + 0.02F);
        else if (ampK > 0F) {
            ampK *= 0.985F;
            if (ampK < 0.01F) ampK = 0F;
        }
        if (ampK > 0F) fase += omega();
    }

    /** La cuchilla pasa por el centro: silbido y, abajo del todo, el corte. */
    private void pasada(ServerLevel sl) {
        Vec3 f = filo();
        sl.playSound(null, f.x, f.y, f.z, AparatosRegistro.PENDULO_SILBIDO.get(), SoundSource.BLOCKS, (0.3F + 0.9F * bajada) * Math.max(0.3F, ampK),
                0.9F + 0.15F * (velocidad - 1) + sl.random.nextFloat() * 0.08F);
        if (enMarcha && bajada >= 1F && ampK > 0.5F && ocupado()) cortar(sl);
    }

    private void cortar(ServerLevel sl) {
        LivingEntity v = victimaEntidad();
        if (v == null || !v.isAlive()) return;
        pasesCorte++;
        Vec3 pecho = mundo(8, 15.5, PIVOTE_Z);
        sl.playSound(null, pecho.x, pecho.y, pecho.z, AparatosRegistro.PENDULO_CORTE.get(), SoundSource.BLOCKS, 1.3F, 0.9F + sl.random.nextFloat() * 0.15F);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), pecho.x, pecho.y, pecho.z,
                22 + pasesCorte * 10, 0.35, 0.08, 0.2, 0.12);
        sl.sendParticles(ParticleTypes.CRIT, pecho.x, pecho.y + 0.1, pecho.z, 6, 0.3, 0.05, 0.1, 0.2);
        TemblorPacket.enviar(sl, pecho, 10, 0.7F + pasesCorte * 0.35F, 10);
        if (v instanceof ServerPlayer sp) AparatosRed.a(sp, new AparatoEfectoPacket(sp.getId(), AparatoEfectoPacket.CORTE, pasesCorte));
        Aparatos.herida(v, Parte.TORSO, Tipo.CORTE, 3);
        if (pasesCorte >= 2) Aparatos.herida(v, v.getRandom().nextBoolean() ? Parte.BRAZO_DER : Parte.BRAZO_IZQ, Tipo.CORTE, 2);
        if (pasesCorte >= 3) Aparatos.matar(v, AparatosRegistro.DANO_PENDULO, captor);
        else Aparatos.danar(v, AparatosRegistro.DANO_PENDULO, pasesCorte == 1 ? 9F : 14F, captor);
        marcar();
    }

    @Override
    protected void tickVictima(ServerLevel sl, LivingEntity v) {
        if (v instanceof ServerPlayer sp) tickQte(sp);
        else if (enMarcha && correas != 0 && pasesCorte == 0 && sl.getGameTime() % 80 == 0 && v.getRandom().nextFloat() < 0.12F)
            romperCorrea(sl, v, null);
    }

    // ------------------------------------------------------------------ minijuego: romper las correas
    private void tickQte(ServerPlayer sp) {
        if (!enMarcha || correas == 0 || pasesCorte > 0 || qteActivo) return;
        if (esperaQte > 0) {
            esperaQte--;
            return;
        }
        if (Qte.activo(sp)) {
            esperaQte = 20;   // está en otro minijuego: que lo acabe
            return;
        }
        int n = 4 - Integer.bitCount(correas) + 1;
        float d = Mth.clamp(0.3F + 0.4F * bajada + 0.05F * (velocidad - 1), 0F, 0.9F);
        final int ses = ++sesion;
        qteActivo = true;
        Qte.iniciar(sp, QteTipo.SECUENCIA, QteParams.de(d).titulo(Component.translatable("qte.miscelanea.titulo.pendulo", n)).ancla(sp.position(), 3.0),
                (p, r) -> finQte(ses, p, r));
    }

    private void finQte(int ses, @Nullable ServerPlayer p, QteResultado r) {
        if (ses != sesion) return;
        qteActivo = false;
        if (isRemoved() || p == null || !esVictima(p) || !(level instanceof ServerLevel sl)) return;
        if (r.exito()) {
            esperaQte = 12;
            romperCorrea(sl, p, p);
        } else if (r == QteResultado.CANCELADO) {
            esperaQte = 30;
        } else {
            esperaQte = 40;
            p.displayClientMessage(Component.translatable("msg.miscelanea.pendulo.aguanta").withStyle(ChatFormatting.GRAY), true);
        }
    }

    private void romperCorrea(ServerLevel sl, LivingEntity v, @Nullable ServerPlayer sp) {
        if (correas == 0) return;
        int n = Integer.bitCount(correas), k = v.getRandom().nextInt(n), i = 0;
        for (int b = 0; b < 4; b++) {
            if ((correas & (1 << b)) == 0) continue;
            if (k-- == 0) {
                i = b;
                break;
            }
        }
        correas &= ~(1 << i);
        Vec3 c = mundo(CORREAS[i][0], 14, CORREAS[i][1]);
        sl.playSound(null, c.x, c.y, c.z, AparatosRegistro.PENDULO_CORREA.get(), SoundSource.BLOCKS, 1F, 0.9F + v.getRandom().nextFloat() * 0.2F);
        sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.LEATHER)), c.x, c.y, c.z, 14, 0.15, 0.1, 0.15, 0.12);
        marcar();
        int quedan = Integer.bitCount(correas);
        if (sp != null && quedan > 0)
            sp.displayClientMessage(Component.translatable("msg.miscelanea.pendulo.correa_rota_tu", quedan).withStyle(ChatFormatting.GREEN), true);
        avisarCaptor(sl, Component.translatable("msg.miscelanea.pendulo.correa_rota", v.getDisplayName(), quedan).withStyle(ChatFormatting.RED));
        if (quedan == 0) liberar(sl, v, sp);
    }

    /** Las cuatro correas rotas: rueda fuera de la mesa. */
    private void liberar(ServerLevel sl, LivingEntity v, @Nullable ServerPlayer sp) {
        avisarCaptor(sl, Component.translatable("msg.miscelanea.pendulo.libre", v.getDisplayName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        sacar(true);
        if (sp != null) sp.displayClientMessage(Component.translatable("msg.miscelanea.pendulo.libre_tu").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
    }

    private void avisarCaptor(ServerLevel sl, Component c) {
        if (captor == null) return;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(captor);
        if (p != null && p.level() == sl && p.distanceToSqr(centro()) < 48 * 48) p.displayClientMessage(c, true);
    }

    @Override
    protected void alMeter(LivingEntity v, @Nullable Player quien) {
        correas = 0b1111;
        pasesCorte = 0;
        qteActivo = false;
        sesion++;
        esperaQte = 40;
    }

    @Override
    protected void alSacar(@Nullable LivingEntity v) {
        sesion++;
        if (qteActivo && v instanceof ServerPlayer sp) Qte.cancelar(sp);
        qteActivo = false;
        pasesCorte = 0;
    }

    // ------------------------------------------------------------------ cliente
    @Override
    protected void tickVisual() {
        bajadaVisO = bajadaVis;
        bajadaVis += (bajada - bajadaVis) * 0.35F;
        if (Math.abs(bajada - bajadaVis) < 0.0005F) bajadaVis = bajada;
        palancaVisO = palancaVis;
        palancaVis += ((enMarcha ? 1F : 0F) - palancaVis) * 0.4F;
        manivelaVisO = manivelaVis;
        manivelaVis += (manivela * 40F - manivelaVis) * 0.4F;
        if (Math.abs(manivela * 40F - manivelaVis) > 720F) manivelaVis = manivelaVisO = manivela * 40F;
        // la oscilación se predice aquí y se corrige poco a poco con lo que llega del servidor
        oscilar();
        if (fase >= DOS_PI) fase -= DOS_PI;
        if (ampK > 0F) faseVis += omega();
        float d = fase - faseVis;
        d -= DOS_PI * Mth.floor(d / DOS_PI + 0.5F);
        faseVis += d * 0.2F;
        if (faseVis >= DOS_PI) faseVis -= DOS_PI;
        if (faseVis < 0F) faseVis += DOS_PI;
        angVisO = angVis;
        angVis = amplitud(bajadaVis) * ampK * Mth.sin(faseVis);
        if (Math.abs(angVis - angVisO) > 25F) angVisO = angVis;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.5, 0, 2.5).expandTowards(0, 4.5, 0);
    }

    // ------------------------------------------------------------------ guardar / sincronizar
    @Override
    protected void guardar(CompoundTag t) {
        t.putBoolean("EnMarcha", enMarcha);
        t.putInt("Velocidad", velocidad);
        t.putFloat("Bajada", bajada);
        t.putFloat("Fase", fase);
        t.putFloat("AmpK", ampK);
        t.putInt("Correas", correas);
        t.putInt("Pases", pasesCorte);
        t.putInt("Reloj", reloj);
        t.putInt("Manivela", manivela);
    }

    @Override
    protected void cargar(CompoundTag t) {
        enMarcha = t.getBoolean("EnMarcha");
        velocidad = t.contains("Velocidad") ? Mth.clamp(t.getInt("Velocidad"), 1, 3) : 1;
        bajada = Mth.clamp(t.getFloat("Bajada"), 0F, 1F);
        fase = t.getFloat("Fase");
        ampK = t.getFloat("AmpK");
        correas = t.contains("Correas") ? t.getInt("Correas") & 0b1111 : 0b1111;
        pasesCorte = t.getInt("Pases");
        reloj = t.getInt("Reloj");
        manivela = t.getInt("Manivela");
    }
}
