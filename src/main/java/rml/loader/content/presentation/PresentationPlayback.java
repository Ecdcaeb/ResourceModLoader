package rml.loader.content.presentation;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Server-selected sound identity, duration and audible radius; clients never submit playback commands. */
final class PresentationPlayback {
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("rml_playback");
    private static boolean registered;
    private static java.util.function.Consumer<Play> client;
    static void install(java.util.function.Consumer<Play> handler) { client = handler; }
    static void register() {
        if (registered) return;
        CHANNEL.registerMessage(Handler.class, Play.class, 0, Side.CLIENT); registered = true;
    }
    static void play(World world, double x, double y, double z, PresentationCodecs.SoundPlayback value, int entity, boolean jukebox) {
        if (world.isRemote) return;
        CHANNEL.sendToAllAround(new Play(x, y, z, value, entity, jukebox),
                new NetworkRegistry.TargetPoint(world.provider.getDimension(), x, y, z, value.range));
    }
    public static final class Play implements IMessage {
        double x, y, z;
        ResourceLocation sound;
        float volume, pitch, range;
        int duration, entity;
        boolean jukebox;
        public Play() { }
        Play(double x, double y, double z, PresentationCodecs.SoundPlayback value, int entity, boolean jukebox) {
            this.x = x; this.y = y; this.z = z; this.sound = value.sound; this.volume = value.volume; this.pitch = value.pitch;
            this.range = value.range; this.duration = value.duration; this.entity = entity; this.jukebox = jukebox;
        }
        @Override public void toBytes(ByteBuf buf) {
            PacketBuffer out = new PacketBuffer(buf); out.writeDouble(x); out.writeDouble(y); out.writeDouble(z); out.writeResourceLocation(sound);
            out.writeFloat(volume); out.writeFloat(pitch); out.writeFloat(range); out.writeVarInt(duration); out.writeInt(entity); out.writeBoolean(jukebox);
        }
        @Override public void fromBytes(ByteBuf buf) {
            PacketBuffer in = new PacketBuffer(buf); x = in.readDouble(); y = in.readDouble(); z = in.readDouble(); sound = in.readResourceLocation();
            volume = in.readFloat(); pitch = in.readFloat(); range = in.readFloat(); duration = in.readVarInt(); entity = in.readInt(); jukebox = in.readBoolean();
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(volume) || volume < 0 || !Float.isFinite(pitch) || pitch <= 0
                    || !Float.isFinite(range) || range <= 0 || range > 1024 || duration < 1 || duration > 72000) throw new IllegalArgumentException("Invalid playback packet");
        }
    }
    public static final class Handler implements IMessageHandler<Play, IMessage> {
        @Override public IMessage onMessage(Play message, MessageContext context) {
            if (client == null) throw new IllegalStateException("Playback client is not installed");
            client.accept(message); return null;
        }
    }
}
