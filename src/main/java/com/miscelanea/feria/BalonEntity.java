package com.miscelanea.feria;

import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Balón de la máquina de canastas. Fuera de la máquina vuela con su parábola y bota contra los bloques. Dentro de la
 * jaula usa la forma de la máquina (coordenadas locales): paredes, red del techo, tablero, aro (rebota en el hierro),
 * red y la rampa por la que rueda de vuelta a la bandeja del jugador. Al llegar a la bandeja vuelve a la máquina.
 * <p>Un balón "suelto" (sin máquina, sacado del inventario creativo) solo bota un rato y desaparece.
 */
public class BalonEntity extends Entity {
    private static final EntityDataAccessor<Optional<BlockPos>> MAQ = SynchedEntityData.defineId(BalonEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** Radio del balón para los choques, gravedad por tic. El centro del balón está 0.12 por encima de la entidad. */
    public static final double R = 0.11, G = 0.04, CENTRO = 0.12;
    // ---- forma de la máquina (coordenadas locales: x a lo ancho, z hacia el fondo, y desde el suelo del bloque)
    static final double PARED_X = 0.4375, FONDO_Z = 0.41, TECHO_Y = 1.975, TECHO_DESDE_Z = -0.45;
    static final double LABIO_Z = -0.7625, PENDIENTE = 0.4, RAMPA_Y0 = 0.231, RAMPA_Z0 = -0.5625, RAMPA_MAX = 0.594;
    static final double TUBO = 0.016;

    public boolean anotado;
    public double distancia;
    public long juego;
    public float giro, giroO;
    private int quieto;
    private float golpeMax;
    private boolean golpeAro;

    public BalonEntity(EntityType<? extends BalonEntity> t, Level l) {
        super(t, l);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(MAQ, Optional.empty());
    }

    /** Lo liga a una partida de una máquina: cuenta canastas y vuelve a la bandeja. */
    public void preparar(MaquinaAltaBlockEntity be, long juego, double distancia) {
        entityData.set(MAQ, Optional.of(be.getBlockPos()));
        this.juego = juego;
        this.distancia = distancia;
    }

    public boolean ligado() {
        return entityData.get(MAQ).isPresent();
    }

    @Nullable
    MaquinaAltaBlockEntity maquina() {
        Optional<BlockPos> p = entityData.get(MAQ);
        if (p.isEmpty() || !level().isLoaded(p.get())) return null;
        return level().getBlockEntity(p.get()) instanceof MaquinaAltaBlockEntity be && be.tipo() == MaquinaAltaBlock.CANASTA ? be : null;
    }

    @Override
    public void tick() {
        super.tick();
        giroO = giro;
        MaquinaAltaBlockEntity be = maquina();
        boolean servidor = !level().isClientSide;
        // la partida acabó (o la máquina ya no está): el balón desaparece
        if (servidor && ligado() && (be == null || !be.activo || be.inicio != juego)) {
            discard();
            return;
        }
        Vec3 v = getDeltaMovement();
        giro += (float) Math.min(40, v.length() * 140);
        golpeMax = 0;
        golpeAro = false;
        if (be != null) {
            Vec3 c = be.local(position().add(0, CENTRO, 0)), lv = be.localDir(v);
            if (dentro(c)) {
                noPhysics = true;
                Vec3[] r = simular(be, c, lv);
                c = r[0];
                lv = r[1];
                Vec3 w = be.mundo(c.x, c.y, c.z);
                setPos(w.x, w.y - CENTRO, w.z);
                setDeltaMovement(be.mundoDir(lv));
                sonidos();
                if (servidor) {
                    quieto = lv.lengthSqr() < 0.0009 ? quieto + 1 : 0;
                    // en la bandeja (o atascado en la rampa): vuelve a la máquina para que el jugador lo coja
                    boolean bandeja = c.z < -0.52 && c.y < 0.45 && lv.lengthSqr() < 0.0016;
                    if (bandeja || quieto > 30 || tickCount > 400) {
                        be.devolverBalon(juego);
                        discard();
                    }
                }
                return;
            }
        }
        noPhysics = false;
        // fuera de la máquina: gravedad acumulada de verdad (antes se calculaba pero no se guardaba y volaba recto)
        v = new Vec3(v.x, v.y - G, v.z);
        setDeltaMovement(v);
        Vec3 antes = v;
        move(MoverType.SELF, v);
        Vec3 d = getDeltaMovement();
        if (horizontalCollision) {
            double nx = Math.abs(d.x) < 1e-6 && Math.abs(antes.x) > 1e-6 ? -antes.x * 0.6 : d.x;
            double nz = Math.abs(d.z) < 1e-6 && Math.abs(antes.z) > 1e-6 ? -antes.z * 0.6 : d.z;
            d = new Vec3(nx, d.y, nz);
            golpeMax = Math.max(golpeMax, (float) antes.horizontalDistance());
        }
        if (verticalCollision) {
            if (antes.y < -0.08) {
                d = new Vec3(d.x * 0.8, -antes.y * 0.6, d.z * 0.8);
                golpeMax = Math.max(golpeMax, (float) -antes.y);
            } else if (antes.y > 0) d = new Vec3(d.x, -antes.y * 0.3, d.z);
            else d = new Vec3(d.x * 0.85, 0, d.z * 0.85);
        }
        setDeltaMovement(d.scale(0.99));
        sonidos();
        if (servidor) {
            quieto = onGround() && d.lengthSqr() < 0.0004 ? quieto + 1 : 0;
            if (ligado()) {
                // se ha salido de la máquina: al rato vuelve a la bandeja (como si lo devolviera el feriante)
                if (quieto > 40 || tickCount > 200 || getY() < level().getMinBuildHeight()) {
                    if (be != null) be.devolverBalon(juego);
                    discard();
                }
            } else if (tickCount > 90) discard();
        }
    }

    private void sonidos() {
        if (level().isClientSide || golpeMax < 0.06F) return;
        level().playSound(null, getX(), getY(), getZ(), ModSounds.FE_BOTE.get(), SoundSource.NEUTRAL, Math.min(1F, 0.3F + golpeMax * 2), golpeAro ? 1.7F : 1F);
    }

    /** ¿Está dentro de la jaula de la máquina (incluida la bandeja de delante)? */
    static boolean dentro(Vec3 c) {
        return Math.abs(c.x) < 0.5 && c.z > -0.85 && c.z < 0.5 && c.y > -0.1 && c.y < 2.05;
    }

    /** Altura del suelo de la rampa (sube hacia el fondo) en la z local dada. */
    static double rampa(double z) {
        return Math.min(RAMPA_MAX, RAMPA_Y0 + PENDIENTE * (z - RAMPA_Z0));
    }

    /** Un tic de física dentro de la máquina, en varios pasitos para que no atraviese el aro. Devuelve {posición, velocidad}. */
    private Vec3[] simular(MaquinaAltaBlockEntity be, Vec3 c, Vec3 v) {
        double[] aro = MaquinaAltaBlockEntity.aro(level().getGameTime() - be.inicio);
        double hx = aro[0], hy = aro[1], hz = aro[2], ra = MaquinaAltaBlockEntity.ARO_R;
        int n = Mth.clamp((int) Math.ceil(v.length() / 0.04), 1, 10);
        double dt = 1.0 / n;
        double x = c.x, y = c.y, z = c.z, vx = v.x, vy = v.y, vz = v.z;
        for (int k = 0; k < n; k++) {
            vy -= G * dt;
            double yAntes = y;
            x += vx * dt;
            y += vy * dt;
            z += vz * dt;
            // ¡canasta! el centro del balón cruza hacia abajo el plano del aro por dentro del aro
            if (!anotado && yAntes > hy && y <= hy && vy < 0 && Math.hypot(x - hx, z - hz) < ra) {
                anotado = true;
                if (!level().isClientSide) be.anotar(this);
            }
            // paredes de los lados
            if (x + R > PARED_X) {
                x = PARED_X - R;
                if (vx > 0) vx = -vx * 0.4;
                golpe(Math.abs(v.x));
            } else if (x - R < -PARED_X) {
                x = -PARED_X + R;
                if (vx < 0) vx = -vx * 0.4;
                golpe(Math.abs(v.x));
            }
            // tablero (arriba rebota bien) y red del fondo (abajo casi no)
            if (z + R > FONDO_Z) {
                z = FONDO_Z - R;
                if (vz > 0) {
                    golpe(vz);
                    vz = -vz * (y > 1.45 ? 0.55 : 0.25);
                    vx *= 0.9;
                    vy *= 0.9;
                }
            }
            // red del techo
            if (z > TECHO_DESDE_Z && y + R > TECHO_Y) {
                y = TECHO_Y - R;
                if (vy > 0) vy = -vy * 0.2;
            }
            // labio delantero de la bandeja
            if (z - R < LABIO_Z && y < 0.45) {
                z = LABIO_Z + R;
                if (vz < 0) vz = -vz * 0.2;
            }
            // suelo: la rampa inclinada (rueda sola hacia la bandeja) o la parte plana del fondo
            double ys = rampa(z);
            boolean llano = ys >= RAMPA_MAX;
            double ny = llano ? 1 : 1 / Math.sqrt(1 + PENDIENTE * PENDIENTE), nz = llano ? 0 : -PENDIENTE * ny;
            double dist = (y - ys) * ny;
            if (dist < R) {
                double pen = R - dist;
                y += ny * pen;
                z += nz * pen;
                double vn = vy * ny + vz * nz;
                if (vn < 0) {
                    golpe((float) -vn);
                    double e = vn < -0.08 ? 0.45 : 0;   // bota si cae fuerte; si no, rueda
                    vy -= ny * vn * (1 + e);
                    vz -= nz * vn * (1 + e);
                }
                // rodando: un poco de rozamiento
                vx *= Math.pow(0.97, dt);
                vz *= Math.pow(0.995, dt);
            }
            // el aro: choque contra el punto más cercano del hierro (un toro)
            double qx = x - hx, qz = z - hz, rho = Math.hypot(qx, qz);
            if (rho > 1e-6) {
                double px = hx + qx / rho * ra, pz = hz + qz / rho * ra;
                double dx = x - px, dy = y - hy, dz = z - pz, dd = Math.sqrt(dx * dx + dy * dy + dz * dz), min = R + TUBO;
                if (dd < min && dd > 1e-6) {
                    double mx = dx / dd, my = dy / dd, mz = dz / dd;
                    x = px + mx * min;
                    y = hy + my * min;
                    z = pz + mz * min;
                    double vn = vx * mx + vy * my + vz * mz;
                    if (vn < 0) {
                        golpe((float) -vn);
                        golpeAro = true;
                        // rebote en el hierro: pierde bastante y algo de velocidad tangencial
                        vx -= mx * vn * 1.55;
                        vy -= my * vn * 1.55;
                        vz -= mz * vn * 1.55;
                        vx *= 0.92;
                        vy *= 0.92;
                        vz *= 0.92;
                    }
                }
            }
        }
        // la red frena el balón que acaba de entrar
        if (anotado && y < hy && y > hy - 0.3 && Math.hypot(x - hx, z - hz) < ra) {
            vx *= 0.6;
            vz *= 0.6;
            vy *= 0.85;
        }
        vx *= 0.995;
        vy *= 0.995;
        vz *= 0.995;
        return new Vec3[]{new Vec3(x, y, z), new Vec3(vx, vy, vz)};
    }

    private void golpe(double f) {
        golpeMax = Math.max(golpeMax, (float) f);
    }

    /** No se guarda: al descargarse el chunk o reiniciar el servidor, la máquina ya no lo cuenta. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
