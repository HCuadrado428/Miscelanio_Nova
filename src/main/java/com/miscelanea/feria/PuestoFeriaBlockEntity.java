package com.miscelanea.feria;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Pesca de patitos: con una ficha tienes 3 intentos; pescas con la caña el pato al que apuntes y debajo viene el premio.
 * Tómbola: con una ficha gira el bombo y sale un número del 1 al 100 (cuanto más alto, mejor premio).
 * Churrería: con una ficha, cuatro churros; agachado, chocolate con churros.
 */
public class PuestoFeriaBlockEntity extends BlockEntity {
    public static final int PATOS = 10;
    public static final double RADIO = 1.25, AGUA = 0.62;
    @Nullable public UUID jugador;
    public boolean activo;
    public int intentos, numero = -1;
    public long inicio;
    public long[] pescado = new long[PATOS];
    public int[] premioPato = new int[PATOS];

    public PuestoFeriaBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.PUESTO_FERIA_BE.get(), p, s);
        java.util.Arrays.fill(pescado, -10000);
    }

    public int tipo() {
        return getBlockState().getBlock() instanceof PuestoFeriaBlock b ? b.tipo : 0;
    }

    public Direction facing() {
        return getBlockState().getValue(PuestoFeriaBlock.FACING);
    }

    /** Posición del pato i en un instante (relativa al centro del bloque): {x, y, z, ángulo}. */
    public static double[] pato(int i, double t) {
        double a = t * 0.018 + i * Math.PI * 2 / PATOS;
        return new double[]{Math.cos(a) * RADIO, AGUA + Math.sin(t * 0.15 + i * 1.7) * 0.02, Math.sin(a) * RADIO, a};
    }

    boolean ficha(Player p, ItemStack st) {
        if (!st.is(ModItems.FICHA.get())) {
            p.displayClientMessage(Component.translatable("miscelanea.maquina.ficha"), true);
            return false;
        }
        if (!p.getAbilities().instabuild) st.shrink(1);
        level.playSound(null, worldPosition, ModSounds.ARCADE_FICHA.get(), SoundSource.BLOCKS, 0.8F, 1F);
        return true;
    }

    void usar(Player p, ItemStack st) {
        long ahora = level.getGameTime();
        switch (tipo()) {
            case PuestoFeriaBlock.PATITOS -> {
                if (activo && p.getUUID().equals(jugador)) {
                    p.displayClientMessage(Component.translatable("miscelanea.patitos.quedan", intentos), true);
                    return;
                }
                if (activo && ahora - inicio < 1200) {
                    p.displayClientMessage(Component.translatable("miscelanea.patitos.ocupado"), true);
                    return;
                }
                if (!ficha(p, st)) return;
                jugador = p.getUUID();
                activo = true;
                intentos = 3;
                inicio = ahora;
                if (!p.getInventory().hasAnyMatching(x -> x.is(ModItems.CANA_PATITOS.get()))) p.getInventory().add(new ItemStack(ModItems.CANA_PATITOS.get()));
                p.displayClientMessage(Component.translatable("miscelanea.patitos.empieza"), true);
            }
            case PuestoFeriaBlock.TOMBOLA -> {
                if (activo) return;
                if (!ficha(p, st)) return;
                jugador = p.getUUID();
                activo = true;
                inicio = ahora;
                numero = 1 + level.random.nextInt(100);
                level.playSound(null, worldPosition, ModSounds.FE_BOMBO.get(), SoundSource.BLOCKS, 1F, 1F);
            }
            default -> {
                if (!ficha(p, st)) return;
                ItemStack r = p.isSecondaryUseActive() ? new ItemStack(ModItems.CHURROS_CHOCOLATE.get()) : new ItemStack(ModItems.CHURROS.get(), 4);
                soltar(r);
                level.playSound(null, worldPosition, ModSounds.CO_CHISPORROTEO.get(), SoundSource.BLOCKS, 0.8F, 1.1F);
                p.displayClientMessage(Component.translatable("miscelanea.churreria.gracias"), true);
            }
        }
        cambio();
    }

    /** Lanzamiento de caña: pesca el pato más cercano a donde mira el jugador. */
    void pescar(Player p, Vec3 ojo, Vec3 dir) {
        if (!activo || !p.getUUID().equals(jugador) || intentos <= 0) return;
        long ahora = level.getGameTime();
        double yAgua = worldPosition.getY() + AGUA;
        if (dir.y > -0.05) return;
        double k = (yAgua - ojo.y) / dir.y;
        if (k <= 0 || k > 8) return;
        Vec3 donde = ojo.add(dir.scale(k));
        int mejor = -1;
        double cerca = 0.42;
        for (int i = 0; i < PATOS; i++) {
            if (ahora - pescado[i] < 120) continue;
            double[] q = pato(i, ahora);
            double d = Math.hypot(worldPosition.getX() + 0.5 + q[0] - donde.x, worldPosition.getZ() + 0.5 + q[2] - donde.z);
            if (d < cerca) {
                cerca = d;
                mejor = i;
            }
        }
        // si la caña cae lejos del estanque no cuenta como intento
        if (Math.hypot(donde.x - worldPosition.getX() - 0.5, donde.z - worldPosition.getZ() - 0.5) > RADIO + 0.9) return;
        // fallar también gasta intento (antes no, y con la ficha de premio salía a cuenta jugar sin parar)
        intentos--;
        if (mejor < 0) {
            level.playSound(null, donde.x, donde.y, donde.z, net.minecraft.sounds.SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.BLOCKS, 0.5F, 1.4F);
            if (level instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.SPLASH, donde.x, donde.y, donde.z, 8, 0.1, 0, 0.1, 0.1);
            p.displayClientMessage(Component.translatable("miscelanea.patitos.quedan", intentos), true);
            if (intentos <= 0) activo = false;
            cambio();
            return;
        }
        pescado[mejor] = ahora;
        float r = level.random.nextFloat();
        // como mucho 3 patos x 25 % de ficha = 0,75 fichas devueltas por ficha gastada: nunca sale a cuenta
        int premio = r < 0.01F ? 4 : r < 0.08F ? 3 : r < 0.25F ? 2 : r < 0.75F ? 1 : 0;
        premioPato[mejor] = premio;
        ItemStack st = switch (premio) {
            case 4 -> ChapaItem.de(ChapaItem.ORO);
            case 3 -> peluche();
            case 2 -> ChapaItem.de(level.random.nextInt(ChapaItem.ORO));
            case 1 -> algodon();
            default -> new ItemStack(ModItems.FICHA.get());
        };
        soltar(st);
        level.playSound(null, worldPosition, ModSounds.FE_CUAC.get(), SoundSource.BLOCKS, 1F, 0.9F + level.random.nextFloat() * 0.3F);
        if (premio >= 3) level.playSound(null, worldPosition, ModSounds.FE_PREMIO.get(), SoundSource.BLOCKS, 1F, 1F);
        if (level instanceof ServerLevel sl) {
            double[] q = pato(mejor, ahora);
            Vec3 c = new Vec3(worldPosition.getX() + 0.5 + q[0], worldPosition.getY() + AGUA + 0.3, worldPosition.getZ() + 0.5 + q[2]);
            sl.sendParticles(ParticleTypes.SPLASH, c.x, c.y - 0.2, c.z, 14, 0.15, 0, 0.15, 0.1);
            if (premio >= 2) FeriaJuice.confeti(sl, c.add(0, 0.6, 0), premio >= 3 ? 2 : 1);
        }
        p.displayClientMessage(Component.translatable("miscelanea.patitos.premio." + premio).append(" · ")
                .append(Component.translatable("miscelanea.patitos.quedan", intentos)), true);
        if (intentos <= 0) activo = false;
        cambio();
    }

    private ItemStack peluche() {
        ItemStack st = new ItemStack(ModItems.PELUCHE.get());
        CompoundTag est = new CompoundTag();
        est.putString("color", String.valueOf(level.random.nextInt(4)));
        st.getOrCreateTag().put("BlockStateTag", est);
        return st;
    }

    private ItemStack algodon() {
        ItemStack st = new ItemStack(ModItems.ALGODON.get());
        st.getOrCreateTag().putInt("color", level.random.nextInt(5));
        return st;
    }

    private void soltar(ItemStack st) {
        Direction f = facing();
        ItemEntity it = new ItemEntity(level, worldPosition.getX() + 0.5 + f.getStepX() * 0.8, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5 + f.getStepZ() * 0.8, st);
        it.setDeltaMovement(f.getStepX() * 0.1, 0.15, f.getStepZ() * 0.1);
        level.addFreshEntity(it);
    }

    public static void tick(Level l, BlockPos pos, BlockState s, PuestoFeriaBlockEntity be) {
        if (!be.activo) return;
        long t = l.getGameTime() - be.inicio;
        if (be.tipo() == PuestoFeriaBlock.TOMBOLA && t == 60) {
            int n = be.numero;
            int tier = n == 100 ? 4 : n > 90 ? 3 : n > 70 ? 2 : n > 40 ? 1 : 0;
            ItemStack st = switch (tier) {
                case 4 -> ChapaItem.de(ChapaItem.ORO);
                case 3 -> be.peluche();
                case 2 -> ChapaItem.de(l.random.nextInt(ChapaItem.ORO));
                case 1 -> l.random.nextBoolean() ? be.algodon() : new ItemStack(ModItems.PALOMITAS.get());
                default -> ItemStack.EMPTY;
            };
            if (!st.isEmpty()) be.soltar(st);
            if (tier >= 2 && l instanceof ServerLevel sl) FeriaJuice.confeti(sl, Vec3.atCenterOf(pos).add(0, 1.0, 0), tier >= 3 ? 2 : 1);
            l.playSound(null, pos, tier > 0 ? ModSounds.FE_PREMIO.get() : ModSounds.FE_PARA.get(), SoundSource.BLOCKS, 1F, tier > 0 ? 1F : 1.5F);
            for (Player o : l.getEntitiesOfClass(Player.class, new AABB(pos).inflate(16)))
                o.displayClientMessage(Component.translatable("miscelanea.tombola.canta", n, Component.translatable("miscelanea.tombola.premio." + tier))
                        .withStyle(tier >= 3 ? ChatFormatting.GOLD : ChatFormatting.YELLOW), false);
        }
        if (be.tipo() == PuestoFeriaBlock.TOMBOLA && t > 140) {
            be.activo = false;
            be.cambio();
        }
        if (be.tipo() == PuestoFeriaBlock.PATITOS && t > 1200) {
            be.activo = false;
            be.cambio();
        }
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (jugador != null) t.putUUID("jug", jugador);
        t.putBoolean("act", activo);
        t.putInt("int", intentos);
        t.putInt("num", numero);
        t.putLong("ini", inicio);
        t.putLongArray("pesc", pescado);
        t.putIntArray("prem", premioPato);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        jugador = t.hasUUID("jug") ? t.getUUID("jug") : null;
        activo = t.getBoolean("act");
        intentos = t.getInt("int");
        numero = t.contains("num") ? t.getInt("num") : -1;
        inicio = t.getLong("ini");
        long[] p = t.getLongArray("pesc");
        if (p.length == PATOS) pescado = p;
        int[] q = t.getIntArray("prem");
        if (q.length == PATOS) premioPato = q;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2, 2, 2);
    }

    static PuestoFeriaBlockEntity buscar(Level l, Player p) {
        BlockPos c = p.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-7, -4, -7), c.offset(7, 3, 7)))
            if (l.getBlockEntity(q) instanceof PuestoFeriaBlockEntity be && be.tipo() == PuestoFeriaBlock.PATITOS && p.getUUID().equals(be.jugador) && be.activo) return be;
        return null;
    }
}
