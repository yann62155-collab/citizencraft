package fr.citizen.citizencraft;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Autotest lance par GitHub Actions (-Dcitizencraft.selftest=true) : demarre un vrai serveur NeoForge 21.1.215,
 * verifie que le mod charge cote serveur (sans code client) et que /craft et /recettes existent, puis arrete le serveur.
 */
public final class SelfTest {

    private static final Logger LOG = LogUtils.getLogger();

    private SelfTest() {}

    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("citizencraft.selftest")) return;
        MinecraftServer server = event.getServer();
        boolean ok = true;
        for (String cmd : new String[]{"craft", "recettes"}) {
            boolean found = server.getCommands().getDispatcher().getRoot().getChild(cmd) != null;
            LOG.info("[test] commande /{} : {}", cmd, found ? "OK" : "ABSENTE");
            ok &= found;
        }
        boolean cfg = CraftConfig.JOIN_MESSAGE_ENABLED.get() != null;
        LOG.info("[test] configuration : {}", cfg ? "OK" : "ERREUR");
        ok &= cfg;
        LOG.info(ok ? "SELFTEST OK" : "SELFTEST ECHEC");
        server.halt(false);
    }
}
