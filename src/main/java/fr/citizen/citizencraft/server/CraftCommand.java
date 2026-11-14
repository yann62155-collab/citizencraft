package fr.citizen.citizencraft.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.citizen.citizencraft.network.OpenCraftPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * /craft (alias /recettes) : ouvre le Livre des Recettes du Royaume. Accessible a tous les joueurs.
 */
public final class CraftCommand {

    private CraftCommand() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("craft").executes(CraftCommand::open));
        dispatcher.register(Commands.literal("recettes").executes(CraftCommand::open));
    }

    private static int open(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (player.connection.hasChannel(OpenCraftPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, OpenCraftPayload.INSTANCE);
        } else {
            JoinNotifier.sendConsoleBook(player); // joueur sans le mod (Xbox / console)
        }
        return 1;
    }
}
