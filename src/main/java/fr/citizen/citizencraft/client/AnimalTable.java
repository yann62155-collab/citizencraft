package fr.citizen.citizencraft.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Onglet "Animaux" : ce que rapporte chaque espece (selon son poids) avec citizenchasse.
 *
 * >>> A ALIGNER sur les valeurs reelles du mod citizenchasse (poids par espece, regle de decoupe). <<<
 *
 * Regle viande (cahier des charges citizenchasse) :
 *   minimum 30 kg par bete, minimum 2 morceaux, +1 morceau par tranche de 2 kg au-dela du poids minimum.
 *   => morceaux = BASE + floor((poids - 30) / 2)
 * Poissons : filets = BASE + floor((poids - poidsMin) / kgParFilet).
 */
public final class AnimalTable {

    public static final double MEAT_MIN_KG = 30.0;
    public static final int MEAT_BASE_PIECES = 2;
    public static final double MEAT_KG_PER_PIECE = 2.0;

    public enum Kind { VIANDE, POULET, POISSON }

    public record Animal(String name, String entityId, Kind kind, double minKg, double maxKg,
                         int basePieces, double refKg, double kgPerPiece, String productItem, String productName) {

        public int piecesAt(double kg) {
            return basePieces + (int) Math.floor(Math.max(0, kg - refKg) / kgPerPiece);
        }

        public int minPieces() { return piecesAt(minKg); }
        public int maxPieces() { return piecesAt(maxKg); }

        public ItemStack egg() {
            ResourceLocation id = ResourceLocation.parse(entityId);
            return new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg")));
        }

        public ItemStack product() {
            return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(productItem)));
        }
    }

    private static Animal meat(String name, String entity, double min, double max, String item, String product) {
        return new Animal(name, entity, Kind.VIANDE, min, max, MEAT_BASE_PIECES, MEAT_MIN_KG, MEAT_KG_PER_PIECE, item, product);
    }

    private static Animal poultry(String name, String entity, double min, double max, String item, String product) {
        return new Animal(name, entity, Kind.POULET, min, max, MEAT_BASE_PIECES, MEAT_MIN_KG, MEAT_KG_PER_PIECE, item, product);
    }

    private static Animal fish(String name, String entity, double min, double max, double kgPerFillet) {
        return new Animal(name, entity, Kind.POISSON, min, max, 1, min, kgPerFillet, "minecraft:cooked_cod", "Filets de poisson");
    }

    /** Poids min / max en kg. Les gros poids sont rares (cf. citizenchasse). */
    public static final List<Animal> ANIMALS = List.of(
            poultry("Poulet",  "minecraft:chicken", 30, 34,  "minecraft:chicken",  "Morceaux de poulet"),
            meat("Lapin",      "minecraft:rabbit",  30, 34,  "minecraft:rabbit",   "Morceaux de lapin"),
            meat("Mouton",     "minecraft:sheep",   35, 60,  "minecraft:mutton",   "Morceaux de mouton"),
            meat("Chèvre",     "minecraft:goat",    35, 55,  "minecraft:mutton",   "Morceaux de viande"),
            meat("Cochon",     "minecraft:pig",     40, 90,  "minecraft:porkchop", "Morceaux de porc"),
            meat("Vache",      "minecraft:cow",     60, 120, "minecraft:beef",     "Morceaux de boeuf"),
            meat("Meuh-champi","minecraft:mooshroom", 60, 120, "minecraft:beef",   "Morceaux de boeuf"),
            meat("Lama",       "minecraft:llama",   60, 110, "minecraft:beef",     "Morceaux de viande"),
            meat("Âne",        "minecraft:donkey",  70, 120, "minecraft:beef",     "Morceaux de viande"),
            meat("Cheval",     "minecraft:horse",   80, 150, "minecraft:beef",     "Morceaux de viande"),
            meat("Ours polaire","minecraft:polar_bear", 90, 160, "minecraft:beef", "Morceaux de viande"),
            meat("Hoglin",     "minecraft:hoglin",  80, 130, "minecraft:porkchop", "Morceaux de porc"),
            fish("Morue",          "minecraft:cod",           1.0, 4.0, 0.5),
            fish("Saumon",         "minecraft:salmon",        2.0, 8.0, 1.0),
            fish("Poisson tropical","minecraft:tropical_fish", 0.5, 2.0, 0.5),
            fish("Poisson-globe",  "minecraft:pufferfish",    0.5, 2.0, 0.5)
    );

    private AnimalTable() {}
}
