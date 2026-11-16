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
        void open(OpenMessage message);
        void variant(int entityId, ResourceLocation id, String type, ResourceLocation entity, ResourceLocation texture);
    }

    private static ClientHandler clientHandler() {
        ClientHandler handler = clientHandler;
        if (handler == null) throw new IllegalStateException("Presentation client packet handler is not installed");
        return handler;
    }

    static void open(EntityPlayerMP player, ResourceLocation id, PresentationCodecs.DialogDefinition definition, net.minecraft.nbt.NBTTagCompound inherited) {
        UUID token = UUID.randomUUID();
        SESSIONS.put(player.getUniqueID(), new Session(id, player.world, token, definition, inherited.copy()));
        CHANNEL.sendTo(new OpenMessage(id, token, definition), player);
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

    private static void choose(EntityPlayerMP player, ChooseMessage message) {
        Session session = SESSIONS.get(player.getUniqueID());
        if (session == null || !session.id.equals(message.id) || !session.token.equals(message.token) || session.world != player.world || player.isDead) return;
        if (message.index == -1) { SESSIONS.remove(player.getUniqueID()); return; }
        if (message.index < 0 || message.index >= session.definition.actions.length) return;
        net.minecraft.nbt.NBTTagCompound inputs;
        try {
            inputs = PresentationDialogs.validateInputs(session.definition.inputs, message.inputs);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(new net.minecraft.util.text.TextComponentString(ex.getMessage()));
            CHANNEL.sendTo(new OpenMessage(session.id, session.token, session.definition), player);
            return;
        }
        net.minecraft.nbt.NBTTagCompound merged = session.inherited.copy();
        merged.merge(inputs);
        SESSIONS.remove(player.getUniqueID());
        session.definition.actions[message.index].execute(new PresentationDialogs.ActionContext(player, merged, module, session.id));
    }

    @SubscribeEvent
    public static void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public static void changeDimension(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) {
        SESSIONS.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public static void respawn(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent event) {
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
        final UUID token;
        final PresentationCodecs.DialogDefinition definition;
        final net.minecraft.nbt.NBTTagCompound inherited;
        Session(ResourceLocation id, net.minecraft.world.World world, UUID token, PresentationCodecs.DialogDefinition definition, net.minecraft.nbt.NBTTagCompound inherited) {
            this.id = id; this.world = world; this.token = token; this.definition = definition; this.inherited = inherited;
        }
    }
    private static void writeBoundedString(PacketBuffer packet, String value, int maxLength, String field) {
        if (value == null || value.length() > maxLength)
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        packet.writeString(value);
    }

    public static final class OpenMessage implements IMessage {
        ResourceLocation id;
        UUID token;
        net.minecraft.util.text.ITextComponent title, body;
        net.minecraft.util.text.ITextComponent[] actions;
        PresentationDialogs.Input[] inputs;
        public OpenMessage() { }
        OpenMessage(ResourceLocation id, UUID token, PresentationCodecs.DialogDefinition definition) {
            this.id = id; this.token = token; this.title = definition.title; this.body = definition.body; this.inputs = definition.inputs;
            actions = new net.minecraft.util.text.ITextComponent[definition.actions.length];
            for (int i = 0; i < actions.length; i++) actions[i] = definition.actions[i].label();
        }
        @Override public void toBytes(ByteBuf buf) {
            PacketBuffer out = new PacketBuffer(buf);
            out.writeResourceLocation(id); out.writeUniqueId(token); out.writeTextComponent(title); out.writeTextComponent(body);
            out.writeVarInt(actions.length); for (net.minecraft.util.text.ITextComponent action : actions) out.writeTextComponent(action);
            out.writeVarInt(inputs.length); for (PresentationDialogs.Input input : inputs) input.write(out);
        }
        @Override public void fromBytes(ByteBuf buf) {
            PacketBuffer in = new PacketBuffer(buf);
            id = in.readResourceLocation(); token = in.readUniqueId(); title = PresentationDialogs.readText(in); body = PresentationDialogs.readText(in);
            int count = in.readVarInt();
            if (count < 1 || count > 64) throw new IllegalArgumentException("Invalid dialog action count");
            actions = new net.minecraft.util.text.ITextComponent[count];
            for (int i = 0; i < count; i++) actions[i] = PresentationDialogs.readText(in);
            count = in.readVarInt(); if (count < 0 || count > 16) throw new IllegalArgumentException("Invalid input count");
            inputs = new PresentationDialogs.Input[count]; for (int i = 0; i < count; i++) inputs[i] = PresentationDialogs.Input.read(in);
        }
    }

    public static final class ChooseMessage implements IMessage {
        ResourceLocation id;
        UUID token;
        int index;
        Map<String, String> inputs;
        public ChooseMessage() { }
        public ChooseMessage(ResourceLocation id, UUID token, int index, Map<String, String> inputs) {
            this.id = id; this.token = token; this.index = index; this.inputs = new java.util.LinkedHashMap<>(inputs);
        }
        @Override public void toBytes(ByteBuf buf) {
            PacketBuffer out = new PacketBuffer(buf); out.writeResourceLocation(id); out.writeUniqueId(token); out.writeVarInt(index);
            if (inputs.size() > 16) throw new IllegalArgumentException("Too many dialog inputs");
            out.writeVarInt(inputs.size());
            for (Map.Entry<String, String> input : inputs.entrySet()) {
                writeBoundedString(out, input.getKey(), 64, "input key"); writeBoundedString(out, input.getValue(), 1024, "input value");
            }
        }
        @Override public void fromBytes(ByteBuf buf) {
            PacketBuffer in = new PacketBuffer(buf); id = in.readResourceLocation(); token = in.readUniqueId(); index = in.readVarInt();
            int count = in.readVarInt(); if (count < 0 || count > 16) throw new IllegalArgumentException("Invalid input count");
            inputs = new java.util.LinkedHashMap<>();
            for (int i = 0; i < count; i++) if (inputs.put(in.readString(64), in.readString(1024)) != null) throw new IllegalArgumentException("Duplicate input key");
        }
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
            clientHandler().open(message);
            return null;
        }
    }

    static void choose(ResourceLocation id, UUID token, int index, Map<String, String> inputs) { CHANNEL.sendToServer(new ChooseMessage(id, token, index, inputs)); }

    public static final class ChooseHandler implements IMessageHandler<ChooseMessage, IMessage> {
        @Override public IMessage onMessage(ChooseMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> choose(player, message));
            return null;
        }
    }


}
