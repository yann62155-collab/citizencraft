package fr.citizen.citizencraft.client;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.Tags;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reprise cote client des regles de craft de Citizen Realisme (CraftRules.java), avec ses REGLAGES PAR DEFAUT.
 * Si config/citizenrealisme/realisme.json est modifie sur le serveur, mettre a jour les constantes ci-dessous.
 */
public final class RealismeRules {

    public static final String MOD_ID = "citizenrealisme";
    public static final String TOOL_KEY = "citizenrealisme_outil";
    public static final String BAG_KEY = "citizenrealisme_sac";
    public static final String MARTEAU = "marteau";
    public static final String SCIE = "scie";
    public static final String AIGUILLE = "aiguille";

    // ---- realisme.json > craft (valeurs par defaut) ----
    private static final int CASES_INSTANTANEES = 2;
    private static final int TICKS_PAR_CASE = 8;
    private static final int TICKS_AVEC_OUTIL = 20;
    private static final int PENALITE_PAR_CASE = 2;
    private static final int PENALITE_PAR_INGREDIENT = 3;
    public static final int BONUS_OUTIL = 5;
    private static final int REUSSITE_MIN = 70;
    private static final int REUSSITE_MAX = 99;
    public static final int PERTE_POURCENT = 50;
    private static final Set<String> OUTIL_SHIELD = Set.of("minecraft:shield");     // outilParObjet -> marteau
    private static final Set<String> SANS_OUTIL = Set.of("minecraft:crafting_table"); // sansOutil

    private static final List<TagKey<Item>> SCIE_TAGS = List.of(
            ItemTags.WOODEN_DOORS, ItemTags.WOODEN_TRAPDOORS, ItemTags.WOODEN_FENCES, ItemTags.FENCE_GATES,
            ItemTags.BOATS, ItemTags.CHEST_BOATS, ItemTags.SIGNS, ItemTags.HANGING_SIGNS,
            ItemTags.WOODEN_STAIRS, ItemTags.WOODEN_SLABS);
    private static final Set<Item> SCIE_ITEMS = Set.of(Items.CHEST, Items.BARREL, Items.BOOKSHELF, Items.CHISELED_BOOKSHELF,
            Items.LADDER, Items.LECTERN, Items.LOOM, Items.CARTOGRAPHY_TABLE, Items.FLETCHING_TABLE, Items.SMITHING_TABLE,
            Items.COMPOSTER, Items.ARMOR_STAND, Items.ITEM_FRAME, Items.PAINTING, Items.JUKEBOX, Items.NOTE_BLOCK);
    private static final Set<Item> AIGUILLE_ITEMS = Set.of(Items.BOOK, Items.WRITABLE_BOOK, Items.LEAD);

    /** Resultat du calcul pour une recette de table de craft. */
    public record Plan(boolean instant, String tool, int rate, int ticks) {}

    private RealismeRules() {}

    public static boolean active() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static String toolName(String id) {
        return switch (id) {
            case MARTEAU -> "Marteau de forgeron";
            case SCIE -> "Scie de menuisier";
            case AIGUILLE -> "Aiguille de couturier";
            default -> id;
        };
    }

    public static String tag(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag t = data.copyTag();
        return t.contains(key) ? t.getString(key) : null;
    }

    public static Plan plan(ItemStack result, List<Ingredient> ingredients) {
        int filled = 0;
        Set<Item> kinds = new HashSet<>();
        boolean metal = false;
        for (Ingredient in : ingredients) {
            if (in.isEmpty()) continue;
            filled++;
            ItemStack[] opts = in.getItems();
            if (opts.length > 0) {
                kinds.add(opts[0].getItem());
                if (opts[0].is(Tags.Items.INGOTS)) metal = true;
            }
        }
        String tool = requiredTool(result, metal);
        if (tool == null && filled <= CASES_INSTANTANEES) return new Plan(true, null, 100, 0);
        int rate = 100 - Math.max(0, filled - CASES_INSTANTANEES) * PENALITE_PAR_CASE
                - Math.max(0, kinds.size() - 1) * PENALITE_PAR_INGREDIENT;
        rate = Math.max(REUSSITE_MIN, Math.min(REUSSITE_MAX, rate));
        int ticks = Math.max(10, filled * TICKS_PAR_CASE + (tool != null ? TICKS_AVEC_OUTIL : 0));
        return new Plan(false, tool, rate, ticks);
    }

    public static int rateWithTool(int rate) {
        return Math.max(REUSSITE_MIN, Math.min(REUSSITE_MAX, rate + BONUS_OUTIL));
    }

    private static String requiredTool(ItemStack result, boolean metalIngredient) {
        String id = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
        String own = tag(result, TOOL_KEY);
        if (own != null) return SCIE.equals(own) ? MARTEAU : null;
        if (tag(result, BAG_KEY) != null) return AIGUILLE;
        if (SANS_OUTIL.contains(id)) return null;
        if (OUTIL_SHIELD.contains(id)) return MARTEAU;

        Item item = result.getItem();
        if (item instanceof ArmorItem armor) {
            String material = armor.getMaterial().unwrapKey().map(k -> k.location().getPath()).orElse("");
            if (material.equals("leather")) return AIGUILLE;
            if (material.equals("turtle") || material.equals("armadillo")) return null;
            return MARTEAU;
        }
        if (item instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            return tier == Tiers.WOOD || tier == Tiers.STONE ? null : MARTEAU;
        }
        if (result.is(ItemTags.BEDS) || result.is(ItemTags.BANNERS) || result.is(ItemTags.WOOL_CARPETS) || AIGUILLE_ITEMS.contains(item)) {
            return AIGUILLE;
        }
        if (SCIE_ITEMS.contains(item)) return SCIE;
        for (TagKey<Item> t : SCIE_TAGS) {
            if (result.is(t)) return SCIE;
        }
        if (metalIngredient && !result.is(Tags.Items.INGOTS) && !result.is(Tags.Items.NUGGETS)
                && !result.is(Tags.Items.STORAGE_BLOCKS)) {
            return MARTEAU;
        }
        return null;
    }

    /** Texte d'aide des objets de Citizen Realisme (outils et sacs). */
    public static String describe(ItemStack stack) {
        String tool = tag(stack, TOOL_KEY);
        if (tool != null) {
            String use = switch (tool) {
                case MARTEAU -> "armes, armures et objets en métal";
                case SCIE -> "portes, trappes, barrières, coffres, tonneaux, bateaux, panneaux, échelles…";
                case AIGUILLE -> "armures en cuir, lits, bannières, tapis, livres, laisses, sacs";
                default -> "";
            };
            String extra = SCIE.equals(tool) ? " Se fabrique avec un marteau dans l'inventaire." : "";
            return "Outil d'artisan pour : " + use + ". À garder dans l'inventaire, il s'use ; en bon état : +"
                    + BONUS_OUTIL + " % de réussite." + extra;
        }
        String bag = tag(stack, BAG_KEY);
        if (bag != null) {
            String stats = "sacados".equals(bag) ? "+18 cases, +45 kg" : "+9 cases, +20 kg";
            return "Sac : " + stats + ". Demande une aiguille. Clic droit pour l'équiper ; "
                    + "/realisme sac retirer pour l'enlever (il doit être vide). Tombe au sol à la mort.";
        }
        return null;
    }
}
