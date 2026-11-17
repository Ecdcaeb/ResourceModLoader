package rml.loader.agent;

import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Pure-JDK supervisor: the parent pipe owns a separate game JVM, including after Forge installs its security manager. */
public final class PreviewProcessMain {
    private PreviewProcessMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) throw new IllegalArgumentException("Expected target main class");
        AtomicBoolean parentClosed = new AtomicBoolean();
        AtomicReference<Process> owned = new AtomicReference<>();
        Thread guard = new Thread(() -> {
            try {
                while (System.in.read() != -1) { }
            } catch (java.io.IOException ignored) { }
            parentClosed.set(true);
            Process game = owned.get();
            if (game != null) game.destroyForcibly();
        }, "RML preview parent lifetime guard");
        guard.setDaemon(true);
        guard.start();
        List<String> options = ManagementFactory.getRuntimeMXBean().getInputArguments();
        List<String> command = new ArrayList<>(options.size() + args.length + 5);
        command.add(Paths.get(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString());
        String heap = System.getProperty("rml.preview.maxHeap");
        for (String option : options) {
            if (option.startsWith("-Drml.preview.maxHeap=")) continue;
            if (heap != null && (option.startsWith("-Xmx") || option.startsWith("-Xms"))) continue;
            command.add(option);
        }
        if (heap != null) command.add("-Xmx" + heap);
        command.add("-cp"); command.add(System.getProperty("java.class.path"));
        command.addAll(Arrays.asList(args));
        if (parentClosed.get()) return;
        Process game = new ProcessBuilder(command).inheritIO().start();
        owned.set(game);
        if (parentClosed.get()) game.destroyForcibly();
        int result;
        try { result = game.waitFor(); }
        finally {
            if (game.isAlive()) { game.destroyForcibly(); game.waitFor(); }
        }
        if (!parentClosed.get() && result != 0) System.exit(result);
    }
}
