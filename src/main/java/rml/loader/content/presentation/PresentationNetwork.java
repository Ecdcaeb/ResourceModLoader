package rml.loader.content.presentation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Dialog transport keeps the authoritative action list on the server. */
public final class PresentationNetwork {
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("rml_presentation");
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static PresentationModule module;
    private static volatile ClientHandler clientHandler;
    private static boolean registered;

    static synchronized void installClientHandler(ClientHandler handler) {
        if (handler == null) throw new NullPointerException("handler");
        clientHandler = handler;
    }

    static synchronized void register(PresentationModule owner) {
        module = owner;
        if (registered) return;
        CHANNEL.registerMessage(OpenHandler.class, OpenMessage.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(ChooseHandler.class, ChooseMessage.class, 1, Side.SERVER);
        CHANNEL.registerMessage(VariantHandler.class, VariantMessage.class, 2, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(PresentationNetwork.class);
        registered = true;
    }

    interface ClientHandler {
        void open(ResourceLocation id, String title, String body, String[] actions);
        void variant(int entityId, ResourceLocation id, String type, ResourceLocation entity, ResourceLocation texture);
    }

    private static ClientHandler clientHandler() {
        ClientHandler handler = clientHandler;
        if (handler == null) throw new IllegalStateException("Presentation client packet handler is not installed");
        return handler;
    }

    static void open(EntityPlayer player, ResourceLocation id, JsonObject json) {
        if (!(player instanceof EntityPlayerMP)) return;
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        JsonArray actions = json.getAsJsonArray("actions");
        SESSIONS.put(player.getUniqueID(), new Session(id, player.world, actions.size()));
        CHANNEL.sendTo(new OpenMessage(id, json.get("title").getAsString(), json.get("body").getAsString(), labels(actions)), serverPlayer);
    }

    private static String[] labels(JsonArray actions) {
        String[] labels = new String[actions.size()];
        for (int i = 0; i < labels.length; i++) {
            JsonElement item = actions.get(i);
            labels[i] = item.isJsonObject() && item.getAsJsonObject().has("label")
                    ? item.getAsJsonObject().get("label").getAsString() : "Continue";
        }
        return labels;
    }

    static void syncVariant(net.minecraft.entity.Entity entity, ResourceLocation id, String type,
                            PresentationCodecs.EntityVariant value) {
        sendVariant(entity, id, type, value.entity, value.texture);
    }

    static void syncSoundVariant(net.minecraft.entity.Entity entity, ResourceLocation id, String type) {
        sendVariant(entity, id, type, null, null);
    }

    static void syncSurfaceVariant(net.minecraft.entity.Entity entity, ResourceLocation id, String type, ResourceLocation texture) {
        sendVariant(entity, id, type, null, texture);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        net.minecraft.entity.Entity entity = event.getTarget();
        String variantId = entity.getEntityData().getString("rml_presentation_variant");
        String soundId = entity.getEntityData().getString("rml_presentation_sound_variant");
        if (!variantId.isEmpty()) {
            ResourceLocation id = new ResourceLocation(variantId);
            String type = entity.getEntityData().getString("rml_presentation_variant_type");
            String entityType = entity.getEntityData().getString("rml_presentation_variant_entity");
            String texture = entity.getEntityData().getString("rml_presentation_variant_texture");
            VariantMessage message = new VariantMessage(entity.getEntityId(), id, type,
                    entityType.isEmpty() ? null : new ResourceLocation(entityType), texture.isEmpty() ? null : new ResourceLocation(texture));
            CHANNEL.sendTo(message, (EntityPlayerMP) event.getEntityPlayer());
        }
        if (!soundId.isEmpty()) {
            String type = entity.getEntityData().getString("rml_presentation_sound_variant_type");
            CHANNEL.sendTo(new VariantMessage(entity.getEntityId(), new ResourceLocation(soundId), type, null, null),
                    (EntityPlayerMP) event.getEntityPlayer());
        }
    }

    private static void sendVariant(net.minecraft.entity.Entity entity, ResourceLocation id, String type,
                                    ResourceLocation entityType, ResourceLocation texture) {
        VariantMessage message = new VariantMessage(entity.getEntityId(), id, type, entityType, texture);
        CHANNEL.sendToAllTracking(message, entity);
        if (entity instanceof EntityPlayerMP) CHANNEL.sendTo(message, (EntityPlayerMP) entity);
    }

    private static void choose(EntityPlayerMP player, ResourceLocation id, int index) {
        Session session = SESSIONS.get(player.getUniqueID());
        if (session == null || !session.id.equals(id) || session.world != player.world) return;
        if (index == -1) { SESSIONS.remove(player.getUniqueID()); return; }
        if (index < 0 || index >= session.actionCount) return;
        SESSIONS.remove(player.getUniqueID());
        module.executeDialogAction(player, id, index);
    }

    @SubscribeEvent
    public static void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        java.util.Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) if (iterator.next().getValue().world == event.getWorld()) iterator.remove();
    }

