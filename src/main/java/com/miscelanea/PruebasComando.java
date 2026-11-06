package com.miscelanea;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * /miscelanea pruebas: construye delante del jugador una zona de pruebas con todo el mod. Cada pestaña del creativo es
 * una calle: un cartel con su nombre, todos sus bloques ya colocados (máquinas, atracciones, mesas, el cajero...) y
 * cofres con el resto de sus objetos. Suelo de piedra con luces para que se vea de noche. Solo operadores.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class PruebasComando {
    private PruebasComando() {}

    private static final int FILA_MAX = 96;   // largo máximo de una calle antes de empezar otra

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("miscelanea").then(Commands.literal("pruebas").requires(s -> s.hasPermission(2))
                .executes(c -> construir(c.getSource().getPlayerOrException()))));
    }

    /** Separación que necesita un bloque (las atracciones giran y ocupan mucho). */
    private static int hueco(Block b) {
        String n = b.getClass().getSimpleName();
        if (n.contains("Atraccion")) return 24;
        if (n.contains("Susto") || n.contains("CasetaTiro")) return 10;
        if (n.contains("Tirolina")) return 6;
        return 4;
    }

    private record Pieza(ItemStack item, int x, int z) {}

    private record Calle(Component titulo, List<Pieza> bloques, List<ItemStack> resto, int z0, int fondo) {}

    private static int construir(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        // 1) qué va en cada calle, sacado de las pestañas del creativo del mod (en su orden)
        CreativeModeTab.ItemDisplayParameters par = new CreativeModeTab.ItemDisplayParameters(l.enabledFeatures(), true, l.registryAccess());
        List<Calle> calles = new ArrayList<>();
        int z = 0, ancho = 0;
        for (var ro : com.miscelanea.registry.ModTabs.TABS.getEntries()) {
            CreativeModeTab tab = ro.get();
            try {
                tab.buildContents(par);
            } catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger("Miscelanea/Pruebas").warn("No se pudo leer la pestaña {}", ro.getId(), ex);
                continue;
            }
            List<Pieza> bl = new ArrayList<>();
            List<ItemStack> resto = new ArrayList<>();
            int x = 3, fondo = 4, filaZ = z;
            for (ItemStack s : tab.getDisplayItems()) {
                if (s.getItem() instanceof BlockItem bi && ForgeRegistries.ITEMS.getKey(s.getItem()).getNamespace().equals(Miscelanea.MOD_ID)) {
                    int h = hueco(bi.getBlock());
                    if (x + h > FILA_MAX) {
                        // la calle sigue en una fila nueva
                        filaZ += fondo + 2;
                        x = 3;
                        fondo = 4;
                    }
                    bl.add(new Pieza(s.copyWithCount(1), x + h / 2, filaZ + h / 2));
                    x += h;
                    fondo = Math.max(fondo, h);
                    ancho = Math.max(ancho, x);
                } else resto.add(s.copy());
            }
            if (bl.isEmpty() && resto.isEmpty()) continue;
            int total = filaZ - z + fondo;
            calles.add(new Calle(tab.getDisplayName(), bl, resto, z, total));
            z += total + 3;     // una calle de cofres y paso entre pestañas
        }
        ancho = Math.max(ancho, 3 + 2 * 12) + 2;
        int largo = z;
        // 2) dónde: delante del jugador, sobre el suelo donde está
        Direction fr = p.getDirection();
        BlockPos o = p.blockPosition().relative(fr, 3);
        int alto = 30;
        // ejes locales: 'x' a la derecha del jugador, 'z' hacia delante
        Direction der = fr.getClockWise();
        // 3) limpiar y poner el suelo (piedra lisa, con una luz cada 6 bloques)
        for (int i = -1; i <= ancho; i++)
            for (int k = -1; k <= largo; k++) {
                BlockPos c = o.relative(der, i).relative(fr, k);
                l.setBlock(c.below(), (i % 6 == 0 && k % 6 == 0) ? Blocks.SEA_LANTERN.defaultBlockState()
                        : (i == -1 || k == -1 || i == ancho || k == largo) ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                        : Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < alto; y++) {
                    BlockPos a = c.above(y);
                    if (!l.getBlockState(a).isAir()) l.setBlock(a, Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        // 4) colocar todo como si lo pusiera el jugador mirando hacia la calle (así cada bloque hace lo suyo al ponerse)
        float yaw0 = p.getYRot(), pitch0 = p.getXRot();
        ItemStack mano = p.getItemInHand(InteractionHand.MAIN_HAND).copy();
        int puestos = 0, fallos = 0;
        try {
            p.setYRot(fr.getOpposite().toYRot());
            p.setXRot(0);
            for (Calle c : calles) {
                // cartel con el nombre de la pestaña al principio de la calle
                BlockPos cp = o.relative(fr, c.z0 + 1).relative(der, 0);
                l.setBlock(cp, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION,
                        net.minecraft.world.level.block.state.properties.RotationSegment.convertToSegment(fr.getOpposite().toYRot())), 3);
                if (l.getBlockEntity(cp) instanceof SignBlockEntity se) {
                    SignText t = se.getFrontText().setMessage(1, c.titulo).setHasGlowingText(true);
                    se.setText(t, true);
                    se.setChanged();
                }
                for (Pieza pz : c.bloques) {
                    BlockPos suelo = o.relative(der, pz.x).relative(fr, pz.z).below();
                    p.setItemInHand(InteractionHand.MAIN_HAND, pz.item.copy());
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(suelo).add(0, 0.5, 0), Direction.UP, suelo, false);
                    BlockItem bi = (BlockItem) pz.item.getItem();
                    if (bi.place(new BlockPlaceContext(p, InteractionHand.MAIN_HAND, p.getItemInHand(InteractionHand.MAIN_HAND), hit)).consumesAction())
                        puestos++;
                    else fallos++;
                }
                // cofres con el resto de objetos, en fila al final de la calle
                int cx = 3;
                List<ItemStack> r = c.resto;
                for (int i = 0; i < r.size(); i += 27) {
                    BlockPos ch = o.relative(der, cx).relative(fr, c.z0 + c.fondo + 1);
                    l.setBlock(ch, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, fr.getOpposite()), 3);
                    if (l.getBlockEntity(ch) instanceof Container cont)
                        for (int j = 0; j < 27 && i + j < r.size(); j++) cont.setItem(j, r.get(i + j));
                    cx += 2;
                }
            }
            // un cofre más con dinero para probar el banco y el cajero
            BlockPos ch = o.relative(der, 0).relative(fr, Math.max(0, largo - 2));
            l.setBlock(ch, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, fr.getOpposite()), 3);
            if (l.getBlockEntity(ch) instanceof Container cont) {
                int j = 0;
                for (var b : com.miscelanea.movil.Dinero.monedas()) cont.setItem(j++, new ItemStack(b.item(), 64));
                cont.setItem(j, new ItemStack(Items.EMERALD, 64));
            }
        } finally {
            p.setItemInHand(InteractionHand.MAIN_HAND, mano);
            p.setYRot(yaw0);
            p.setXRot(pitch0);
        }
        final int pp = puestos, ff = fallos, n = calles.size();
        p.sendSystemMessage(Component.translatable("miscelanea.pruebas.hecho", pp, n, ff));
        return pp;
    }
}
