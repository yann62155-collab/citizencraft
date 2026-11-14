package fr.citizen.citizencraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Construit, cote client, l'index de toutes les recettes connues (vanilla + tous les mods).
 * En 1.21.1 le serveur synchronise toutes les recettes vers le client : aucun paquet supplementaire n'est requis.
 *
 * >>> Pour ranger un objet autrement, modifie les listes ci-dessous. <<<
 */
public final class RecipeIndex {

    // ---- Identifiants des mods Citizen ----
    public static final String NS_ARTEFACTS = "citizenartefacts";
    public static final String NS_CHASSE = "citizenchasse";
    public static final String NS_FONDERIE = "citizenfonderie";
    public static final String NS_HIBOU = "hiboupostal";
    public static final String NS_ECONOMY = "citizeneconomy";
    public static final String NS_AFFICHE = "citizenaffiches";
    public static final String NS_ECLIPSE = "citizeneclipse";
    public static final String NS_MENUISERIE = "citizenmenuiserie";

    /** Mods dont tous les objets vont dans "Pêche". */
    private static final Set<String> FISHING_MODS = Set.of("tide", "fishingreal", "fish_of_thieves");
    /** Mods dont tous les objets vont dans "Boucherie". */
    private static final Set<String> BUTCHER_MODS = Set.of(NS_CHASSE, "butchery");
    /** Mods dont tous les objets vont dans "Forge & Armures". */
    private static final Set<String> FORGE_MODS = Set.of(NS_FONDERIE, "magistuarmory", "immersive_armors", "alloy_forgery");
    /** Mods dont tous les objets vont dans "Nourriture & Bière". */
    private static final Set<String> FOOD_MODS = Set.of("drinkbeer");

    /** Mots (dans l'identifiant de l'objet) qui le classent en "Pêche" pour le mod citizenchasse. */
    private static final Set<String> FISHING_WORDS = Set.of(
            "fish", "fishing", "poisson", "peche", "net", "filet", "fillet", "rod", "canne", "bait", "appat", "hook", "hamecon",
            "fishmonger", "poissonnier", "cod", "cabillaud", "salmon", "saumon", "holder", "support");
    /** Mots (citizenchasse) qui classent en "Chasse & Boucherie" avant la peche (piege a lapin...). */
    private static final Set<String> HUNTING_WORDS = Set.of("trap", "piege", "rabbit", "lapin");
    /** Mots qui designent un filet de PECHE (et non un filet de poisson) -> doit etre fait a la forge. */
    private static final Set<String> FISHING_NET_WORDS = Set.of("net", "fishing_net", "filet_peche", "filet_de_peche");
    /** Mots qui designent une boite aux lettres -> doit etre faite a la forge. */
    private static final Set<String> MAILBOX_WORDS = Set.of("mailbox", "letterbox", "boite", "boite_aux_lettres", "postbox");
    /** Mots qui designent une boisson -> taverne. */
    private static final Set<String> DRINK_WORDS = Set.of("beer", "biere", "ale", "mead", "hydromel", "wine", "vin", "cider", "cidre");

    public static final class Entry {
        public final Item item;
        public final ItemStack icon;
        public final ResourceLocation id;
        public final String modId;
        public final String modName;
        public final String name;
        public final String searchKey;
        public CraftCategory category = CraftCategory.AUTRES;
        public boolean forgeRequired;
        public boolean tavernRequired;
        public final List<RecipeHolder<?>> recipes = new ArrayList<>();
        /** Fiche d'information (objet sans craft) : texte + objets acceptes. */
        public String info;
        public List<ItemStack> infoItems = List.of();

        Entry(ItemStack stack) {
            this(stack, null, null);
        }

        Entry(ItemStack stack, String customName, String sourceMod) {
            this.item = stack.getItem();
            this.icon = stack.copyWithCount(1); // garde les composants (nom, modele, donnees)
            this.id = BuiltInRegistries.ITEM.getKey(item);
            this.modId = sourceMod != null ? sourceMod : id.getNamespace();
            this.modName = modName(modId);
            this.name = customName != null ? customName : icon.getHoverName().getString();
            this.searchKey = normalize(name + " " + id + " " + modName);
        }