    private static final class Session {
        final ResourceLocation id;
        final net.minecraft.world.World world;
        final int actionCount;
        Session(ResourceLocation id, net.minecraft.world.World world, int actionCount) { this.id = id; this.world = world; this.actionCount = actionCount; }
    }
    private static void writeBoundedString(PacketBuffer packet, String value, int maxLength, String field) {
        if (value == null || value.length() > maxLength)
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        packet.writeString(value);
    }

    public static final class OpenMessage implements IMessage {
        private ResourceLocation id;
        private String title;
        private String body;
        private String[] actions;
        public OpenMessage() { }
        public OpenMessage(ResourceLocation id, String title, String body, String[] actions) { this.id = id; this.title = title; this.body = body; this.actions = actions; }
        @Override public void toBytes(ByteBuf buf) {
            PacketBuffer out = new PacketBuffer(buf);
            out.writeResourceLocation(id);
            writeBoundedString(out, title, 256, "dialog title");
            writeBoundedString(out, body, 2048, "dialog body");
            if (actions == null || actions.length > 16) throw new IllegalArgumentException("Dialog supports at most 16 actions");
            out.writeVarInt(actions.length);
            for (String action : actions) writeBoundedString(out, action, 128, "dialog action");
        }
        @Override public void fromBytes(ByteBuf buf) {
            PacketBuffer in = new PacketBuffer(buf);
            id = in.readResourceLocation(); title = in.readString(256); body = in.readString(2048);
            int count = in.readVarInt();
            if (count < 0 || count > 16) throw new IllegalArgumentException("Invalid dialog action count");
            actions = new String[count];
            for (int i = 0; i < count; i++) actions[i] = in.readString(128);
        }
    }

    public static final class ChooseMessage implements IMessage {
        private ResourceLocation id;
        private int index;
        public ChooseMessage() { }
        public ChooseMessage(ResourceLocation id, int index) { this.id = id; this.index = index; }
        @Override public void toBytes(ByteBuf buf) { PacketBuffer out = new PacketBuffer(buf); out.writeResourceLocation(id); out.writeVarInt(index); }
        @Override public void fromBytes(ByteBuf buf) { PacketBuffer in = new PacketBuffer(buf); id = in.readResourceLocation(); index = in.readVarInt(); }
    }

    public static final class VariantMessage implements IMessage {
        private int entityId;
        private ResourceLocation variant, entity, texture;
        private String type;
        public VariantMessage() { }
        public VariantMessage(int entityId, ResourceLocation variant, String type, ResourceLocation entity, ResourceLocation texture) {
            this.entityId = entityId; this.variant = variant; this.type = type; this.entity = entity; this.texture = texture;
        }
        @Override public void toBytes(ByteBuf buf) {
            PacketBuffer out = new PacketBuffer(buf);
            out.writeVarInt(entityId); out.writeResourceLocation(variant);
            writeBoundedString(out, type, 64, "variant type");
            out.writeBoolean(entity != null);
            if (entity != null) out.writeResourceLocation(entity);
            out.writeBoolean(texture != null);
            if (texture != null) out.writeResourceLocation(texture);
        }
        @Override public void fromBytes(ByteBuf buf) {
            PacketBuffer in = new PacketBuffer(buf);
            entityId = in.readVarInt(); variant = in.readResourceLocation(); type = in.readString(64);
            entity = in.readBoolean() ? in.readResourceLocation() : null;
            texture = in.readBoolean() ? in.readResourceLocation() : null;
        }
    }

    public static final class VariantHandler implements IMessageHandler<VariantMessage, IMessage> {
        @Override public IMessage onMessage(VariantMessage message, MessageContext context) {
            clientHandler().variant(message.entityId, message.variant, message.type, message.entity, message.texture);
            return null;
        }
    }

    public static final class OpenHandler implements IMessageHandler<OpenMessage, IMessage> {
        @Override public IMessage onMessage(OpenMessage message, MessageContext context) {
            clientHandler().open(message.id, message.title, message.body, message.actions);
            return null;
        }
    }

    static void choose(ResourceLocation id, int index) { CHANNEL.sendToServer(new ChooseMessage(id, index)); }

    public static final class ChooseHandler implements IMessageHandler<ChooseMessage, IMessage> {
        @Override public IMessage onMessage(ChooseMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> choose(player, message.id, message.index));
            return null;
        }
    }


}
