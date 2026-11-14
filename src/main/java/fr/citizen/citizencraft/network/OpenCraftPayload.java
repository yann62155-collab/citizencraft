package fr.citizen.citizencraft.network;

import fr.citizen.citizencraft.CitizenCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Paquet serveur -> client : "ouvre le Livre des Recettes".
 * Les recettes elles-memes sont deja synchronisees par Minecraft (1.21.1) vers le client.
 */
public record OpenCraftPayload() implements CustomPacketPayload {

    public static final OpenCraftPayload INSTANCE = new OpenCraftPayload();

    public static final CustomPacketPayload.Type<OpenCraftPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CitizenCraft.MOD_ID, "open_craft"));

    public static final StreamCodec<ByteBuf, OpenCraftPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenCraftPayload payload, IPayloadContext context) {
        // Code client uniquement : ClientHooks n'est jamais charge sur le serveur dedie.
        context.enqueueWork(fr.citizen.citizencraft.client.ClientHooks::openCraftScreen);
    }
}
