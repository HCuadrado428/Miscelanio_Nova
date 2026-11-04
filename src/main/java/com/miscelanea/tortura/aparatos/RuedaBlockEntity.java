package com.miscelanea.tortura.aparatos;

import com.miscelanea.ConfigServidor;
import com.miscelanea.bromas.BromasRed;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Rueda de Catalina. La víctima va atada en aspa (muñecas y tobillos, cuatro correas) delante de una rueda de carro
 * grande y gira con ella; en primera persona la cámara gira entera (RuedaCliente).
 * <ul>
 *   <li>Captor: clic derecho a la rueda (o rueda del ratón mirándola) = empujón; la velocidad se acumula y la rueda frena
 *       sola poco a poco. Crujidos de la madera y el trinquete según gira. Shift + clic derecho con la mano vacía = soltarla
 *       (como todos los aparatos).</li>
 *   <li>Víctima (jugador): mientras la rueda gira deprisa salen rondas del minijuego del MAREO (en el cliente): la vista
 *       se le va sola y tiene que mantener la mirilla en el blanco con el ratón (que, con la pantalla girada, no va
 *       hacia donde parece). Superar {@link ConfigServidor#ruedaAciertosPorCorrea} rondas suelta una correa; con las
 *       cuatro sueltas, se escapa. Cada fallo: náuseas, un moratón y daño moderado. Hasta
 *       {@link ConfigServidor#ruedaMuerteTicks} atado no puede bajar de un corazón; después, un fallo puede matar (fundido a
 *       negro de 2 s y muerte con su mensaje).</li>
 *   <li>Mobs: solo dan vueltas (y se marean: algo de daño, sin matar hasta el mismo tiempo).</li>
 * </ul>
 * Medidas (con el modelo mirando al norte, ver RuedaRenderer y scratchpad/gen_bromas.py): eje a {@link #EJE_Y} sobre el
 * suelo del bloque; plano de la rueda {@link #RUEDA_ATRAS} detrás del centro; cuerpo de la víctima {@link #DELANTE} por
 * delante. Un JUGADOR tiene los ojos en el eje (así la cámara gira sobre sí misma): su posición es el eje menos la
 * altura de los ojos y el cliente sube el cuerpo al dibujarlo para que el centro del cuerpo quede en el eje.
 */
public class RuedaBlockEntity extends AparatoBlockEntity {
    public static final double EJE_Y = 1.35, DELANTE = 0.07, RUEDA_ATRAS = 2.5 / 16.0;
    public static final float VEL_MAX = 24F, ROCE = 0.985F, VEL_JUEGO = 4F, IMPULSO_CLIC = 3F, IMPULSO_RUEDA = 1.5F;
    public static final int DURACION_RONDA = 100, PAUSA_RONDA = 50, TODAS = 0b1111;
    /** % de la ronda que hay que estar en el blanco. */
    public static final int ACIERTO_PCT = 55;

    // ---- servidor (se guarda y se sincroniza)
    private float angulo, vel;
    private int correas = TODAS, aciertos, montadoT, fundido, enfriarRonda = 40, crujidoT;
    private long rondaInicio = -1;

    // ---- cliente
    public double angCli, angCliO;
    private float velCli, correccion;
    private boolean iniciado;
    private int muesca, sonidoT;

    public RuedaBlockEntity(BlockPos p, BlockState s) {
        super(RuedaRegistro.RUEDA_BE.get(), p, s);
    }

    @Override
    public int tipo() {
        return AparatosVictimas.RUEDA;
    }

    // ------------------------------------------------------------------ consultas
    public int correas() {
        return correas;
    }

    public int aciertos() {
        return aciertos;
    }

    /** Velocidad (grados por tick, con signo) que se sabe en este lado. */
    public float velocidad() {
        return level != null && level.isClientSide ? velCli : vel;
    }

    /** Vueltas por minuto. */
    public int rpm() {
        return Math.round(Math.abs(velocidad()) * 20F * 60F / 360F);
    }

    public float dificultad() {
        return Mth.clamp((Math.abs(vel) - VEL_JUEGO) / (VEL_MAX - VEL_JUEGO), 0F, 1F);
    }

    /** Ángulo de la rueda que se dibuja (grados, sin acotar). */
    public float anguloVis(float pt) {
        return (float) Mth.lerp(pt, angCliO, angCli);
    }

    /** Vector hacia el frente (hacia donde mira la víctima). */
    public Vec3 eje() {
        Direction f = frente();
        return new Vec3(f.getStepX(), 0, f.getStepZ());
    }

    /** Centro del cuerpo de la víctima (en el eje de la rueda). */
    public Vec3 cubo() {
        return Vec3.atBottomCenterOf(worldPosition).add(eje().scale(DELANTE)).add(0, EJE_Y, 0);
    }

    @Override
    public Vec3 sitioVictima(LivingEntity v) {
        Vec3 c = cubo();
        double baja = v instanceof Player ? v.getEyeHeight(net.minecraft.world.entity.Pose.STANDING) : v.getBbHeight() * 0.5;
        return c.subtract(0, baja, 0);
    }

    @Override
    public boolean sentado() {
        return false;
    }

    @Override
    public float giroCabeza() {
        return 60F;
    }

    // ------------------------------------------------------------------ uso
    @Override
    protected void usarExtra(Player p, boolean anexo, BlockHitResult hit) {
        impulso(p, 1, true);
    }

    /** Empujón del captor: clic derecho (más fuerte) o rueda del ratón (dir = ±1). */
    public void impulso(Player p, int dir, boolean clic) {
        if (ocupado() && !puedeManejar(p)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.aparato.no_tuyo").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (fundido > 0) return;
        vel = Mth.clamp(vel + dir * (clic ? IMPULSO_CLIC : IMPULSO_RUEDA), -VEL_MAX, VEL_MAX);
        sonido(AparatosRegistro.POTRO_CARRACA.get(), 0.7F, 0.8F + Math.abs(vel) / VEL_MAX * 0.5F);
        p.displayClientMessage(Component.translatable("msg.miscelanea.rueda.velocidad", rpm()).withStyle(ChatFormatting.GOLD), true);
        sincronizar();
    }

    // ------------------------------------------------------------------ minijuego (servidor)
    private void iniciarRonda(ServerPlayer sp) {
        rondaInicio = sp.serverLevel().getGameTime();
        BromasRed.a(sp, new BromasRed.RuedaS2C(BromasRed.RuedaS2C.JUEGO, worldPosition, DURACION_RONDA, dificultad()));
    }

    /** La víctima informa del % de la ronda que ha estado en el blanco. */
    public void resultado(ServerPlayer sp, int pct) {
        if (rondaInicio < 0 || !(level instanceof ServerLevel sl) || fundido > 0) return;
        long t = sl.getGameTime() - rondaInicio;
        rondaInicio = -1;
        enfriarRonda = PAUSA_RONDA;
        // antes de tiempo no vale (el cliente no puede acabar la ronda antes)
        if (t >= DURACION_RONDA * 3 / 4 && pct >= ACIERTO_PCT) acierto(sl, sp);
        else fallo(sl, sp);
    }

    private void acierto(ServerLevel sl, ServerPlayer sp) {
        aciertos++;
        int n = ConfigServidor.ruedaAciertosPorCorrea();
        if (aciertos < n) {
            sp.displayClientMessage(Component.translatable("msg.miscelanea.rueda.bien", aciertos, n).withStyle(ChatFormatting.GREEN), true);
            sl.playSound(null, sp.getX(), sp.getY() + 1, sp.getZ(), AparatosRegistro.PENDULO_CORREA.get(), SoundSource.PLAYERS, 0.6F, 1.3F);
            marcar();
            return;
        }
        aciertos = 0;
        correas &= correas - 1;   // suelta la primera que quede (muñeca der., muñeca izq., tobillo der., tobillo izq.)
        int quedan = Integer.bitCount(correas);
        sonido(AparatosRegistro.COLGADO_DESATAR.get(), 1F, 1.1F);
        if (quedan == 0) {
            sp.displayClientMessage(Component.translatable("msg.miscelanea.rueda.libre_tu").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
            avisarCaptor(sl, Component.translatable("msg.miscelanea.rueda.libre", sp.getDisplayName()).withStyle(ChatFormatting.RED));
            sacar(true);
            return;
        }
        sp.displayClientMessage(Component.translatable("msg.miscelanea.rueda.correa", quedan).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        avisarCaptor(sl, Component.translatable("msg.miscelanea.rueda.correa_captor", sp.getDisplayName(), quedan).withStyle(ChatFormatting.GOLD));
        marcar();
    }

    private void fallo(ServerLevel sl, ServerPlayer sp) {
        float dano = ConfigServidor.ruedaDanoFallo() * (1F + dificultad() * 0.75F);
        sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, false));
        Parte[] ext = {Parte.BRAZO_DER, Parte.BRAZO_IZQ, Parte.PIERNA_DER, Parte.PIERNA_IZQ};
        Aparatos.herida(sp, ext[sp.getRandom().nextInt(ext.length)], Tipo.MORATON, 1);
        float vida = sp.getHealth() + sp.getAbsorptionAmount();
        if (dano >= vida) {
            if (ConfigServidor.ruedaPuedeMatar() && montadoT >= ConfigServidor.ruedaMuerteTicks()) {
                fundido = 40;
                BromasRed.a(sp, new BromasRed.RuedaS2C(BromasRed.RuedaS2C.FUNDIDO, worldPosition, fundido, 0F));
                sp.displayClientMessage(Component.translatable("msg.miscelanea.rueda.apaga").withStyle(ChatFormatting.DARK_GRAY), true);
                return;
            }
            dano = Math.max(0F, vida - 2F);
        }
        if (dano > 0F) Aparatos.danar(sp, RuedaRegistro.DANO_RUEDA, dano, captor);
        sp.displayClientMessage(Component.translatable("msg.miscelanea.rueda.mareo").withStyle(ChatFormatting.RED), true);
    }

    private void avisarCaptor(ServerLevel sl, Component c) {
        if (captor == null) return;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(captor);
        if (p != null) p.displayClientMessage(c, true);
    }

    // ------------------------------------------------------------------ tick (servidor)
    @Override
    protected void tickAparato(ServerLevel sl) {
        angulo = (angulo + vel) % 360F;
        if (angulo < 0) angulo += 360F;
        vel *= ROCE;
        if (Math.abs(vel) < 0.05F) vel = 0F;
        float rapido = Math.abs(vel);
        if (rapido > 0F && sl.getGameTime() % 10 == 0) marcar();
        // la madera cruje más a menudo cuanto más deprisa
        if (rapido > 1F && ++crujidoT >= Math.max(8, (int) (40 - rapido * 1.5F))) {
            crujidoT = 0;
            sonido(AparatosRegistro.INMERSION_CRUJIDO.get(), 0.35F + rapido / 40F, 0.6F + rapido / 60F + sl.random.nextFloat() * 0.1F);
        }
    }

    @Override
    protected void tickVictima(ServerLevel sl, LivingEntity v) {
        montadoT++;
        if (fundido > 0) {
            if (--fundido == 0) Aparatos.matar(v, RuedaRegistro.DANO_RUEDA, captor);
            return;
        }
        float rapido = Math.abs(vel);
        if (v instanceof ServerPlayer sp) {
            if (rondaInicio >= 0) {
                // no ha contestado (desconectado, menú...): cuenta como fallo
                if (sl.getGameTime() - rondaInicio > DURACION_RONDA + 60) {
                    rondaInicio = -1;
                    enfriarRonda = PAUSA_RONDA;
                    fallo(sl, sp);
                }
            } else if (enfriarRonda > 0) enfriarRonda--;
            else if (rapido >= VEL_JUEGO) iniciarRonda(sp);
            if (rapido >= VEL_JUEGO * 2.5F && montadoT % 100 == 0)
                sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
        } else if (rapido >= VEL_JUEGO && montadoT % 60 == 0) {
            // un mob se marea: algo de daño (sin matar hasta el mismo tiempo que un jugador)
            float d = 1F;
            boolean puede = ConfigServidor.ruedaPuedeMatar() && montadoT >= ConfigServidor.ruedaMuerteTicks();
            if (!puede) d = Math.min(d, Math.max(0F, v.getHealth() - 1F));
            if (d > 0F) Aparatos.danar(v, RuedaRegistro.DANO_RUEDA, d, captor);
            sl.sendParticles(ParticleTypes.EFFECT, v.getX(), v.getY() + v.getBbHeight(), v.getZ(), 5, 0.3, 0.2, 0.3, 0.02);
        }
    }

    @Override
    protected void alMeter(LivingEntity v, @Nullable Player quien) {
        correas = TODAS;
        aciertos = 0;
        montadoT = 0;
        fundido = 0;
        rondaInicio = -1;
        enfriarRonda = 40;
        if (v instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.translatable("msg.miscelanea.rueda.dentro_ayuda").withStyle(ChatFormatting.GRAY), false);
    }

    @Override
    protected void alSacar(@Nullable LivingEntity v) {
        rondaInicio = -1;
        fundido = 0;
        aciertos = 0;
        correas = TODAS;
        if (v instanceof ServerPlayer sp) {
            BromasRed.a(sp, new BromasRed.RuedaS2C(BromasRed.RuedaS2C.PARAR, worldPosition, 0, 0F));
            sp.removeEffect(MobEffects.CONFUSION);
        }
    }

    // ------------------------------------------------------------------ cliente
    @Override
    protected void tickVisual() {
        if (!iniciado) {
            iniciado = true;
            angCli = angCliO = angulo;
            velCli = vel;
            correccion = 0F;
        }
        angCliO = angCli;
        velCli *= ROCE;
        if (Math.abs(velCli) < 0.05F) velCli = 0F;
        float c = correccion * 0.25F;
        correccion -= c;
        angCli += velCli + c;
        if (Math.abs(angCli) > 36000.0) {
            // que no crezca sin fin (precisión al pasarlo a float)
            double vueltas = Math.floor(angCli / 360.0) * 360.0;
            angCli -= vueltas;
            angCliO -= vueltas;
        }
        // trinquete: un clic cada 30º
        int m = Mth.floor(angCli / 30.0);
        if (sonidoT > 0) sonidoT--;
        if (m != muesca) {
            muesca = m;
            if (level != null && sonidoT == 0 && Math.abs(velCli) > 0.3F) {
                sonidoT = 2;
                Vec3 p = cubo();
                level.playLocalSound(p.x, p.y, p.z, AparatosRegistro.POTRO_CARRACA.get(), SoundSource.BLOCKS,
                        0.25F + Math.min(0.3F, Math.abs(velCli) / 40F), 1.3F + Math.abs(velCli) / VEL_MAX * 0.5F, false);
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.0, 0, 2.0).expandTowards(0, 2.5, 0);
    }

    // ------------------------------------------------------------------ guardar / sincronizar
    @Override
    protected void guardar(CompoundTag t) {
        t.putFloat("Angulo", angulo);
        t.putFloat("Vel", vel);
        t.putInt("Correas", correas);
        t.putInt("Aciertos", aciertos);
        t.putInt("Montado", montadoT);
    }

    @Override
    protected void cargar(CompoundTag t) {
        angulo = t.getFloat("Angulo");
        vel = Mth.clamp(t.getFloat("Vel"), -VEL_MAX, VEL_MAX);
        correas = t.contains("Correas") ? t.getInt("Correas") & TODAS : TODAS;
        aciertos = t.getInt("Aciertos");
        montadoT = t.getInt("Montado");
        if (level != null && level.isClientSide) {
            velCli = vel;
            if (!iniciado) {
                iniciado = true;
                angCli = angCliO = angulo;
                correccion = 0F;
            } else correccion = Mth.wrapDegrees(angulo - (float) (angCli % 360.0));
        }
    }
}
