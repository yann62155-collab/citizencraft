package fr.citizen.citizencraft.server;

import fr.citizen.citizencraft.CraftConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Message de bienvenue : invite le joueur a taper /craft quelques secondes apres sa connexion.
 */
public final class JoinNotifier {

    private static final TextColor GOLD = TextColor.fromRgb(0xD4AF37);
    private static final TextColor PARCHMENT = TextColor.fromRgb(0xE8D9B0);
    private static final TextColor DARK_RED = TextColor.fromRgb(0xB03030);

    /** UUID du joueur -> ticks restants avant l'envoi du message. */
    private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();

    private JoinNotifier() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CraftConfig.JOIN_MESSAGE_ENABLED.get()) return;
        PENDING.put(player.getUUID(), Math.max(1, CraftConfig.JOIN_MESSAGE_DELAY_SECONDS.get() * 20));
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            int left = e.getValue() - 1;
            if (left > 0) {
                e.setValue(left);
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player != null) send(player);
        }
    }

    /** Version texte pour les joueurs sans le mod (Xbox / console via Citizen Bedrock). */
    public static void sendConsoleBook(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("✦ Le Livre des Recettes du Royaume ✦").withStyle(s -> s.withColor(GOLD).withBold(true)));
        player.sendSystemMessage(Component.literal("Le livre illustré n'est disponible que sur PC (mod Citizen Craft). Règles du royaume :")
                .withStyle(s -> s.withColor(PARCHMENT)));
        rules(player);
    }

    private static void rules(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("  Artefacts, armures, filets de pêche, boîtes aux lettres, lettres, beuglantes, cartons d'envoi et affiches : à la forge.")
                .withStyle(s -> s.withColor(DARK_RED).withItalic(true)));
        player.sendSystemMessage(Component.literal("  Nourriture et bière : à la taverne ou au restaurant.")
                .withStyle(s -> s.withColor(DARK_RED).withItalic(true)));
        player.sendSystemMessage(Component.literal("  Portes à clé et clés : fabriquées uniquement par le menuisier.")
                .withStyle(s -> s.withColor(DARK_RED).withItalic(true)));
        player.sendSystemMessage(Component.literal("  La pêche est réservée aux pêcheurs. Seuls le pêcheur, la taverne et le restaurant peuvent cuisiner le poisson.")
                .withStyle(s -> s.withColor(DARK_RED).withItalic(true)));
    }

    private static void send(ServerPlayer player) {
        MutableComponent button = Component.literal("[Ouvrir le livre]").withStyle(s -> s
                .withColor(GOLD)
                .withBold(true)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/craft"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Cliquer pour ouvrir /craft").withStyle(ChatFormatting.GRAY))));

        player.sendSystemMessage(Component.literal("✦ ─────────────────────────── ✦").withStyle(s -> s.withColor(GOLD)));
        player.sendSystemMessage(Component.literal("  Le Livre des Recettes du Royaume").withStyle(s -> s.withColor(GOLD).withBold(true)));
        player.sendSystemMessage(Component.literal("  Tapez ").withStyle(s -> s.withColor(PARCHMENT))
                .append(Component.literal("/craft").withStyle(s -> s.withColor(GOLD).withBold(true)))
                .append(Component.literal(" pour consulter toutes les recettes du royaume. ").withStyle(s -> s.withColor(PARCHMENT)))
                .append(button));
        rules(player);
        player.sendSystemMessage(Component.literal("✦ ─────────────────────────── ✦").withStyle(s -> s.withColor(GOLD)));
    }
}
