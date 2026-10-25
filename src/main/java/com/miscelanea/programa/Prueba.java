package com.miscelanea.programa;

import com.miscelanea.Miscelanea;
import com.miscelanea.carcel.CadaverBlockEntity;
import com.miscelanea.carcel.CarroChivato;
import com.miscelanea.carcel.FluorescenteBlock;
import com.miscelanea.carcel.GafasItem;
import com.miscelanea.carcel.LlaveItem;
import com.miscelanea.carcel.MonitorBlock;
import com.miscelanea.carcel.RejaBlock;
import com.miscelanea.carcel.Ruido;
import com.miscelanea.carcel.Simbolo;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModBlocks;
import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * La prueba "Matar al chivato" (Programa 1 · Comisaría) en la dimensión Programa. Una partida a la vez,
 * compartida por todos los jugadores que entren. Etapas y mecánicas como en The Outlast Trials.
 */
public final class Prueba {
    public static final ResourceKey<Level> DIM = Programa.DIM;
    public static final BlockPos ORIGEN = new BlockPos(0, 56, 0);
    private static final String TAG = "MiscelaneaPrograma";
    public static final int VIDAS = 3;

    public enum Etapa { ENTRAR, SEGURIDAD, BUSCAR, EMPUJAR, GENERADOR, LLAVES, EJECUTAR, SALIR, FIN }

    private static boolean activa;
    private static Etapa etapa = Etapa.ENTRAR;
    private static long inicio;
    private static Mapa.Datos datos;
    private static Mapa.Colocado colocado;
    private static final int[] simbolos = new int[4];
    private static int manivela;
    private static int muertes, vidas;
    private static int finTimer;
    private static UUID carroId, coyleId;
    private static Vec3 carroInicio;
    private static final Set<UUID> dentro = new HashSet<>();
    private static final List<BlockPos> generadores = new ArrayList<>();
    private static final Map<BlockPos, BlockPos> primerInterruptor = new HashMap<>();
    private static final Set<BlockPos> cajasTrampa = new HashSet<>();
    private static final Map<BlockPos, List<BlockPos>> puertasTrampa = new HashMap<>();
    private static final Map<BlockPos, Long> alarmas = new HashMap<>();

    private Prueba() {}

    public static boolean activa() {
        return activa;
    }

    public static boolean dentro(Player p) {
        return activa && dentro.contains(p.getUUID()) && p.level().dimension() == DIM;
    }

    public static int jugadoresDentro() {
        return dentro.size();
    }

    public static int manivela() {
        return manivela;
    }

    public static void setManivela(int n) {
        manivela = n;
    }

    private static BlockPos abs(BlockPos rel) {
        return ORIGEN.offset(rel);
    }

    // =================================================================== entrar / salir

    public static void entrar(ServerPlayer p) {
        entrar(p, Rigs.Rig.STUN);
    }

