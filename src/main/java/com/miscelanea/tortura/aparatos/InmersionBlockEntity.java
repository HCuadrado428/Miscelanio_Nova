package com.miscelanea.tortura.aparatos;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * SILLA DE INMERSIÓN (el "ducking stool" medieval). Un poste en la orilla con una viga basculante: en una punta el
 * contrapeso, en la otra la silla colgada (siempre derecha) con la víctima atada. La viga gira suave entre arriba
 * ({@link #ANG_ARRIBA}) y abajo ({@link #ANG_ABAJO}); abajo, la silla queda hundida en el agua de detrás del poste.
 * <ul>
 *   <li>Captor: MANTENER clic derecho en el poste = sumergir (al soltar, sube sola); clic en el apoyo del contrapeso
 *       (el anexo) = bloquearla abajo / soltarla.</li>
 *   <li>Víctima (jugador) bajo el agua: AGUANTAR LA RESPIRACIÓN (cliente: JuegoInmersion): mantener la marca en la
 *       zona de calma; dentro, el aire (el de verdad, las burbujas del HUD) baja despacio; fuera, deprisa. Sin aire:
 *       daño de ahogarse (propio, con mensaje). Los mobs pierden aire a un ritmo fijo; los que respiran bajo el agua
 *       (o con respiración acuática) no.</li>
 *   <li>Efectos: chapuzón al entrar y salir, burbujas, crujidos de la viga, goteo al subir y tos al salir si ha
 *       aguantado mucho.</li>
 * </ul>
 * El asiento de la víctima se mueve con la silla ({@link #sitioMovil}). Medidas en scratchpad/gen_tortura4a.py (I_*).
 */
public class InmersionBlockEntity extends AparatoBlockEntity {
    /** Eje de la viga (y, z en px), largo hasta el pasador de la silla y caída del pasador al asiento. */
    public static final float EJE_Y = 18F, EJE_Z = 8F, BRAZO = 30F, ASIENTO = 13.5F;
    /** Ángulo de la viga (grados; positivo = la silla arriba). */
    public static final float ANG_ARRIBA = 25F, ANG_ABAJO = -58F;
    /** Lo que avanza el brazo (0 arriba .. 1 abajo) por tick al bajar y al subir. */
    public static final float BAJA = 0.03F, SUBE = 0.022F;
    /** Aire (de 300) que se pierde por tick bajo el agua dentro y fuera de la zona de calma. */
    public static final float AIRE_DENTRO = 0.8F, AIRE_FUERA = 3.5F;

    private float brazo, objetivo, aire, aireMin, fueraFrac;
    private int mantener, esperaToggle, goteo, ahogo, crujir;
    private long ultimoInforme;
    private boolean bloqueada, enAgua, sumergida, juego, movia;
    /** Cliente: brazo que usa el asiento (cur) y los dos anteriores (el renderer dibuja entre esos dos, ver tickVisual). */
    public float brazoCli, brazoO1, brazoO2;
    private boolean iniciado;

    public InmersionBlockEntity(BlockPos p, BlockState s) {
        super(AparatosRegistro.INMERSION_BE.get(), p, s);
    }

    @Override
    public int tipo() {
        return AparatosVictimas.INMERSION;
    }

    // ------------------------------------------------------------------ geometría
    public boolean bloqueada() {
        return bloqueada;
    }

    public float brazo() {
        return brazo;
    }

    /** Ángulo de la viga (grados) para un brazo 0..1 (con aceleración y frenada). */
    public static float angulo(float b) {
        b = Mth.clamp(b, 0F, 1F);
        float s = b * b * (3 - 2 * b);
        return Mth.lerp(s, ANG_ARRIBA, ANG_ABAJO);
    }

    /** Pasador de la silla en px del modelo (y, z) para un brazo. */
    public static float[] pasador(float b) {
        double a = Math.toRadians(angulo(b));
        return new float[]{(float) (EJE_Y + BRAZO * Math.sin(a)), (float) (EJE_Z + BRAZO * Math.cos(a))};
    }

    /** Del espacio del modelo (px, mirando al norte) al mundo. */
    public Vec3 mundo(double mx, double my, double mz) {
        Direction f = frente(), der = f.getClockWise(), atras = f.getOpposite();
        double dx = mx / 16 - 0.5, dz = mz / 16 - 0.5;
        return new Vec3(worldPosition.getX() + 0.5 + der.getStepX() * dx + atras.getStepX() * dz, worldPosition.getY() + my / 16,
                worldPosition.getZ() + 0.5 + der.getStepZ() * dx + atras.getStepZ() * dz);
    }

    /** Centro del asiento (mundo) para un brazo. */
    public Vec3 asiento(float b) {
        float[] p = pasador(b);
        return mundo(8, p[0] - ASIENTO, p[1]);
    }

    private float brazoActual() {
        return level != null && level.isClientSide ? brazoCli : brazo;
    }

    private Vec3 sitio(LivingEntity v, float b) {
        Direction atras = frente().getOpposite();
        double baja = v instanceof Player ? 0.58 : Math.min(0.58, 0.58 * v.getBbHeight() / 1.8);
        return asiento(b).add(atras.getStepX() * 0.09, -baja, atras.getStepZ() * 0.09);
    }

    @Override
    public Vec3 sitioVictima(LivingEntity v) {
        return sitio(v, brazoActual());
    }

    @Override
    @Nullable
    public Vec3 sitioMovil(LivingEntity v) {
        return sitio(v, brazoActual());
    }

    @Override
    public boolean sentado() {
        return true;
    }

    @Override
    public float giroCabeza() {
        return 70F;
    }

    /** Bloque de agua donde se hunde la silla (detrás del poste, uno más abajo). */
    public BlockPos posAgua() {
        return worldPosition.relative(frente().getOpposite()).below();
    }

    public boolean hayAgua() {
        return level != null && level.getFluidState(posAgua()).is(FluidTags.WATER);
    }

    public boolean hondo() {
        return hayAgua() && level.getFluidState(posAgua().below()).is(FluidTags.WATER);
    }

    private boolean sillaEnAgua() {
        return hayAgua() && asiento(brazo).y < posAgua().getY() + 0.85;
    }

    /** Al soltarla: delante del apoyo, o a un lado del poste. */
    @Override
    public Vec3 salida(LivingEntity v) {
        if (level == null) return v.position();
        Direction f = frente();
        for (BlockPos p : new BlockPos[]{worldPosition.relative(f, 2), worldPosition.relative(f.getClockWise()), worldPosition.relative(f.getCounterClockWise()),
                worldPosition.relative(f).relative(f.getClockWise())}) {
            Vec3 s = Vec3.atBottomCenterOf(p);
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
            if (esperaToggle > 0) return;
            esperaToggle = 10;
            bloqueada = !bloqueada;
            objetivo = bloqueada ? 1F : 0F;
            mantener = 0;
            sonido(AparatosRegistro.PENDULO_PALANCA.get(), 0.8F, bloqueada ? 0.8F : 1.05F);
            p.displayClientMessage(Component.translatable(bloqueada ? "msg.miscelanea.inmersion.bloqueada" : "msg.miscelanea.inmersion.suelta")
                    .withStyle(bloqueada ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
            marcar();
            return;
        }
        if (mantener == 0 && !bloqueada && !hayAgua())
            p.displayClientMessage(Component.translatable("msg.miscelanea.inmersion.sin_agua").withStyle(ChatFormatting.RED), true);
        mantener = 6;   // el clic mantenido se repite cada 4 ticks: al soltar, sube sola
        if (objetivo != 1F) {
            objetivo = 1F;
            marcar();
        }
    }

    /** Cinemática de muerte (Muertes): la silla se queda abajo, en el agua, hasta el final. */
    public void hundirPorMuerte() {
        mantener = 6;
        if (objetivo != 1F) {
            objetivo = 1F;
            marcar();
        }
    }

    // ------------------------------------------------------------------ tick
    private float mover(float b) {
        if (b < objetivo) return Math.min(objetivo, b + BAJA);
        if (b > objetivo) return Math.max(objetivo, b - SUBE);
        return b;
    }

    @Override
    protected void tickAparato(ServerLevel sl) {
        if (esperaToggle > 0) esperaToggle--;
        if (mantener > 0) mantener--;
        if (mantener == 0 && !bloqueada && objetivo != 0F) {   // nadie mantiene el clic: sube sola
            objetivo = 0F;
            marcar();
        }
        float antes = brazo;
        brazo = mover(brazo);
        boolean mueve = brazo != antes;
        if (mueve) {
            if (--crujir <= 0) {
                crujir = 18;
                sonido(AparatosRegistro.INMERSION_CRUJIDO.get(), 0.7F, 0.8F + sl.random.nextFloat() * 0.3F);
            }
            if (sl.getGameTime() % 5 == 0) marcar();
        } else {
            crujir = 0;
            if (movia) marcar();
        }
        movia = mueve;
        boolean agua = sillaEnAgua();
        if (agua != enAgua) {
            enAgua = agua;
            chapuzon(sl, agua);
            if (!agua) goteo = 120;
            marcar();
        }
        if (goteo > 0) {
            goteo--;
            Vec3 s = asiento(brazo);
            if (goteo % 3 == 0) sl.sendParticles(ParticleTypes.FALLING_WATER, s.x, s.y + 0.4, s.z, 2 + goteo / 30, 0.4, 0.45, 0.4, 0);
            if (goteo % 40 == 20) sl.playSound(null, s.x, s.y, s.z, AparatosRegistro.INMERSION_GOTEO.get(), SoundSource.BLOCKS, 0.35F + goteo / 300F, 1F);
        }
    }

    private void chapuzon(ServerLevel sl, boolean entra) {
        Vec3 s = asiento(brazo);
        double y = posAgua().getY() + 0.9;
        sl.playSound(null, s.x, y, s.z, AparatosRegistro.INMERSION_CHAPUZON.get(), SoundSource.BLOCKS, entra ? 1.2F : 0.75F,
                (entra ? 0.9F : 1.1F) + sl.random.nextFloat() * 0.1F);
        sl.sendParticles(ParticleTypes.SPLASH, s.x, y, s.z, entra ? 60 : 30, 0.5, 0.1, 0.5, 0.3);
        if (entra) sl.sendParticles(ParticleTypes.BUBBLE, s.x, y - 0.6, s.z, 30, 0.4, 0.3, 0.4, 0.15);
        else sl.sendParticles(ParticleTypes.FALLING_WATER, s.x, s.y + 0.5, s.z, 20, 0.45, 0.5, 0.45, 0);
    }

    @Override
    protected void tickVictima(ServerLevel sl, LivingEntity v) {
        ServerPlayer sp = v instanceof ServerPlayer x ? x : null;
        boolean bajo = v.isEyeInFluid(FluidTags.WATER);
        if (bajo && !sumergida) {
            sumergida = true;
            aire = v.getAirSupply();
            aireMin = aire;
            ahogo = 0;
            fueraFrac = 0F;
            if (sp != null) empezarJuego(sp);
        } else if (!bajo && sumergida) {
            sumergida = false;
            if (sp != null && juego) pararJuego(sp);
            if (aireMin < v.getMaxAirSupply() * 0.6F) tos(sl, v);
        }
        if (!bajo) return;
        boolean respira = v.canBreatheUnderwater() || MobEffectUtil.hasWaterBreathing(v) || (v instanceof Player pl && pl.getAbilities().invulnerable);
        if (respira) return;
        float frac = sp == null ? 0.35F : sl.getGameTime() - ultimoInforme > 30 ? 1F : fueraFrac;
        aire -= Mth.lerp(frac, AIRE_DENTRO, AIRE_FUERA);
        if (aire <= 0F) {
            aire = 0F;
            if (++ahogo >= 20) {
                ahogo = 0;
                sl.sendParticles(ParticleTypes.BUBBLE, v.getX(), v.getEyeY(), v.getZ(), 12, 0.2, 0.15, 0.2, 0.1);
                Aparatos.danar(v, AparatosRegistro.DANO_INMERSION, 2F, captor);
            }
        }
        aireMin = Math.min(aireMin, aire);
        // el aire de verdad (el de las burbujas del HUD): se pone después de que el juego le quite el suyo
        v.setAirSupply(Math.round(aire));
        if (sl.getGameTime() % 4 == 0)
            sl.sendParticles(ParticleTypes.BUBBLE, v.getX(), v.getEyeY(), v.getZ(), frac > 0.4F ? 4 : 1, 0.15, 0.08, 0.15, 0.05);
        if (sl.getGameTime() % 30 == 0)
            sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), AparatosRegistro.INMERSION_BURBUJAS.get(), SoundSource.BLOCKS, 0.4F + frac * 0.5F,
                    0.9F + sl.random.nextFloat() * 0.2F);
    }

    private void tos(ServerLevel sl, LivingEntity v) {
        sl.playSound(null, v.getX(), v.getEyeY(), v.getZ(), AparatosRegistro.INMERSION_TOS.get(), v instanceof Player ? SoundSource.PLAYERS : SoundSource.NEUTRAL,
                1F, 0.92F + sl.random.nextFloat() * 0.16F);
        Vec3 m = v.getEyePosition().add(v.getLookAngle().scale(0.3));
        sl.sendParticles(ParticleTypes.SPLASH, m.x, m.y - 0.1, m.z, 12, 0.08, 0.05, 0.08, 0.15);
    }

    /** Lo que manda la víctima cada 10 ticks (ya recortado a 0..10). */
    void informe(int fuera) {
        if (level == null) return;
        fueraFrac = fuera / 10F;
        ultimoInforme = level.getGameTime();
    }

    private void empezarJuego(ServerPlayer sp) {
        juego = true;
        if (level != null) ultimoInforme = level.getGameTime() + 20;   // margen para que arranque en el cliente
        com.miscelanea.tortura.qte.Qte.cancelar(sp);
        AparatosRed.a(sp, new AparatoJuegoPacket(AparatoJuegoPacket.INMERSION, worldPosition, sp.getRandom().nextLong() | 1L, 0.5F));
    }

    private void pararJuego(ServerPlayer sp) {
        juego = false;
        AparatosRed.a(sp, new AparatoJuegoPacket(AparatoJuegoPacket.PARAR, worldPosition, 0, 0F));
    }

    @Override
    protected void alMeter(LivingEntity v, @Nullable Player quien) {
        sumergida = false;
        juego = false;
        fueraFrac = 0F;
    }

    @Override
    protected void alSacar(@Nullable LivingEntity v) {
        if (juego && v instanceof ServerPlayer sp) pararJuego(sp);
        juego = false;
        sumergida = false;
    }

    // ------------------------------------------------------------------ cliente
    @Override
    protected void tickVisual() {
        if (!iniciado) {
            iniciado = true;
            brazoCli = brazoO1 = brazoO2 = brazo;
        }
        // el asiento (que se mueve antes que el aparato en cada tick) usa brazoCli; el renderer dibuja entre los dos
        // anteriores, así la silla y la víctima van a la par
        brazoO2 = brazoO1;
        brazoO1 = brazoCli;
        brazo = mover(brazo);   // predicción de lo que hará el servidor hasta la próxima sincronización
        brazoCli += (brazo - brazoCli) * 0.6F;
        if (Math.abs(brazo - brazoCli) < 0.0005F) brazoCli = brazo;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(3.5);
    }

    // ------------------------------------------------------------------ guardar / sincronizar
    @Override
    protected void guardar(CompoundTag t) {
        t.putFloat("Brazo", brazo);
        t.putFloat("Objetivo", objetivo);
        t.putBoolean("Bloqueada", bloqueada);
        t.putBoolean("EnAgua", enAgua);
        t.putInt("Goteo", goteo);
    }

    @Override
    protected void cargar(CompoundTag t) {
        brazo = Mth.clamp(t.getFloat("Brazo"), 0F, 1F);
        objetivo = Mth.clamp(t.getFloat("Objetivo"), 0F, 1F);
        bloqueada = t.getBoolean("Bloqueada");
        enAgua = t.getBoolean("EnAgua");
        goteo = t.getInt("Goteo");
    }
}
