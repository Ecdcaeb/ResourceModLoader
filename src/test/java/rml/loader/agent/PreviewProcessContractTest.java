package rml.loader.agent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/** Verifies the wrapper keeps an early boot process tied to its parent's stdin pipe. */
public final class PreviewProcessContractTest {
    public static void main(String[] args) throws Exception {
        String java = Paths.get(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
        verifyPipe(java, BlockingMain.class);
        verifyPipe(java, ForgeBlockingMain.class);
        Process failed = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                PreviewProcessMain.class.getName(), FailingMain.class.getName()).redirectErrorStream(true).start();
        try {
            if (!failed.waitFor(15, TimeUnit.SECONDS)) throw new AssertionError("Preview target exception did not terminate the child");
            String output = new BufferedReader(new InputStreamReader(failed.getInputStream(), "UTF-8")).lines().collect(Collectors.joining("\n"));
            if (failed.exitValue() == 0 || !output.contains("expected-preview-target-failure"))
                throw new AssertionError("Preview wrapper concealed its target's failure: " + output);
        } finally {
            failed.getOutputStream().close();
            if (failed.isAlive()) failed.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        }
    }

    private static void verifyPipe(String java, Class<?> target) throws Exception {
        Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                PreviewProcessMain.class.getName(), target.getName()).redirectErrorStream(true).start();
        ExecutorService reader = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> started = reader.submit(() -> {
                BufferedReader output = new BufferedReader(new InputStreamReader(child.getInputStream(), "UTF-8"));
                String line;
                while ((line = output.readLine()) != null) if ("preview-target-ready".equals(line)) return true;
                return false;
            });
            if (!started.get(15, TimeUnit.SECONDS) || !child.isAlive()) throw new AssertionError("Preview target did not start with its parent pipe open");
            child.getOutputStream().close();
            if (!child.waitFor(5, TimeUnit.SECONDS)) throw new AssertionError("Preview child survived parent stdin EOF: " + target.getName());
            if (child.exitValue() != 0) throw new AssertionError("EOF guard returned an unexpected exit code: " + child.exitValue());
        } finally {
            child.getOutputStream().close();
            if (child.isAlive()) child.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            reader.shutdownNow();
        }
    }

    public static final class BlockingMain {
        public static void main(String[] args) throws InterruptedException {
            System.out.println("preview-target-ready");
            Thread.sleep(Long.MAX_VALUE);
        }
    }

    public static final class ForgeBlockingMain {
        public static void main(String[] args) throws InterruptedException {
            System.setSecurityManager(new net.minecraftforge.fml.relauncher.FMLSecurityManager());
            BlockingMain.main(args);
        }
    }

    public static final class FailingMain {
        public static void main(String[] args) { throw new IllegalStateException("expected-preview-target-failure"); }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
