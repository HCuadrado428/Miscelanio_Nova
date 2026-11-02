package com.miscelanea.tesoros;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Tesoros enterrados: como mucho uno por chunk (40 %), decidido de forma fija con la semilla. Su posición se fija la primera vez
 * que un detector pasa cerca (a 1-3 bloques bajo la superficie, en tierra/arena/grava/piedra) y se guarda con el mundo.
 * Al romper ese bloque sale el botín: común, raro, fósil o cápsula del tiempo.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public class Tesoros extends SavedData {
    private static final String NOMBRE = "miscelanea_tesoros";
    public static final String[] TIPOS = {"comun", "raro", "fosil", "capsula"};
    /** chunk → {x,y,z,tipo,cavado} o {-1} si no hay tesoro. */
    private final CompoundTag datos = new CompoundTag();

    static Tesoros de(ServerLevel l) {
        return l.getDataStorage().computeIfAbsent(t -> {
            Tesoros r = new Tesoros();
            r.datos.merge(t.getCompound("chunks"));
            return r;
        }, Tesoros::new, NOMBRE);
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.put("chunks", datos);
        return t;
    }

    private static long mezcla(long a) {
        a ^= a >>> 33;
        a *= 0xff51afd7ed558ccdL;
        a ^= a >>> 33;
        a *= 0xc4ceb9fe1a85ec53L;
        a ^= a >>> 33;
        return a;
    }

    private static boolean suelo(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.CLAY)
                || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.MUD);
    }

    /** Devuelve el tesoro del chunk (sin cavar) o null. Lo crea si es la primera vez que se mira. */
    CompoundTag tesoro(ServerLevel l, int cx, int cz) {
        String k = cx + "," + cz;
        if (datos.contains(k)) {
            CompoundTag t = datos.getCompound(k);
            return t.contains("x") && !t.getBoolean("cavado") ? t : null;
        }
        if (!l.hasChunk(cx, cz)) return null;
        long h = mezcla(l.getSeed() ^ (cx * 341873128712L + cz * 132897987541L) ^ l.dimension().location().hashCode());
        CompoundTag t = new CompoundTag();
        if ((h & 1023) < 410) {
            int x = (cx << 4) + (int) ((h >>> 10) & 15), z = (cz << 4) + (int) ((h >>> 14) & 15);
            int prof = 1 + (int) ((h >>> 18) % 3);
            // Sin hojas (árboles encima) y sin capas de nieve: si no, la "superficie" no era suelo y no salía nunca tesoro
            int sup = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            while (sup > l.getMinBuildHeight() && l.getBlockState(new BlockPos(x, sup, z)).is(Blocks.SNOW)) sup--;
            BlockPos p = new BlockPos(x, sup - prof, z);
            if (suelo(l.getBlockState(p)) && suelo(l.getBlockState(new BlockPos(x, sup, z)))) {
                int r = (int) ((h >>> 24) % 100);
                t.putInt("x", x);
                t.putInt("y", p.getY());
                t.putInt("z", z);
                t.putString("tipo", r < 55 ? "comun" : r < 72 ? "raro" : r < 92 ? "fosil" : "capsula");
            }
        }
        datos.put(k, t);
        setDirty();
        return t.contains("x") ? t : null;
    }

    /** Solo lectura: el tesoro (sin cavar) de un chunk ya mirado por un detector, o null. No crea ni guarda nada. */
    CompoundTag existente(int cx, int cz) {
        String k = cx + "," + cz;
        if (!datos.contains(k)) return null;
        CompoundTag t = datos.getCompound(k);
        return t.contains("x") && !t.getBoolean("cavado") ? t : null;
    }

    private static boolean llevaDetector(ServerPlayer p) {
        return p.getMainHandItem().getItem() instanceof DetectorItem || p.getOffhandItem().getItem() instanceof DetectorItem;
    }

    /** Tesoro más cercano en un radio de ~1 chunk: {distancia, dx, dz} o null. */
    double[] cercano(ServerLevel l, Vec3 p) {
        int cx = ((int) Math.floor(p.x)) >> 4, cz = ((int) Math.floor(p.z)) >> 4;
        double mejor = 1e9;
        double[] r = null;
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++) {
                CompoundTag t = tesoro(l, cx + i, cz + j);
                if (t == null) continue;
                double dx = t.getInt("x") + 0.5 - p.x, dy = t.getInt("y") + 0.5 - p.y, dz = t.getInt("z") + 0.5 - p.z;
                double d = Math.sqrt(dx * dx + dy * dy * 0.3 + dz * dz);
                if (d < mejor) {
                    mejor = d;
                    r = new double[]{d, dx, dz};
                }
            }
        return r;
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 8 != 0) return;
        if (!llevaDetector(p)) return;
        // el plato va un poco por delante de los pies
        Vec3 v = p.getLookAngle();
        Vec3 plato = p.position().add(v.x * 1.2, 0, v.z * 1.2);
        double[] c = de(p.serverLevel()).cercano(p.serverLevel(), plato);
        float fuerza = 0, ang = 0;
        double dist = 99;
        if (c != null && c[0] < 18) {
            dist = c[0];
            fuerza = (float) Math.max(0, 1 - c[0] / 18);
            ang = (float) (Math.toDegrees(Math.atan2(-c[1], c[2])) - p.getYRot());
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new DetectorPacket(fuerza, (float) dist, ang));
    }

    @SubscribeEvent
    public static void romper(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel l) || !(e.getPlayer() instanceof ServerPlayer p)) return;
        BlockPos pos = e.getPos();
        Tesoros ts = de(l);
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        // Antes se llamaba a tesoro() en CADA bloque roto, creando y guardando una entrada por chunk minado.
        // Ahora solo se lee lo que ya existe; solo se genera si el jugador lleva el detector (como al pasar con él).
        CompoundTag t = ts.existente(cx, cz);
        if (t == null && llevaDetector(p)) t = ts.tesoro(l, cx, cz);
        if (t == null || t.getInt("x") != pos.getX() || t.getInt("y") != pos.getY() || t.getInt("z") != pos.getZ()) return;
        t.putBoolean("cavado", true);
        ts.setDirty();
        String tipo = t.getString("tipo");
        LootTable tabla = l.getServer().getLootData().getLootTable(new ResourceLocation(Miscelanea.MOD_ID, "tesoros/" + tipo));
        LootParams lp = new LootParams.Builder(l).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withParameter(LootContextParams.THIS_ENTITY, p)
                .withLuck(p.getLuck()).create(LootContextParamSets.CHEST);
        for (ItemStack s : tabla.getRandomItems(lp)) {
            ItemEntity it = new ItemEntity(l, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, s);
            it.setDeltaMovement(l.random.nextGaussian() * 0.05, 0.25, l.random.nextGaussian() * 0.05);
            l.addFreshEntity(it);
        }
        l.sendParticles(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.1);
        l.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, 0.4, 0.3, 0.4, 0);
        l.playSound(null, pos, ModSounds.TESORO.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.miscelanea.tesoro." + tipo).withStyle(net.minecraft.ChatFormatting.GOLD), true);
    }
}