        public boolean isInfo() {
            return recipes.isEmpty();
        }
    }

    public final List<Entry> entries;
    public final Map<Item, Entry> byItem;

    private RecipeIndex(List<Entry> entries, Map<Item, Entry> byItem) {
        this.entries = entries;
        this.byItem = byItem;
    }

    public static RecipeIndex build(Minecraft mc) {
        ClientLevel level = mc.level;
        Map<Item, Entry> map = new LinkedHashMap<>();
        // Objets "maison" faits a partir d'un objet vanilla avec composants (ex. Citizen Realisme : la scie = cisailles renommees)
        Map<String, Entry> custom = new LinkedHashMap<>();
        if (level != null) {
            RegistryAccess access = level.registryAccess();
            for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
                try {
                    Recipe<?> recipe = holder.value();
                    if (recipe.isSpecial()) continue; // recettes dynamiques (teinture d'armure, feux d'artifice...)
                    ItemStack out = recipe.getResultItem(access);
                    if (out == null || out.isEmpty()) continue;
                    Entry e;
                    if (out.getComponentsPatch().isEmpty()) {
                        e = map.computeIfAbsent(out.getItem(), i -> new Entry(out));
                    } else {
                        String key = holder.id().getNamespace() + "|" + BuiltInRegistries.ITEM.getKey(out.getItem())
                                + "|" + out.getHoverName().getString();
                        e = custom.computeIfAbsent(key, k -> new Entry(out, null, holder.id().getNamespace()));
                    }
                    e.recipes.add(holder);
                } catch (Throwable ignored) {
                    // une recette d'un mod tiers mal formee ne doit pas casser le menu
                }
            }
        }
        for (Entry e : map.values()) classify(e);
        for (Entry e : custom.values()) classify(e);