    public static void entrar(ServerPlayer p, Rigs.Rig rig) {
        MinecraftServer srv = p.server;
        ServerLevel lvl = srv.getLevel(DIM);
        if (lvl == null || p.level().dimension() == DIM) return;
        if (activa && etapa == Etapa.FIN) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.programa_ocupado"), true);
            return;
        }
        if (!activa) {
            try {
                construir(lvl);
            } catch (Exception e) {
                Miscelanea.LOGGER.error("No se pudo construir el mapa del Programa", e);
                p.displayClientMessage(Component.literal("Error: " + e.getMessage()), false);
                return;
            }
        }
        guardar(p);
        Mapa.Marca sp = datos.una("spawn");
        BlockPos s = abs(sp.pos());
        p.teleportTo(lvl, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, sp.entero("yaw", 0), 0);
        preparar(p);
        Jugadores.reiniciar(p, rig);
        dentro.add(p.getUUID());
        lvl.playSound(null, s, SoundEvents.IRON_DOOR_CLOSE, SoundSource.AMBIENT, 1.0F, 0.5F);
        enviar(p, true);
    }

    private static void guardar(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.put("Inv", p.getInventory().save(new ListTag()));
        t.putString("Dim", p.level().dimension().location().toString());
        t.putDouble("X", p.getX());
        t.putDouble("Y", p.getY());
        t.putDouble("Z", p.getZ());
        t.putFloat("Yaw", p.getYRot());
        t.putInt("Modo", p.gameMode.getGameModeForPlayer().getId());
        t.putFloat("Vida", p.getHealth());
        t.putInt("Comida", p.getFoodData().getFoodLevel());
        p.getPersistentData().put(TAG, t);
    }

    private static void preparar(ServerPlayer p) {
        p.getInventory().clearContent();
        p.removeAllEffects();
        p.setGameMode(GameType.ADVENTURE);
        p.setHealth(p.getMaxHealth());
        ItemStack gafas = new ItemStack(ModItems.GAFAS.get());
        gafas.getOrCreateTag().putInt("Bateria", GafasItem.BATERIA_MAX);
        p.setItemSlot(EquipmentSlot.HEAD, gafas);
        p.setInvulnerable(false);
    }

    public static boolean restaurar(ServerPlayer p, boolean teletransportar) {
        CompoundTag t = p.getPersistentData().getCompound(TAG);
        if (t.isEmpty()) return false;
        p.getPersistentData().remove(TAG);
        dentro.remove(p.getUUID());
        Jugadores.quitar(p);
        Acciones.parar(p);
        p.getInventory().clearContent();
        p.getInventory().load(t.getList("Inv", Tag.TAG_COMPOUND));
        p.removeAllEffects();
        p.setInvulnerable(false);
        p.setGameMode(GameType.byId(t.getInt("Modo")));
        p.setHealth(Math.max(1.0F, t.getFloat("Vida")));
        p.getFoodData().setFoodLevel(t.getInt("Comida"));
        if (teletransportar) {
            ResourceKey<Level> k = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(t.getString("Dim")));
            ServerLevel dest = p.server.getLevel(k);
            if (dest == null || k == DIM) dest = p.server.overworld();
            p.teleportTo(dest, t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"), t.getFloat("Yaw"), 0);
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), PruebaEstadoPacket.inactivo());
        return true;
    }

    public static void salir(ServerPlayer p) {
        if (!restaurar(p, true) && p.level().dimension() == DIM) {
            ServerLevel o = p.server.overworld();
            BlockPos s = o.getSharedSpawnPos();
            p.teleportTo(o, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0, 0);
        }
    }

    // =================================================================== construir

    private static void construir(ServerLevel lvl) throws Exception {
        datos = Mapa.cargar(lvl.getServer());
        AABB zona = new AABB(ORIGEN, ORIGEN.offset(datos.sx, datos.sy, datos.sz)).inflate(4);
        for (Entity e : lvl.getEntitiesOfClass(Entity.class, zona, e -> !(e instanceof Player))) e.discard();
        colocado = Mapa.colocar(lvl, ORIGEN, datos);
        RandomSource r = lvl.random;
        Random jr = new Random(r.nextLong());

        // Símbolos al azar para las 4 rejas
        List<Integer> sim = new ArrayList<>();
        for (int i = 0; i < Simbolo.TOTAL; i++) sim.add(i);
        Collections.shuffle(sim, jr);
        for (int g = 0; g < 4; g++) simbolos[g] = sim.get(g % sim.size());
        for (Mapa.Marca m : datos.de("reja")) {
            BlockPos p = abs(m.pos());
            BlockState s = lvl.getBlockState(p);
            if (s.getBlock() instanceof RejaBlock) lvl.setBlock(p, s.setValue(Simbolo.PROPIEDAD, simbolos[m.entero("g", 0)]), Block.UPDATE_CLIENTS);
        }
        for (Mapa.Marca m : datos.de("monitor")) {
            BlockPos p = abs(m.pos());
            BlockState s = lvl.getBlockState(p);
            if (!(s.getBlock() instanceof MonitorBlock)) {
                Direction d = Direction.byName(m.texto("facing"));
                s = ModBlocks.MONITOR.get().defaultBlockState().setValue(MonitorBlock.FACING, d != null ? d : Direction.NORTH);
            }
            lvl.setBlock(p, s.setValue(Simbolo.PROPIEDAD, simbolos[m.entero("reja", 0)]), Block.UPDATE_CLIENTS);
        }

        // Cadáveres: llave 0 en la sala KEY; 3 llaves en cadáveres al azar; el resto señuelos y trampas
        List<Mapa.Marca> cand = new ArrayList<>();
        for (Mapa.Marca m : datos.de("cadaver")) {
            if (m.entero("llave", -1) >= 0) ponerCadaver(lvl, m, m.entero("llave", 0), false);
            else if (m.bool("cand")) cand.add(m);
            else ponerCadaver(lvl, m, -1, false);
        }
        Collections.shuffle(cand, jr);
        for (int i = 0; i < cand.size(); i++) {
            Mapa.Marca m = cand.get(i);
            if (i < 3) ponerCadaver(lvl, m, i + 1, false);
            else ponerCadaver(lvl, m, -1, r.nextFloat() < 0.35F);
        }

        // Carteles
        for (Mapa.Marca m : datos.de("cartel")) {
            if (!(lvl.getBlockEntity(abs(m.pos())) instanceof SignBlockEntity sb)) continue;
            var lineas = m.datos().getAsJsonArray("texto");
            DyeColor color = DyeColor.byName(m.texto("color"), DyeColor.BLACK);
            SignText txt = new SignText().setColor(color).setHasGlowingText(color != DyeColor.BLACK);
            for (int i = 0; i < Math.min(2, lineas.size()); i++) txt = txt.setMessage(i + 1, Component.literal(lineas.get(i).getAsString()));
            sb.setText(txt, true);
            sb.setText(txt, false);
            sb.setWaxed(true);
            sb.setChanged();
            lvl.sendBlockUpdated(sb.getBlockPos(), sb.getBlockState(), sb.getBlockState(), 3);
        }

        // Generadores: hay 4 sitios y en cada partida funcionan 2 (como en el juego, cambian de sitio)
        generadores.clear();
        primerInterruptor.clear();
        List<BlockPos> gens = new ArrayList<>(colocado.generadores);
        Collections.shuffle(gens, jr);
        for (int i = 0; i < gens.size(); i++) {
            BlockPos g = gens.get(i);
            List<BlockPos> sws = new ArrayList<>();
            for (BlockPos q : BlockPos.betweenClosed(g.offset(-4, -1, -4), g.offset(4, 2, 4))) {
                if (lvl.getBlockState(q).getBlock() instanceof InterruptorBlock) sws.add(q.immutable());
            }
            if (i < 2) {
                generadores.add(g);
                if (!sws.isEmpty()) primerInterruptor.put(g, sws.get(jr.nextInt(sws.size())));
            } else {
                lvl.setBlock(g, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
                for (BlockPos q : sws) lvl.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        // Cajas de herramientas con trampa de gas
        cajasTrampa.clear();
        for (BlockPos c : colocado.cajas) if (r.nextFloat() < 0.3F) cajasTrampa.add(c);

        // Puertas: 4 con trampa (luz roja) y 3 atrancadas, distintas en cada partida
        puertasTrampa.clear();
        List<BlockPos> puertas = new ArrayList<>(colocado.puertas);
        Collections.shuffle(puertas, jr);
        int k = 0;
        for (BlockPos d : puertas) {
            BlockState s = lvl.getBlockState(d);
            if (!(s.getBlock() instanceof DoorBlock)) continue;
            if (k < 4) {
                List<BlockPos> luces = new ArrayList<>();
                Direction f = s.getValue(DoorBlock.FACING);
                for (Direction lado : new Direction[]{f, f.getOpposite()}) {
                    BlockPos lp = d.above(2).relative(lado);
                    if (lvl.getBlockState(lp).isAir()) {
                        lvl.setBlock(lp, ModBlocks.LUZ_TRAMPA.get().defaultBlockState().setValue(LuzBlock.FACING, lado), Block.UPDATE_ALL);
                        luces.add(lp);
                    }
                }
                puertasTrampa.put(d, luces);
            } else if (k < 7) {
                BlockState b = ModBlocks.BARRICADA.get().defaultBlockState();
                lvl.setBlock(d, b, Block.UPDATE_CLIENTS);
                lvl.setBlock(d.above(), b, Block.UPDATE_CLIENTS);
            } else break;
            k++;
        }

        alarmas.clear();
        for (BlockPos a : colocado.alarmas) alarmas.put(a, 0L);

        // Chivato en su celda
        Mapa.Marca mc = datos.una("carro");
        BlockPos cp = abs(mc.pos());
        CarroChivato carro = new CarroChivato(ModEntities.CARRO_CHIVATO.get(), lvl);
        carro.moveTo(cp.getX() + 0.5, cp.getY(), cp.getZ() + 0.5, mc.entero("yaw", 0), 0);
        lvl.addFreshEntity(carro);
        carroId = carro.getUUID();
        carroInicio = carro.position();

        // Gritones dormidos desde el principio
        for (Mapa.Marca m : datos.de("screamer")) ModEntities.GRITON.get().spawn(lvl, abs(m.pos()), MobSpawnType.EVENT);

        // Objetos sueltos al azar
        for (Mapa.Marca m : datos.de("botin")) {
            if (r.nextFloat() > 0.55F) continue;
            ItemStack it = Botin.suelo(r);
            if (it.isEmpty()) continue;
            BlockPos p = abs(m.pos());
            ItemEntity ie = new ItemEntity(lvl, p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5, it);
            ie.setDeltaMovement(Vec3.ZERO);
            ie.setUnlimitedLifetime();
            lvl.addFreshEntity(ie);
        }

        etapa = Etapa.ENTRAR;
        inicio = lvl.getGameTime();
        muertes = 0;
        vidas = VIDAS;
        manivela = 0;
        coyleId = null;
        finTimer = 0;
        activa = true;
    }

    private static void ponerCadaver(ServerLevel lvl, Mapa.Marca m, int reja, boolean trampa) {
        if (!(lvl.getBlockEntity(abs(m.pos())) instanceof CadaverBlockEntity be)) return;
        be.sacarContenido();
        be.setTrampa(trampa);
        if (reja >= 0) {
            be.setContenido(LlaveItem.con(ModItems.LLAVE.get(), simbolos[reja]));
            be.setSimbolo(simbolos[reja]);
        } else {
            be.setSimbolo(lvl.random.nextInt(Simbolo.TOTAL));
        }
    }

    // =================================================================== bucle

    public static void tick(MinecraftServer srv) {
        if (!activa) return;
        ServerLevel lvl = srv.getLevel(DIM);
        if (lvl == null) return;
        List<ServerPlayer> js = jugadores(lvl);
        if (js.isEmpty()) {
            activa = false;
            dentro.clear();
            return;
        }
        long t = lvl.getGameTime();
        CarroChivato carro = carroId != null && lvl.getEntity(carroId) instanceof CarroChivato c ? c : null;

        for (ServerPlayer p : js) {
            if (etapa != Etapa.FIN) Jugadores.tick(p, lvl);
            if (Jugadores.caido(p)) {
                p.setPose(Pose.SWIMMING);
                Acciones.poner(p, Acciones.Accion.CAIDO, 20);
            }
        }
        if (t % 5 == 0) alarmas(lvl, js, t);

        switch (etapa) {
            case ENTRAR -> {
                if (enZona(js, "entrada")) cambiar(lvl, Etapa.SEGURIDAD);
            }
            case BUSCAR -> {
                if (enZona(js, "celdas")) cambiar(lvl, Etapa.EMPUJAR);
            }
            case EMPUJAR -> {
                if (carro != null && carroInicio != null && carro.position().distanceTo(carroInicio) > 4.0) apagon(lvl);
            }
            case GENERADOR -> {
                if (t % 37 == 0 && !colocado.luces.isEmpty()) {
                    BlockPos p = colocado.luces.get(lvl.random.nextInt(colocado.luces.size()));
                    BlockState s = lvl.getBlockState(p);
                    if (s.getBlock() instanceof FluorescenteBlock) {
                        lvl.setBlock(p, s.setValue(FluorescenteBlock.LIT, true), Block.UPDATE_ALL);
                        lvl.scheduleTick(p, s.getBlock(), 3);
                    }
                }
            }
            case LLAVES -> {
                if (carro != null && carro.estado() >= CarroChivato.ENGANCHADO) {
                    cambiar(lvl, Etapa.EJECUTAR);
                    Mapa.Marca m = datos.una("coyle_ejecucion");
                    if (m != null) {
                        Mob c = ModEntities.JEFE.get().spawn(lvl, abs(m.pos()), MobSpawnType.EVENT);
                        if (c != null) coyleId = c.getUUID();
                        lvl.playSound(null, abs(m.pos()), ModSounds.JEFE_RESPIRA.get(), SoundSource.HOSTILE, 3.0F, 0.8F);
                    }
                }
            }
            case EJECUTAR -> {
                if (carro != null && carro.estado() == CarroChivato.MUERTO) {
                    cambiar(lvl, Etapa.SALIR);
                    Mapa.Marca m = datos.una("coyle_salida");
                    Entity c = coyleId != null ? lvl.getEntity(coyleId) : null;
                    if (m != null) {
                        BlockPos p = abs(m.pos());
                        if (c != null) c.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                        else {
                            Mob nuevo = ModEntities.JEFE.get().spawn(lvl, p, MobSpawnType.EVENT);
                            if (nuevo != null) coyleId = nuevo.getUUID();
                        }
                    }
                }
            }
            case FIN -> {
                if (--finTimer <= 0) {
                    for (ServerPlayer p : js) restaurar(p, true);
                    activa = false;
                    dentro.clear();
                    return;
                }
            }
            default -> {}
        }
        if (t % 5 == 0) for (ServerPlayer p : js) enviar(p, false);
    }

    private static List<ServerPlayer> jugadores(ServerLevel lvl) {
        List<ServerPlayer> l = new ArrayList<>();
        for (ServerPlayer p : lvl.players()) if (dentro.contains(p.getUUID())) l.add(p);
        return l;
    }

    private static boolean enZona(List<ServerPlayer> js, String id) {
        for (Mapa.Marca m : datos.de("zona")) {
            if (!m.texto("id").equals(id)) continue;
            var h = m.datos().getAsJsonArray("hasta");
            AABB caja = new AABB(abs(m.pos()), abs(new BlockPos(h.get(0).getAsInt(), h.get(1).getAsInt(), h.get(2).getAsInt())).offset(1, 1, 1));
            for (ServerPlayer p : js) if (caja.contains(p.position())) return true;
        }
        return false;
    }

    private static void cambiar(ServerLevel lvl, Etapa nueva) {
        etapa = nueva;
        for (ServerPlayer p : jugadores(lvl)) {
            p.playNotifySound(SoundEvents.UI_TOAST_IN, SoundSource.MASTER, 1.0F, 0.7F);
            enviar(p, false);
        }
    }

    private static void apagon(ServerLevel lvl) {
        luces(lvl, false);
        for (ServerPlayer p : jugadores(lvl)) {
            p.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 1.0F, 0.4F);
            p.playNotifySound(ModSounds.GRITO.get(), SoundSource.HOSTILE, 0.4F, 0.6F);
        }
        for (Mapa.Marca m : datos.de("grunt")) ModEntities.GUARDIA.get().spawn(lvl, abs(m.pos()), MobSpawnType.EVENT);
        for (Mapa.Marca m : datos.de("pusher")) ModEntities.PUSHER.get().spawn(lvl, abs(m.pos()), MobSpawnType.EVENT);
        cambiar(lvl, Etapa.GENERADOR);
    }

    private static void luces(ServerLevel lvl, boolean on) {
        for (BlockPos p : colocado.luces) {
            BlockState s = lvl.getBlockState(p);
            if (s.getBlock() instanceof FluorescenteBlock && s.getValue(FluorescenteBlock.LIT) != on) {
                lvl.setBlock(p, s.setValue(FluorescenteBlock.LIT, on), Block.UPDATE_ALL);
            }
        }
    }

    /** Alarmas de ruido: suenan si pasas cerca de pie (agachado no las disparas). */
    private static void alarmas(ServerLevel lvl, List<ServerPlayer> js, long t) {
        for (Map.Entry<BlockPos, Long> a : alarmas.entrySet()) {
            BlockPos p = a.getKey();
            BlockState s = lvl.getBlockState(p);
            if (!(s.getBlock() instanceof LuzBlock)) continue;
            if (s.getValue(LuzBlock.DISPARADA)) {
                if (t - a.getValue() > 100) lvl.setBlock(p, s.setValue(LuzBlock.DISPARADA, false), Block.UPDATE_ALL);
                else if ((t - a.getValue()) % 15 == 0) lvl.playSound(null, p, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.6F);
                continue;
            }
            for (ServerPlayer j : js) {
                if (j.isCrouching() || j.distanceToSqr(Vec3.atCenterOf(p)) > 12.25) continue;
                if (j.getDeltaMovement().horizontalDistanceSqr() < 0.0004 && !j.isSprinting()) continue;
                lvl.setBlock(p, s.setValue(LuzBlock.DISPARADA, true), Block.UPDATE_ALL);
                a.setValue(t);
                lvl.playSound(null, p, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.5F, 1.4F);
                Ruido.alertar(lvl, Vec3.atCenterOf(p), 32, j);
                break;
            }
        }
    }

    // =================================================================== avisos de los bloques

    public static void boton(ServerLevel lvl, BlockPos pos, ServerPlayer p) {
        if (!activa || lvl.dimension() != DIM) return;
        for (Mapa.Marca m : datos.de("boton")) {
            if (!abs(m.pos()).equals(pos)) continue;
            String id = m.texto("id");
            if (id.equals("seguridad") && (etapa == Etapa.SEGURIDAD || etapa == Etapa.ENTRAR)) {
                for (Mapa.Marca d : datos.de("puerta_celdas")) {
                    BlockPos dp = abs(d.pos());
                    BlockState s = lvl.getBlockState(dp);
                    if (s.getBlock() instanceof DoorBlock db) db.setOpen(p, lvl, s, dp, true);
                }
                lvl.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.5F, 0.5F);
                cambiar(lvl, Etapa.BUSCAR);
            } else if (id.equals("furgoneta") && etapa == Etapa.SALIR) {
                terminar(lvl, true);
            }
        }
    }

    public static void generadorCambio(ServerLevel lvl, ServerPlayer p) {
        if (activa) enviar(p, false);
    }

    private static int generadoresEncendidos(Level lvl) {
        int n = 0;
        for (BlockPos g : generadores) {
            BlockState s = lvl.getBlockState(g);
            if (s.getBlock() instanceof GeneradorBlock && s.getValue(GeneradorBlock.ENCENDIDO)) n++;
        }
        return n;
    }

    public static void generadorArrancado(ServerLevel lvl, ServerPlayer p) {
        if (!activa || lvl.dimension() != DIM) return;
        manivela = 0;
        if (generadoresEncendidos(lvl) < generadores.size()) {
            enviar(p, false);
            return;
        }
        luces(lvl, true);
        for (ServerPlayer j : jugadores(lvl)) j.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 1.0F, 0.6F);
        if (etapa == Etapa.GENERADOR) cambiar(lvl, Etapa.LLAVES);
    }

    /**
     * Los interruptores van en un orden (distinto cada partida). Si activas primero el que no es, saltan chispas,
     * se apagan los dos y hace ruido. Devuelve si se puede activar.
     */
    public static boolean interruptor(ServerLevel lvl, BlockPos pos, Player p) {
        if (!activa || lvl.dimension() != DIM) return true;
        for (Map.Entry<BlockPos, BlockPos> e : primerInterruptor.entrySet()) {
            if (e.getKey().distManhattan(pos) > 9) continue;
            BlockState primero = lvl.getBlockState(e.getValue());
            boolean primeroOn = primero.getBlock() instanceof InterruptorBlock && primero.getValue(InterruptorBlock.ON);
            if (pos.equals(e.getValue()) || primeroOn) return true;
            for (BlockPos q : BlockPos.betweenClosed(e.getKey().offset(-4, -1, -4), e.getKey().offset(4, 2, 4))) {
                BlockState s = lvl.getBlockState(q);
                if (s.getBlock() instanceof InterruptorBlock) lvl.setBlock(q, s.setValue(InterruptorBlock.ON, false), Block.UPDATE_ALL);
            }
            lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.3);
            lvl.playSound(null, pos, ModSounds.DESCARGA.get(), SoundSource.BLOCKS, 1.0F, 1.3F);
            Ruido.alertar(lvl, Vec3.atCenterOf(pos), 20, p);
            return false;
        }
        return true;
    }

    public static boolean esTrampa(BlockPos caja) {
        return cajasTrampa.remove(caja);
    }

    /** Abrir una puerta con trampa sin ir agachado: descarga. Agachado la desactivas. */
    public static void puertaUsada(ServerLevel lvl, BlockPos pos, ServerPlayer p) {
        BlockPos base = lvl.getBlockState(pos).getBlock() instanceof DoorBlock && lvl.getBlockState(pos).getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        List<BlockPos> luces = puertasTrampa.remove(base);
        if (luces == null) return;
        for (BlockPos l : luces) lvl.removeBlock(l, false);
        if (p.isCrouching()) {
            lvl.playSound(null, base, SoundEvents.TRIPWIRE_DETACH, SoundSource.BLOCKS, 1.0F, 0.8F);
            return;
        }
        p.hurt(com.miscelanea.registry.ModDamageTypes.descarga(lvl, p), 6.0F);
        Rigs.aturdir(p, 50);
        lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.6, 0.4, 0.3);
        lvl.playSound(null, base, ModSounds.DESCARGA.get(), SoundSource.BLOCKS, 1.4F, 1.0F);
        Ruido.alertar(lvl, Vec3.atCenterOf(base), 20, p);
    }

    /** El chivato no se mueve hasta que se encuentra, ni durante el apagón. */
    public static boolean puedeEmpujar(CarroChivato c) {
        if (!activa || c.level().dimension() != DIM) return true;
        if (etapa == Etapa.BUSCAR) {
            etapa = Etapa.EMPUJAR;
            return true;
        }
        return etapa == Etapa.EMPUJAR || etapa == Etapa.LLAVES;
    }

    // =================================================================== muerte, caído, vidas

    /** Golpe mortal dentro de la prueba. Devuelve true si la muerte se cancela (caído o reaparición). */
    public static boolean muerte(ServerPlayer p) {
        if (!dentro(p)) return false;
        if (etapa == Etapa.FIN) {
            p.setHealth(1.0F);
            return true;
        }
        if (Jugadores.caer(p, jugadores((ServerLevel) p.level()).size())) return true;
        morir(p);
        return true;
    }

    public static void vidaExtra(ServerPlayer p) {
        vidas++;
        p.playNotifySound(SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    /** Muerte de verdad: se gasta un intento y reapareces en el último punto de control. Sin intentos, prueba fallida. */
    public static void morir(ServerPlayer p) {
        if (!dentro(p) || etapa == Etapa.FIN) return;
        ServerLevel lvl = (ServerLevel) p.level();
        muertes++;
        vidas--;
        Jugadores.Estado e = Jugadores.de(p);
        e.caido = 0;
        Acciones.parar(p);
        if (vidas <= 0) {
            p.setHealth(p.getMaxHealth());
            terminar(lvl, false);
            return;
        }
        Jugadores.restaurarCordura(p);
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.setPose(Pose.STANDING);
        BlockPos cp = puntoControl();
        p.teleportTo(lvl, cp.getX() + 0.5, cp.getY(), cp.getZ() + 0.5, p.getYRot(), 0);
        for (Mob m : lvl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48), m -> m.getTarget() == p)) m.setTarget(null);
        p.playNotifySound(SoundEvents.WITHER_HURT, SoundSource.MASTER, 0.6F, 0.5F);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new PruebaFinPacket(false, "", 0, vidas));
        enviar(p, false);
    }

    private static BlockPos puntoControl() {
        BlockPos mejor = abs(datos.una("spawn").pos());
        int mejorEtapa = -1;
        for (Mapa.Marca m : datos.de("checkpoint")) {
            int e = m.entero("etapa", 0);
            if (e <= etapa.ordinal() && e > mejorEtapa) {
                mejorEtapa = e;
                mejor = abs(m.pos());
            }
        }
        return mejor;
    }

    private static int segundos(ServerLevel lvl) {
        return (int) ((lvl.getGameTime() - inicio) / 20);
    }

    private static void terminar(ServerLevel lvl, boolean ok) {
        int seg = segundos(lvl);
        String nota = ok ? nota(seg) : "F";
        etapa = Etapa.FIN;
        finTimer = 160;
        for (ServerPlayer p : jugadores(lvl)) {
            p.setInvulnerable(true);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new PruebaFinPacket(ok, nota, seg, muertes));
            p.playNotifySound(ok ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 0.8F, ok ? 0.8F : 0.5F);
        }
        for (Mob m : lvl.getEntitiesOfClass(Mob.class, new AABB(ORIGEN, ORIGEN.offset(datos.sx, datos.sy, datos.sz)))) m.setTarget(null);
    }

    private static String nota(int seg) {
        int pts = 100 - muertes * 15;
        if (seg > 12 * 60) pts -= 10;
        if (seg > 20 * 60) pts -= 10;
        if (seg > 30 * 60) pts -= 10;
        if (pts >= 95) return "A+";
        if (pts >= 85) return "A";
        if (pts >= 70) return "B";
        if (pts >= 55) return "C";
        return "D";
    }

    // =================================================================== HUD

    private static void enviar(ServerPlayer p, boolean intro) {
        int a = 0, b = 0;
        ServerLevel lvl = (ServerLevel) p.level();
        if (etapa == Etapa.GENERADOR) {
            // a: subpaso del generador más cercano sin arrancar (0 combustible, 1 interruptores, 2 manivela); b: encendidos/total
            BlockPos cerca = null;
            double dmin = Double.MAX_VALUE;
            for (BlockPos g : generadores) {
                BlockState s = lvl.getBlockState(g);
                if (!(s.getBlock() instanceof GeneradorBlock) || s.getValue(GeneradorBlock.ENCENDIDO)) continue;
                double d = p.distanceToSqr(Vec3.atCenterOf(g));
                if (d < dmin) {
                    dmin = d;
                    cerca = g;
                }
            }
            if (cerca != null) {
                BlockState g = lvl.getBlockState(cerca);
                if (!g.getValue(GeneradorBlock.LLENO)) a = 0;
                else if (!GeneradorBlock.interruptores(lvl, cerca)) a = 1;
                else a = 2;
            }
            b = generadoresEncendidos(lvl) * 10 + generadores.size();
        } else if (etapa == Etapa.LLAVES || etapa == Etapa.EJECUTAR) {
            for (int g = 0; g < 4; g++) {
                for (Mapa.Marca m : datos.de("reja")) {
                    if (m.entero("g", -1) != g) continue;
                    BlockState s = lvl.getBlockState(abs(m.pos()));
                    if (s.getBlock() instanceof RejaBlock && s.getValue(RejaBlock.ABIERTA)) a++;
                    break;
                }
            }
            b = 4;
        }
        Jugadores.Estado e = Jugadores.de(p);
        boolean perseguido = !lvl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p).isEmpty();
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new PruebaEstadoPacket(true, intro, etapa.ordinal(), a, b, manivela, inicio,
                muertes, vidas, e.cordura, e.psicosis, e.caido, e.rig.ordinal(), e.rigCd, e.rig.recarga, e.rigOff, perseguido));
    }

    public static void alConectar(ServerPlayer p) {
        if (p.level().dimension() != DIM) return;
        if (!activa || !dentro.contains(p.getUUID())) salir(p);
        else enviar(p, false);
    }

    public static void alDesconectar(ServerPlayer p) {
        dentro.remove(p.getUUID());
    }
}
