package com.miscelanea.tortura.aparatos;

import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Colgado boca abajo: la víctima cuelga de los tobillos de la cuerda del gancho, con el cuerpo siguiendo la cuerda
 * (péndulo de dos ángulos, como la jaula colgante). El asiento invisible se mueve con ella ({@link #sitioMovil}).
 * <ul>
 *   <li>Captor: rueda del ratón mirando el gancho = manivela (sube / baja); clic derecho con la mano vacía al colgado (o al
 *       gancho) = empujarlo; Shift + clic derecho con la mano vacía = bajarlo al suelo y soltarlo. Pegarle también lo
 *       balancea.</li>
 *   <li>Víctima: la sangre se le va a la cabeza (pantalla roja y borrosa, en el cliente); a partir de {@link #DANO_DESDE}
 *       ticks colgado, daño lento (mortal, con su propio mensaje). SALTAR: intenta escapar: ALTERNAR para encogerse y
 *       agarrar la cuerda, luego MACHACAR para desatarse; si lo consigue, cae de cabeza (daño de caída según la altura).</li>
 * </ul>
 * Medidas (punto de la cuerda, eje del torno) = scratchpad/gen_tortura5.py (G_*).
 */
public class GanchoBlockEntity extends AparatoBlockEntity {
    /** Altura (px) del punto por el que sale la cuerda (la argolla), en el centro del bloque. */
    public static final float PIVOTE_PX = 5.0F;
    /** Eje del torno (y, z en px, paralelo a x) y radio del tambor (px). */
    public static final float TORNO_Y = 11.5F, TORNO_Z = 8F, TAMBOR_R = 1.6F;
    public static final double L_MIN = 0.35, L_INICIAL = 1.0, VEL_TORNO = 0.03, PASO_MANIVELA = 0.25;
    static final double G = 0.065, ROCE = 0.9965;
    public static final int DANO_DESDE = 1200, DANO_CADA = 50;

    /** Estado del péndulo (lo usan igual el servidor y la predicción del cliente). */
    public static final class Estado {
        public double ax, az, wx, wz, largo = L_INICIAL, objetivo = L_INICIAL;

        void copiar(Estado o) {
            ax = o.ax;
            az = o.az;
            wx = o.wx;
            wz = o.wz;
            largo = o.largo;
            objetivo = o.objetivo;
        }

        /** Un tick: torno hacia el objetivo y péndulo con rozamiento. Devuelve true si el torno se ha movido. */
        boolean paso(double alto, double maxL, boolean colgando) {
            boolean torno = false;
            double obj = Mth.clamp(objetivo, L_MIN, Math.max(L_MIN, maxL));
            if (Math.abs(obj - largo) > 1.0E-3) {
                largo += Mth.clamp(obj - largo, -VEL_TORNO, VEL_TORNO);
                torno = true;
            }
            if (!colgando) {
                wx *= 0.8;
                wz *= 0.8;
                ax *= 0.9;
                az *= 0.9;
            } else {
                double brazo = largo + alto * 0.55;
                double w0 = G / brazo;
                wx = (wx - w0 * Math.sin(ax)) * ROCE;
                wz = (wz - w0 * Math.sin(az)) * ROCE;
            }
            ax = Mth.clamp(ax + wx, -1.25, 1.25);
            az = Mth.clamp(az + wz, -1.25, 1.25);
            return torno;
        }
    }

    // ---- servidor (se guarda y se sincroniza)
    private final Estado est = new Estado();
    private double alto = 1.8, maxL = 4;
    private int colgadoT, enfriarEscape, radialSigno, avisoT;
    private boolean agarrado;
    /** Al soltarse él solo: sale en el aire (y cae); si no, se le deja en el suelo. */
    private boolean caidaLibre;

    // ---- cliente: simulación (predicción) y lo que se dibuja (cur = d0, y los dos anteriores)
    private final Estado sim = new Estado();
    public float ax0, az0, l0, ax1, az1, l1, ax2, az2, l2;
    private boolean iniciado;
    public int colgadoCli;

    public GanchoBlockEntity(BlockPos p, BlockState s) {
        super(AparatosRegistro.GANCHO_BE.get(), p, s);
    }

    @Override
    public int tipo() {
        return AparatosVictimas.COLGADO;
    }

    // ------------------------------------------------------------------ geometría
    public Vec3 pivote() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + PIVOTE_PX / 16.0, worldPosition.getZ() + 0.5);
    }

    /** Dirección de la cuerda (unitaria, hacia abajo) con esos ángulos (= JaulaEntity.direccion). */
    public static Vec3 direccion(double ax, double az) {
        return new Vec3(Math.sin(ax), -Math.cos(ax) * Math.cos(az), Math.sin(az)).normalize();
    }

    public double alto() {
        return alto;
    }

    public boolean agarrado() {
        return agarrado;
    }

    public int colgadoT() {
        return colgadoT;
    }

    /** Pies de la entidad (esquina de abajo de su caja) con el cuerpo colgando de la cuerda. */
    private Vec3 sitio(double ax, double az, double largo) {
        Vec3 c = pivote().add(direccion(ax, az).scale(largo + alto * 0.5));
        return c.add(0, -alto * 0.5, 0);
    }

    @Override
    public Vec3 sitioVictima(LivingEntity v) {
        return level != null && level.isClientSide ? sitio(ax0, az0, l0) : sitio(est.ax, est.az, est.largo);
    }

    @Override
    @Nullable
    public Vec3 sitioMovil(LivingEntity v) {
        return sitioVictima(v);
    }

    @Override
    public boolean sentado() {
        return false;
    }

    @Override
    public float giroCabeza() {
        return 85F;
    }

    /** Suelo bajo el gancho (y del bloque de apoyo) o null si no hay en 30 bloques. */
    @Nullable
    private Double suelo(Vec3 desde) {
        if (level == null) return null;
        BlockHitResult r = level.clip(new ClipContext(desde, desde.add(0, -30, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        return r.getType() == HitResult.Type.MISS ? null : r.getLocation().y;
    }

    private double calcularMax(double h) {
        Vec3 p = pivote().add(0, -0.05, 0);
        Double s = suelo(p);
        if (s == null) return 12.0;
        return Math.min(12.0, p.y - s - h - 0.12);
    }

    @Override
    public Vec3 salida(LivingEntity v) {
        Vec3 aqui = sitio(est.ax, est.az, est.largo);
        if (caidaLibre || level == null) return aqui;
        // bajarlo: de pie en el suelo, debajo de donde está la cabeza
        Vec3 c = aqui.add(0, alto * 0.5, 0);
        Double s = suelo(c);
        if (s != null) {
            Vec3 pie = new Vec3(c.x, s, c.z);
            if (level.noCollision(v, v.getDimensions(v.getPose()).makeBoundingBox(pie))) return pie;
        }
        Double s2 = suelo(pivote().add(0, -0.05, 0));
        return s2 != null ? new Vec3(worldPosition.getX() + 0.5, s2, worldPosition.getZ() + 0.5) : aqui;
    }

    // ------------------------------------------------------------------ meter / sacar
    @Override
    public boolean meter(LivingEntity v, @Nullable Player quien) {
        if (ocupado() || level == null || level.isClientSide) return super.meter(v, quien);
        double h = v.getBbHeight();
        double max = calcularMax(h);
        if (max < L_MIN) {
            if (quien != null)
                quien.displayClientMessage(Component.translatable("msg.miscelanea.gancho.bajo").withStyle(ChatFormatting.RED), true);
            return false;
        }
        double antesL = est.largo, antesO = est.objetivo, antesH = alto, antesM = maxL;
        alto = h;
        maxL = max;
        est.largo = est.objetivo = Mth.clamp(est.objetivo, L_MIN, max);
        est.ax = est.az = est.wx = est.wz = 0;
        if (!super.meter(v, quien)) {
            est.largo = antesL;
            est.objetivo = antesO;
            alto = antesH;
            maxL = antesM;
            return false;
        }
        return true;
    }

    @Override
    protected void alMeter(LivingEntity v, @Nullable Player quien) {
        colgadoT = 0;
        agarrado = false;
        caidaLibre = false;
        enfriarEscape = 40;
        if (v instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.translatable("msg.miscelanea.gancho.dentro_ayuda", Component.keybind("key.jump"))
                    .withStyle(ChatFormatting.GRAY), false);
        sonido(AparatosRegistro.COLGADO_CUERDA.get(), 1F, 0.8F);
    }

    @Override
    protected void alSacar(@Nullable LivingEntity v) {
        agarrado = false;
        colgadoT = 0;
        if (v instanceof ServerPlayer sp) {
            Qte.cancelar(sp);
            sp.removeEffect(MobEffects.CONFUSION);
        }
    }

    // ------------------------------------------------------------------ uso
    @Override
    protected void usarExtra(Player p, boolean anexo, BlockHitResult hit) {
        if (!ocupado()) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.gancho.vacio").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (p.getMainHandItem().isEmpty()) empujar(p.position(), 1.0);
        else p.displayClientMessage(Component.translatable("msg.miscelanea.gancho.ayuda_captor").withStyle(ChatFormatting.GRAY), true);
    }

    /** Empuja al colgado alejándolo de 'desde' (en horizontal). */
    public void empujar(Vec3 desde, double fuerza) {
        if (!ocupado()) return;
        Vec3 c = sitio(est.ax, est.az, est.largo).add(0, alto * 0.5, 0);
        Vec3 d = new Vec3(c.x - desde.x, 0, c.z - desde.z);
        if (d.lengthSqr() < 1.0E-6) d = new Vec3(1, 0, 0);
        d = d.normalize();
        double k = fuerza * 0.05 / Math.sqrt(est.largo + alto * 0.55);
        est.wx += d.x * k;
        est.wz += d.z * k;
        sonido(AparatosRegistro.COLGADO_CUERDA.get(), 0.7F, 0.9F + level.random.nextFloat() * 0.2F);
        sincronizar();
    }

    /** Rueda del ratón mirando el gancho: la manivela sube (+1) o baja (-1) un paso. */
    public void manivela(Player p, int d) {
        if (ocupado() && !puedeManejar(p)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.aparato.no_tuyo").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!ocupado()) maxL = calcularMax(1.8);
        double antes = est.objetivo;
        est.objetivo = Mth.clamp(est.objetivo - d * PASO_MANIVELA, L_MIN, Math.max(L_MIN, maxL));
        if (Math.abs(antes - est.objetivo) < 1.0E-4) {
            p.displayClientMessage(Component.translatable(d > 0 ? "msg.miscelanea.gancho.arriba" : "msg.miscelanea.gancho.abajo")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        p.displayClientMessage(Component.translatable("msg.miscelanea.gancho.cuerda", String.format(java.util.Locale.ROOT, "%.2f", est.objetivo))
                .withStyle(ChatFormatting.GOLD), true);
        sincronizar();
    }

    // ------------------------------------------------------------------ escapar (víctima jugador)
    void escapar(ServerPlayer v) {
        if (!esVictima(v) || enfriarEscape > 0 || Qte.activo(v)) return;
        enfriarEscape = 60;
        // primero: encogerse balanceándose hasta agarrar la cuerda (ALTERNAR)
        empujar(v.position().add(v.getLookAngle().scale(-1)), 0.6);
        Qte.iniciar(v, QteTipo.ALTERNAR, QteParams.de(0.45F).titulo(Component.translatable("qte.miscelanea.titulo.colgado_agarrar"))
                .ancla(v.position(), 6), (pl, r) -> {
            if (pl == null || isRemoved() || !esVictima(pl) || !pl.isAlive()) return;
            if (!r.exito()) {
                enfriarEscape = 80;
                if (r != com.miscelanea.tortura.qte.QteResultado.CANCELADO)
                    pl.displayClientMessage(Component.translatable("msg.miscelanea.gancho.no_llegas").withStyle(ChatFormatting.RED), true);
                return;
            }
            agarrado = true;
            sonido(AparatosRegistro.COLGADO_CUERDA.get(), 1F, 1.2F);
            pl.displayClientMessage(Component.translatable("msg.miscelanea.gancho.agarrado").withStyle(ChatFormatting.GOLD), true);
            sincronizar();
            // después: desatar el nudo de los tobillos (MACHACAR)
            Qte.despues(8, () -> {
                if (isRemoved() || !esVictima(pl) || !pl.isAlive() || !agarrado) return;
                Qte.iniciar(pl, QteTipo.MACHACAR, QteParams.de(0.55F).titulo(Component.translatable("qte.miscelanea.titulo.colgado_desatar"))
                        .ancla(pl.position(), 6), (p2, r2) -> {
                    if (p2 == null || isRemoved() || !esVictima(p2) || !p2.isAlive()) return;
                    if (r2.exito()) soltarse(p2);
                    else {
                        agarrado = false;
                        enfriarEscape = 100;
                        if (r2 != com.miscelanea.tortura.qte.QteResultado.CANCELADO)
                            p2.displayClientMessage(Component.translatable("msg.miscelanea.gancho.resbalas").withStyle(ChatFormatting.RED), true);
                        empujar(p2.position().add(level.random.nextDouble() - 0.5, 0, level.random.nextDouble() - 0.5), 0.8);
                        sincronizar();
                    }
                });
            });
        });
    }

    /** Se ha desatado: cae de cabeza desde donde está. */
    private void soltarse(ServerPlayer v) {
        Vec3 aqui = sitio(est.ax, est.az, est.largo);
        Double s = suelo(aqui.add(0, alto * 0.5, 0));
        double caida = s == null ? 3.0 : Math.max(0.0, aqui.y - s);
        caidaLibre = true;
        sonido(AparatosRegistro.COLGADO_DESATAR.get(), 1F, 1F);
        sacar(false);
        caidaLibre = false;
        // cae de cabeza: el golpe es algo peor que una caída normal desde esa altura
        v.fallDistance = (float) (2.0 + caida * 0.5);
        AparatosRed.a(v, new InterrogaEfectoPacket(v.getId(), InterrogaEfectoPacket.SUELTA, (int) Math.round(caida * 10)));
        v.displayClientMessage(Component.translatable("msg.miscelanea.gancho.libre_tu").withStyle(ChatFormatting.GREEN), true);
        if (captor != null && level instanceof ServerLevel sl) {
            ServerPlayer c = sl.getServer().getPlayerList().getPlayer(captor);
            if (c != null) c.displayClientMessage(Component.translatable("msg.miscelanea.gancho.libre", v.getDisplayName()).withStyle(ChatFormatting.RED), true);
        }
    }

    // ------------------------------------------------------------------ tick
    @Override
    protected void tickAparato(ServerLevel sl) {
        if (enfriarEscape > 0) enfriarEscape--;
        if (Math.floorMod(worldPosition.hashCode(), 20) == sl.getGameTime() % 20 ) maxL = calcularMax(ocupado() ? alto : 1.8);
        boolean torno = est.paso(alto, maxL, ocupado());
        if (torno && sl.getGameTime() % 3 == 0) sonido(AparatosRegistro.COLGADO_MANIVELA.get(), 0.6F, est.objetivo < est.largo ? 1.15F : 0.9F);
        if (ocupado()) crujir(sl);
        // sincronizar mientras se mueve (el cliente predice entre medias)
        double amp = Math.abs(est.ax) + Math.abs(est.az);
        if ((amp > 0.01 || torno) && sl.getGameTime() % 10 == 0) marcar();
    }

    /** En los extremos del vaivén la cuerda cruje; colgado quieto, de vez en cuando. */
    private void crujir(ServerLevel sl) {
        double radial = est.wx * est.ax + est.wz * est.az;
        int s = radial > 1.0E-6 ? 1 : radial < -1.0E-6 ? -1 : radialSigno;
        double amp = Math.sqrt(est.ax * est.ax + est.az * est.az);
        Vec3 p = pivote();
        if (s != radialSigno && s < 0 && amp > 0.08)
            sl.playSound(null, p.x, p.y, p.z, AparatosRegistro.COLGADO_CUERDA.get(), SoundSource.BLOCKS, (float) Math.min(1.0, 0.25 + amp * 1.5),
                    0.85F + sl.random.nextFloat() * 0.3F);
        else if (sl.random.nextInt(160) == 0)
            sl.playSound(null, p.x, p.y, p.z, AparatosRegistro.COLGADO_CUERDA.get(), SoundSource.BLOCKS, 0.3F, 0.75F + sl.random.nextFloat() * 0.2F);
        radialSigno = s;
    }

    @Override
    protected void tickVictima(ServerLevel sl, LivingEntity v) {
        colgadoT++;
        RandomSource r = v.getRandom();
        if (v instanceof Mob && colgadoT % 30 == 0 && r.nextFloat() < 0.5F) {
            // un mob se revuelve
            double a = r.nextDouble() * Math.PI * 2;
            double k = 0.006 + r.nextDouble() * 0.006;
            est.wx += Math.cos(a) * k;
            est.wz += Math.sin(a) * k;
        }
        int desde = com.miscelanea.ConfigServidor.ganchoDanoDesdeTicks();
        if (colgadoT >= desde) {
            if ((colgadoT - desde) % DANO_CADA == 0) Aparatos.danar(v, AparatosRegistro.DANO_COLGADO, 1.0F, captor);
            if (v instanceof ServerPlayer sp && colgadoT % 100 == 0)
                sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, false));
        }
        if (v instanceof ServerPlayer sp && colgadoT == desde)
            sp.displayClientMessage(Component.translatable("msg.miscelanea.gancho.sangre").withStyle(ChatFormatting.DARK_RED), true);
        if (++avisoT >= 200) {
            avisoT = 0;
            marcar();   // (colgadoT para la pantalla roja del cliente)
        }
    }

    // ------------------------------------------------------------------ cliente
    @Override
    protected void tickVisual() {
        if (!iniciado) {
            iniciado = true;
            ax0 = ax1 = ax2 = (float) sim.ax;
            az0 = az1 = az2 = (float) sim.az;
            l0 = l1 = l2 = (float) sim.largo;
        }
        ax2 = ax1;
        az2 = az1;
        l2 = l1;
        ax1 = ax0;
        az1 = az0;
        l1 = l0;
        sim.paso(alto, maxL, ocupado());
        ax0 += (float) ((sim.ax - ax0) * 0.5);
        az0 += (float) ((sim.az - az0) * 0.5);
        l0 += (float) ((sim.largo - l0) * 0.5);
        if (ocupado()) colgadoCli++;
        else colgadoCli = 0;
    }

    /** Lo que se dibuja (entre los dos estados anteriores; ver InmersionBlockEntity). */
    public float axVis(float pt) {
        return Mth.lerp(pt, ax2, ax1);
    }

    public float azVis(float pt) {
        return Mth.lerp(pt, az2, az1);
    }

    public float largoVis(float pt) {
        return Mth.lerp(pt, l2, l1);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.getX() - 4, worldPosition.getY() - 14, worldPosition.getZ() - 4, worldPosition.getX() + 5,
                worldPosition.getY() + 1, worldPosition.getZ() + 5);
    }

    // ------------------------------------------------------------------ guardar / sincronizar
    @Override
    protected void guardar(CompoundTag t) {
        t.putDouble("Ax", est.ax);
        t.putDouble("Az", est.az);
        t.putDouble("Wx", est.wx);
        t.putDouble("Wz", est.wz);
        t.putDouble("Largo", est.largo);
        t.putDouble("Objetivo", est.objetivo);
        t.putDouble("Alto", alto);
        t.putDouble("MaxL", maxL);
        t.putInt("Colgado", colgadoT);
        t.putBoolean("Agarrado", agarrado);
    }

    @Override
    protected void cargar(CompoundTag t) {
        est.ax = t.getDouble("Ax");
        est.az = t.getDouble("Az");
        est.wx = t.getDouble("Wx");
        est.wz = t.getDouble("Wz");
        est.largo = t.contains("Largo") ? Mth.clamp(t.getDouble("Largo"), L_MIN, 12.0) : L_INICIAL;
        est.objetivo = t.contains("Objetivo") ? Mth.clamp(t.getDouble("Objetivo"), L_MIN, 12.0) : est.largo;
        alto = t.contains("Alto") ? Mth.clamp(t.getDouble("Alto"), 0.3, 2.6) : 1.8;
        maxL = t.contains("MaxL") ? t.getDouble("MaxL") : 4.0;
        colgadoT = t.getInt("Colgado");
        agarrado = t.getBoolean("Agarrado");
        if (level != null && level.isClientSide) {
            sim.copiar(est);
            colgadoCli = colgadoT;
            if (!iniciado) {
                iniciado = true;
                ax0 = ax1 = ax2 = (float) est.ax;
                az0 = az1 = az2 = (float) est.az;
                l0 = l1 = l2 = (float) est.largo;
            }
        }
    }
}
