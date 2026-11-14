package fr.citizen.citizencraft;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Fichier : config/citizencraft-common.toml (modifiable par FTP sur le serveur).
 */
public final class CraftConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue JOIN_MESSAGE_ENABLED;
    public static final ModConfigSpec.IntValue JOIN_MESSAGE_DELAY_SECONDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("connexion");
        JOIN_MESSAGE_ENABLED = b
                .comment("Afficher le message qui invite le joueur a taper /craft quand il rejoint le serveur.")
                .define("messageActif", true);
        JOIN_MESSAGE_DELAY_SECONDS = b
                .comment("Delai (en secondes) avant l'envoi du message, pour qu'il ne soit pas noye dans les autres messages de connexion.")
                .defineInRange("delaiSecondes", 5, 0, 120);
        b.pop();
        SPEC = b.build();
    }

    private CraftConfig() {}
}
