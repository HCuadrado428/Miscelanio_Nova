package com.miscelanea.client.manual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miscelanea.Miscelanea;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Contenido del manual, leído de los recursos: todos los {@code assets/<espacio>/manual/*.json} (de este mod, de otros o
 * de un paquete de recursos). Cada archivo es un capítulo o un trozo de capítulo; los que comparten {@code "id"} se
 * juntan (las entradas se suman en el orden de los archivos), así que una rama nueva puede añadir sus cosas con un
 * archivo propio sin tocar los demás:
 * <pre>
 * {
 *   "id": "feria",                        capítulo (si ya existe, se le suman las entradas)
 *   "orden": 10,                          posición en el índice (menor = antes)
 *   "tipo": "objetos",                    "objetos" (por defecto), "controles" o "comandos"
 *   "pestana": "miscelanea:feria",        pestaña del creativo: lo que haya en ella y no salga en ninguna entrada se
 *                                         añade solo al final, con la descripción de su tooltip
 *   "icono": "miscelanea:tombola",
 *   "titulo": {"es": "...", "en": "..."}, textos: una cadena o un objeto por idioma ("es", "en", "es_es"...)
 *   "intro": {"es": "...", "en": "..."},
 *   "entradas": [
 *     {
 *       "id": "noria",
 *       "objetos": ["miscelanea:noria"],  el primero es el icono; admite NBT ("id{...}") y "pocion:", "arrojadiza:",
 *                                         "persistente:" o "flecha:" + id de poción
 *       "titulo": ...,                    opcional (por defecto, el nombre del primer objeto)
 *       "texto": ...,                     párrafos separados por \n; "# " título, "- " viñeta;
 *                                         {key:key.miscelanea.pipboy} = la tecla que tenga puesta el jugador
 *       "creativo": true,                 sello de "solo en creativo"
 *       "receta": "auto",                 "auto" (por defecto: se busca en el RecipeManager), "no" o el id de una receta
 *       "imagen": {"textura": "miscelanea:textures/gui/manual/x.png", "ancho": 100, "alto": 50}   (en px de GUI; la
 *                                         textura a 2 texels por pixel)
 *       "tecla": "key.miscelanea.pipboy", (capítulo de controles) la tecla que explica la entrada
 *       "comando": "espejismo",           (capítulo de comandos) el subcomando de /miscelanea
 *       "uso": ...                        (comandos) cómo se escribe, si el jugador no tiene permiso para verlo
 *     }
 *   ]
 * }
 * </pre>
 */
public final class ManualLibro {
    public final List<Capitulo> capitulos = new ArrayList<>();
    /** Clave de objeto ({@link #clave}) → entrada que lo explica (para saltar desde una receta). */
    public final Map<String, Entrada> porObjeto = new HashMap<>();

    /** Texto en varios idiomas. */
    public static final class Texto {
        final Map<String, String> t = new LinkedHashMap<>();

        static Texto de(JsonElement e) {
            if (e == null || e.isJsonNull()) return null;
            Texto x = new Texto();
            if (e.isJsonPrimitive()) x.t.put("*", e.getAsString());
            else if (e.isJsonObject()) for (var en : e.getAsJsonObject().entrySet()) x.t.put(en.getKey().toLowerCase(Locale.ROOT), unir(en.getValue()));
            else if (e.isJsonArray()) x.t.put("*", unir(e));
            return x;
        }

        /** Un párrafo largo se puede escribir como lista de líneas. */
        private static String unir(JsonElement e) {
            if (!e.isJsonArray()) return e.getAsString();
            StringBuilder sb = new StringBuilder();
            for (JsonElement l : e.getAsJsonArray()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(l.getAsString());
            }
            return sb.toString();
        }

        public String get() {
            String idioma = idioma();
            String v = t.get(idioma);
            if (v == null && idioma.length() >= 2) v = t.get(idioma.substring(0, 2));
            if (v == null) v = t.get("*");
            if (v == null) v = t.get("en");
            if (v == null) v = t.get("en_us");
            if (v == null) v = t.get("es");
            if (v == null) v = t.get("es_es");
            if (v == null && !t.isEmpty()) v = t.values().iterator().next();
            return v == null ? "" : v;
        }
    }

    public static final class Capitulo {
        public String id, tipo = "objetos", pestana, icono;
        public int orden = 1000;
        public Texto titulo, intro;
        public final List<Entrada> entradas = new ArrayList<>();
        public ItemStack iconoStack = ItemStack.EMPTY;

        public String titulo() {
            if (titulo != null) return titulo.get();
            if (pestana != null) {
                CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.tryParse(pestana));
                if (tab != null) return tab.getDisplayName().getString();
            }
            return id;
        }
    }

    public static final class Entrada {
        public String id, capitulo, tecla, comando, receta = "auto";
        public Texto titulo, texto, uso;
        public Boolean creativo;
        public final List<ItemStack> objetos = new ArrayList<>();
        public ResourceLocation imagen;
        public int imagenAncho, imagenAlto;
        /** Entrada añadida sola (objeto de la pestaña que no salía en ningún JSON): el texto sale de su tooltip. */
        public boolean auto;
        public String textoAuto = "";

        public ItemStack icono() {
            return objetos.isEmpty() ? ItemStack.EMPTY : objetos.get(0);
        }

        public String titulo() {
            if (titulo != null) return titulo.get();
            if (!objetos.isEmpty()) return objetos.get(0).getHoverName().getString();
            if (tecla != null) return net.minecraft.network.chat.Component.translatable(tecla).getString();
            if (comando != null) return "/" + Miscelanea.MOD_ID + " " + comando;
            return id;
        }

        public String texto() {
            return texto != null ? texto.get() : textoAuto;
        }

        public String ancla() {
            return "e:" + capitulo + "/" + id;
        }
    }

    // ------------------------------------------------------------------ carga

    public static ManualLibro cargar() {
        ManualLibro l = new ManualLibro();
        Map<String, Capitulo> caps = new LinkedHashMap<>();
        Minecraft mc = Minecraft.getInstance();
        Map<ResourceLocation, Resource> res = mc.getResourceManager().listResources("manual", rl -> rl.getPath().endsWith(".json"));
        List<ResourceLocation> orden = new ArrayList<>(res.keySet());
        // primero los del mod y, dentro de cada espacio, por nombre de archivo (así el orden de las entradas es estable)
        orden.sort(Comparator.comparing((ResourceLocation rl) -> !rl.getNamespace().equals(Miscelanea.MOD_ID))
                .thenComparing(ResourceLocation::getNamespace).thenComparing(ResourceLocation::getPath));
        for (ResourceLocation rl : orden) {
            try (Reader r = res.get(rl).openAsReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                leerCapitulo(caps, o);
            } catch (Exception e) {
                Miscelanea.LOGGER.warn("[miscelanea] No se ha podido leer la página del manual {}: {}", rl, e.toString());
            }
        }
        l.capitulos.addAll(caps.values());
        l.capitulos.sort(Comparator.comparingInt(c -> c.orden));
        // lo que cubren las entradas
        for (Capitulo c : l.capitulos)
            for (Entrada e : c.entradas)
                for (ItemStack st : e.objetos) l.porObjeto.putIfAbsent(clave(st), e);
        l.rellenarConPestanas();
        return l;
    }

    private static void leerCapitulo(Map<String, Capitulo> caps, JsonObject o) {
        String id = o.has("id") ? o.get("id").getAsString() : "varios";
        Capitulo c = caps.computeIfAbsent(id, k -> {
            Capitulo n = new Capitulo();
            n.id = k;
            return n;
        });
        if (o.has("orden")) c.orden = o.get("orden").getAsInt();
        if (o.has("tipo")) c.tipo = o.get("tipo").getAsString();
        if (o.has("pestana")) c.pestana = o.get("pestana").getAsString();
        if (o.has("icono")) {
            c.icono = o.get("icono").getAsString();
            c.iconoStack = objeto(c.icono);
        }
        if (o.has("titulo") && c.titulo == null) c.titulo = Texto.de(o.get("titulo"));
        if (o.has("intro") && c.intro == null) c.intro = Texto.de(o.get("intro"));
        if (!o.has("entradas")) return;
        JsonArray arr = o.getAsJsonArray("entradas");
        Set<String> ids = new HashSet<>();
        for (Entrada e : c.entradas) ids.add(e.id);
        for (JsonElement el : arr) {
            JsonObject j = el.getAsJsonObject();
            Entrada e = new Entrada();
            e.capitulo = c.id;
            if (j.has("objetos")) {
                JsonElement ob = j.get("objetos");
                if (ob.isJsonArray()) for (JsonElement x : ob.getAsJsonArray()) anadir(e, x.getAsString());
                else anadir(e, ob.getAsString());
                // los objetos de otra rama que aún no existen: si no queda ninguno, la entrada no se enseña
                if (e.objetos.isEmpty()) continue;
            }
            e.id = j.has("id") ? j.get("id").getAsString()
                    : j.has("tecla") ? j.get("tecla").getAsString()
                    : j.has("comando") ? j.get("comando").getAsString()
                    : !e.objetos.isEmpty() ? clave(e.objetos.get(0)) : "entrada" + c.entradas.size();
            if (!ids.add(e.id)) e.id = e.id + "_" + c.entradas.size();
            e.titulo = Texto.de(j.get("titulo"));
            e.texto = Texto.de(j.get("texto"));
            e.uso = Texto.de(j.get("uso"));
            if (j.has("creativo")) e.creativo = j.get("creativo").getAsBoolean();
            if (j.has("receta")) e.receta = j.get("receta").isJsonPrimitive() && j.get("receta").getAsJsonPrimitive().isBoolean()
                    ? (j.get("receta").getAsBoolean() ? "auto" : "no") : j.get("receta").getAsString();
            if (j.has("tecla")) e.tecla = j.get("tecla").getAsString();
            if (j.has("comando")) e.comando = j.get("comando").getAsString();
            if (j.has("imagen")) {
                JsonObject im = j.getAsJsonObject("imagen");
                e.imagen = ResourceLocation.tryParse(im.get("textura").getAsString());
                e.imagenAncho = im.has("ancho") ? im.get("ancho").getAsInt() : 100;
                e.imagenAlto = im.has("alto") ? im.get("alto").getAsInt() : 50;
            }
            c.entradas.add(e);
        }
    }

    private static void anadir(Entrada e, String s) {
        ItemStack st = objeto(s);
        if (!st.isEmpty()) e.objetos.add(st);
    }

    /** "miscelanea:noria", "minecraft:potion{Potion:\"miscelanea:cupido\"}", "pocion:miscelanea:cupido"... */
    public static ItemStack objeto(String s) {
        if (s == null) return ItemStack.EMPTY;
        s = s.trim();
        Item pocion = null;
        if (s.startsWith("pocion:")) pocion = Items.POTION;
        else if (s.startsWith("arrojadiza:")) pocion = Items.SPLASH_POTION;
        else if (s.startsWith("persistente:")) pocion = Items.LINGERING_POTION;
        else if (s.startsWith("flecha:")) pocion = Items.TIPPED_ARROW;
        try {
            if (pocion != null) {
                ResourceLocation rl = ResourceLocation.tryParse(s.substring(s.indexOf(':') + 1));
                Potion p = rl == null ? null : ForgeRegistries.POTIONS.getValue(rl);
                if (p == null || p == Potions.EMPTY || !ForgeRegistries.POTIONS.containsKey(rl)) return ItemStack.EMPTY;
                return PotionUtils.setPotion(new ItemStack(pocion), p);
            }
            int i = s.indexOf('{');
            ResourceLocation rl = ResourceLocation.tryParse(i < 0 ? s : s.substring(0, i));
            if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) return ItemStack.EMPTY;
            Item it = ForgeRegistries.ITEMS.getValue(rl);
            if (it == null || it == Items.AIR) return ItemStack.EMPTY;
            ItemStack st = new ItemStack(it);
            if (i >= 0) st.setTag(TagParser.parseTag(s.substring(i)));
            return st;
        } catch (Exception ex) {
            return ItemStack.EMPTY;
        }
    }

    /** Lo que identifica a un objeto en el manual: su id, o "pocion:nombre" para las pociones (todas sus formas). */
    public static String clave(ItemStack st) {
        Potion p = PotionUtils.getPotion(st);
        if (p != Potions.EMPTY && (st.is(Items.POTION) || st.is(Items.SPLASH_POTION) || st.is(Items.LINGERING_POTION) || st.is(Items.TIPPED_ARROW)))
            return "pocion:" + p.getName("");
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(st.getItem());
        return rl == null ? "?" : rl.toString();
    }

    private static boolean delMod(ItemStack st) {
        Potion p = PotionUtils.getPotion(st);
        if (p != Potions.EMPTY) {
            ResourceLocation rl = ForgeRegistries.POTIONS.getKey(p);
            return rl != null && rl.getNamespace().equals(Miscelanea.MOD_ID);
        }
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(st.getItem());
        return rl != null && rl.getNamespace().equals(Miscelanea.MOD_ID);
    }

    /** Lo que hay en la pestaña del creativo de cada capítulo y no sale en ninguna entrada: entrada automática. */
    private void rellenarConPestanas() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), false, mc.level.registryAccess());
        } catch (Exception e) {
            return;
        }
        for (Capitulo c : capitulos) {
            if (c.pestana == null) continue;
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.tryParse(c.pestana));
            if (tab == null) continue;
            if (c.iconoStack.isEmpty()) c.iconoStack = tab.getIconItem();
            for (ItemStack st : tab.getDisplayItems()) {
                if (!delMod(st)) continue;
                String k = clave(st);
                if (porObjeto.containsKey(k)) continue;
                Entrada e = new Entrada();
                e.capitulo = c.id;
                e.id = "auto_" + k.replace(':', '_').replace('|', '_');
                e.auto = true;
                e.objetos.add(st.copy());
                StringBuilder sb = new StringBuilder();
                try {
                    List<net.minecraft.network.chat.Component> tt = st.getTooltipLines(mc.player, TooltipFlag.Default.NORMAL);
                    for (int i = 1; i < tt.size(); i++) {
                        String s = tt.get(i).getString();
                        if (s.isBlank()) continue;
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(s);
                    }
                } catch (Exception ignored) {
                }
                e.textoAuto = sb.toString();
                c.entradas.add(e);
                porObjeto.put(k, e);
            }
        }
    }

    // ------------------------------------------------------------------ utilidades

    public static String idioma() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "es_es";
        }
    }

    public static boolean espanol() {
        return idioma().startsWith("es");
    }

    /** Minúsculas y sin tildes, para buscar. */
    public static String normal(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").replaceAll("§.", "");
    }
}