        List<Entry> list = new ArrayList<>(map.values());
        list.addAll(custom.values());
        list.addAll(infoEntries());
        Collator collator = Collator.getInstance(Locale.FRENCH);
        collator.setStrength(Collator.PRIMARY);
        list.sort((a, b) -> collator.compare(a.name, b.name));
        list.forEach(e -> e.recipes.sort((a, b) -> a.id().compareTo(b.id())));
        return new RecipeIndex(List.copyOf(list), new HashMap<>(map));
    }

    /** Objets sans craft mais utiles a connaitre (appats). */
    private static List<Entry> infoEntries() {
        List<Entry> out = new ArrayList<>();
        if (ModList.get().isLoaded(NS_MENUISERIE)) addMenuiserie(out);
        if (RealismeRules.active()) {
            out.add(info("minecraft:enchanting_table", "Rituel d'enchantement", RealismeRules.MOD_ID, CraftCategory.ARTISANAT,
                    "Chaque option a un taux de réussite (+1 % par bibliothèque, 15 max) et demande des catalyseurs en plus "
                            + "du lapis et des niveaux. Option 1 : 1 éclat d'améthyste. Option 2 : 2 améthystes + 1 poudre de "
                            + "glowstone. Option 3 : 3 améthystes + 1 perle de l'Ender. Échec : niveaux, lapis et catalyseurs "
                            + "perdus, 35 % de risque de malédiction.",
                    "minecraft:amethyst_shard", "minecraft:glowstone_dust", "minecraft:ender_pearl", "minecraft:lapis_lazuli"));
            out.add(info("minecraft:crafting_table", "Craft réaliste : règles", RealismeRules.MOD_ID, CraftCategory.ARTISANAT,
                    "Crafts de 2 cases ou moins sans outil : instantanés. Les autres prennent du temps (barre de progression, "
                            + "bouger annule) et ont un taux de réussite : un échec fait perdre une partie des ingrédients. "
                            + "Certains crafts demandent un outil d'artisan dans l'inventaire : marteau, scie ou aiguille."));
        }
        if (!ModList.get().isLoaded(NS_CHASSE)) return out;

        Entry rabbitBait = new Entry(stack("minecraft:carrot"), "Appât à lapin", NS_CHASSE);
        rabbitBait.category = CraftCategory.BOUCHERIE;
        rabbitBait.info = "Pas de craft. Pour appâter le piège à lapin, utilisez une carotte, une carotte dorée ou un pissenlit.";
        rabbitBait.infoItems = List.of(stack("minecraft:carrot"), stack("minecraft:golden_carrot"), stack("minecraft:dandelion"));
        out.add(rabbitBait);

        Entry fishBait = new Entry(stack("minecraft:rotten_flesh"), "Appâts de pêche (sans craft)", NS_CHASSE);
        fishBait.category = CraftCategory.PECHE;
        fishBait.info = "En plus de l'appât à poisson, la chair putréfiée et l'œil d'araignée servent aussi d'appât.";
        fishBait.infoItems = List.of(stack("minecraft:rotten_flesh"), stack("minecraft:spider_eye"));
        out.add(fishBait);
        return out;
    }

    private static Entry info(String icon, String name, String ns, CraftCategory cat, String text, String... items) {
        Entry e = new Entry(stack(icon), name, ns);
        e.category = cat;
        e.info = text;
        List<ItemStack> l = new ArrayList<>();
        for (String i : items) l.add(stack(i));
        e.infoItems = List.copyOf(l);
        return e;
    }

    /** Fabrications faites dans les machines de Citizen Menuiserie (pas a l'etabli classique). */
    private static void addMenuiserie(List<Entry> out) {
        String keyExtra = " + 1 lingot pour la clé fournie (fer, cuivre ou or). Établi de menuiserie : "
                + "la porte et sa clé sont toujours données ensemble, un nom peut être gravé sur la serrure.";
        CraftCategory c = CraftCategory.MENUISERIE;
        String ns = NS_MENUISERIE;
        out.add(info("minecraft:oak_door", "Porte à clé en chêne", ns, c,
                "6 planches de chêne + 1 lingot de fer" + keyExtra, "minecraft:oak_planks", "minecraft:iron_ingot"));
        out.add(info("minecraft:spruce_door", "Porte à clé en épicéa", ns, c,
                "6 planches d'épicéa + 1 lingot de fer" + keyExtra, "minecraft:spruce_planks", "minecraft:iron_ingot"));
        out.add(info("minecraft:birch_door", "Porte à clé en bouleau", ns, c,
                "6 planches de bouleau + 1 lingot de fer" + keyExtra, "minecraft:birch_planks", "minecraft:iron_ingot"));
        out.add(info("minecraft:dark_oak_door", "Porte à clé en chêne noir", ns, c,
                "6 planches de chêne noir + 1 lingot de fer" + keyExtra, "minecraft:dark_oak_planks", "minecraft:iron_ingot"));
        out.add(info("minecraft:iron_door", "Porte à clé en fer forgé", ns, c,
                "7 lingots de fer" + keyExtra, "minecraft:iron_ingot"));
        out.add(info("minecraft:tripwire_hook", "Clé fournie avec la porte", ns, c,
                "Choisie à l'établi de menuiserie, en plus de la porte : clé en fer (+1 lingot de fer), "
                        + "en cuivre (+1 lingot de cuivre) ou en or (+1 lingot d'or). "
                        + "Ex. : porte en fer forgé + clé en fer = 8 lingots de fer.",
                "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:gold_ingot"));
        out.add(info("minecraft:tripwire_hook", "Clés vierges", ns, c,
                "Machine à clés : poser 1 lingot dans l'emplacement du bas, puis « Forger 2 clés vierges ». "
                        + "1 lingot de fer, de cuivre ou d'or = 2 clés vierges de cette matière.",
                "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:gold_ingot"));
        out.add(info("minecraft:name_tag", "Copie de clé", ns, c,
                "Machine à clés : la clé à copier en haut (rendue intacte), 1 clé vierge en bas. "
                        + "Nom du porteur facultatif (ex. « Paul, employé »). La copie ouvre la même porte "
                        + "et prend la matière de la clé vierge."));
        out.add(info("minecraft:iron_door", "Portes à clé : règles", ns, c,
                "Il faut tenir la bonne clé en main pour ouvrir. Sans clé : « Cette porte est verrouillée. "
                        + "Il vous faut une clé en main pour l'ouvrir. » Mauvaise clé : « Ce n'est pas la bonne clé. » "
                        + "Pour démonter la porte, il faut sa clé dans l'inventaire (elle garde sa serrure). "
                        + "Ni la redstone, ni les villageois, ni les zombies ne peuvent l'ouvrir."));
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    /** Appat a poisson de citizenchasse (pour la note "chair putrefiee / oeil d'araignee"). */
    public static boolean isFishBait(Entry e) {
        if (!e.modId.equals(NS_CHASSE) || e.isInfo()) return false;
        Set<String> w = new HashSet<>(Arrays.asList(e.id.getPath().split("[_/.\\-]")));
        return (w.contains("bait") || w.contains("appat")) && !w.contains("rabbit") && !w.contains("lapin");
    }

    private static void classify(Entry e) {
        String ns = e.modId;
        String path = e.id.getPath().toLowerCase(Locale.ROOT);
        Set<String> words = new HashSet<>(Arrays.asList(path.split("[_/.\\-]")));
        boolean isFood = e.icon.has(DataComponents.FOOD);
        boolean isDrink = containsAny(words, path, DRINK_WORDS);
        boolean isArmor = e.item instanceof ArmorItem;

        if (ns.equals(RealismeRules.MOD_ID)) {
            e.category = CraftCategory.ARTISANAT; // outils d'artisan et sacs
        } else if (ns.equals(NS_ARTEFACTS)) {
            e.category = CraftCategory.ARTEFACTS;
        } else if (ns.equals(NS_CHASSE) && containsAny(words, path, HUNTING_WORDS)) {
            e.category = CraftCategory.BOUCHERIE; // piege a lapin, appat a lapin...
        } else if (FISHING_MODS.contains(ns)
                || (ns.equals(NS_CHASSE) && containsAny(words, path, FISHING_WORDS))
                || path.contains("fishing")) {
            e.category = CraftCategory.PECHE;
        } else if (BUTCHER_MODS.contains(ns)) {
            e.category = CraftCategory.BOUCHERIE;
        } else if (ns.equals(NS_HIBOU)) {
            e.category = CraftCategory.COURRIER;
        } else if (ns.equals(NS_MENUISERIE)) {
            e.category = CraftCategory.MENUISERIE;
        } else if (ns.equals(NS_AFFICHE)) {
            e.category = CraftCategory.AFFICHES;
        } else if (ns.equals(NS_ECONOMY)) {
            e.category = CraftCategory.ECONOMIE;
        } else if (FORGE_MODS.contains(ns) || isArmor) {
            e.category = CraftCategory.FORGE;
        } else if (FOOD_MODS.contains(ns) || isFood || isDrink) {
            e.category = CraftCategory.NOURRITURE;
        } else if (ns.equals("minecraft")) {
            e.category = CraftCategory.MINECRAFT;
        } else {
            e.category = CraftCategory.AUTRES;
        }

        // ---- Regles RP du serveur ----
        boolean fishingNet = (e.category == CraftCategory.PECHE) && containsAny(words, path, FISHING_NET_WORDS)
                && !path.contains("fillet");
        // Hibou postal : boite aux lettres, lettres et cartons d'envoi -> forge
        boolean mailbox = ns.equals(NS_HIBOU);
        e.forgeRequired = e.category == CraftCategory.ARTEFACTS || e.category == CraftCategory.AFFICHES
                || isArmor || fishingNet || mailbox;
        e.tavernRequired = !e.forgeRequired && e.category == CraftCategory.NOURRITURE;
        if (e.category == CraftCategory.ARTISANAT) { // sacoche = armure de cheval renommee : pas une armure RP
            e.forgeRequired = false;
            e.tavernRequired = false;
        }
    }

    /** Un mot entier de l'identifiant, ou une expression composee (avec _) contenue dans le chemin. */
    private static boolean containsAny(Set<String> words, String path, Set<String> keys) {
        for (String k : keys) {
            if (k.contains("_") ? path.contains(k) : words.contains(k)) return true;
        }
        return false;
    }

    public static String modName(String modId) {
        if (modId.equals("minecraft")) return "Minecraft";
        return ModList.get().getModContainerById(modId)
                .map(c -> c.getModInfo().getDisplayName())
                .orElse(modId);
    }

    public static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
