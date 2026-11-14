package fr.citizen.citizencraft.client;

import net.minecraft.client.Minecraft;

/**
 * Point d'entree client. Ne jamais referencer cette classe depuis du code serveur (hors handler de paquet).
 */
public final class ClientHooks {

    private ClientHooks() {}

    public static void openCraftScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new CraftScreen());
    }
}
