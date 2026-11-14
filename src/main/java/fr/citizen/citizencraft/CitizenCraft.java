package fr.citizen.citizencraft;

import fr.citizen.citizencraft.network.OpenCraftPayload;
import fr.citizen.citizencraft.server.CraftCommand;
import fr.citizen.citizencraft.server.JoinNotifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Citizen Craft : commande /craft qui ouvre le "Livre des Recettes du Royaume".
 */
@Mod(CitizenCraft.MOD_ID)
public class CitizenCraft {

    public static final String MOD_ID = "citizencraft";

    public CitizenCraft(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CraftConfig.SPEC);

        modBus.addListener(CitizenCraft::registerPayloads);

        NeoForge.EVENT_BUS.addListener(CraftCommand::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(JoinNotifier::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(JoinNotifier::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(JoinNotifier::onServerTick);
        NeoForge.EVENT_BUS.addListener(SelfTest::onServerStarted);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional() : les joueurs sans le mod (Xbox via Citizen Bedrock) peuvent toujours se connecter
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(OpenCraftPayload.TYPE, OpenCraftPayload.STREAM_CODEC, OpenCraftPayload::handle);
    }
}
