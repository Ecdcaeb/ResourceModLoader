package rml.loader.agent;

import com.google.gson.JsonArray;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.ITextComponent;

/** Records command replies without replacing the native player or console identity. */
public final class CommandCapture implements AutoCloseable {
    private static final ThreadLocal<CommandCapture> ACTIVE = new ThreadLocal<>();
    private final ICommandSender sender;
    private final CommandCapture previous;
    private final JsonArray messages = new JsonArray();
    private int characters;
    private boolean truncated;

    CommandCapture(ICommandSender sender) {
        this.sender = sender;
        previous = ACTIVE.get();
        ACTIVE.set(this);
    }

    /** Called by the native server/player sendMessage hooks. Normal message delivery is unchanged. */
    public static void message(ICommandSender sender, ITextComponent message) {
        CommandCapture capture = ACTIVE.get();
        if (capture == null || capture.sender != sender) return;
        String text = message.getUnformattedText();
        if (capture.messages.size() >= 128 || capture.characters + text.length() > 32768) {
            capture.truncated = true;
            return;
        }
        capture.characters += text.length();
        capture.messages.add(text);
    }

    JsonArray messages() { return messages; }
    boolean truncated() { return truncated; }

    @Override public void close() {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }
}
