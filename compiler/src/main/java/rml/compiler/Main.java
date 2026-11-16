package rml.compiler;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Standalone command-line frontend. Compilation never starts Minecraft. */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) System.exit(status);
    }

    public static int run(String[] args, PrintStream out, PrintStream error) {
        if (args.length == 1 && "capabilities".equals(args[0])) {
            out.println("RML language 1 -> Minecraft 1.12.2 / RML content format 1");
            out.println("functions: includes, constants, continuations, recursive tags, load/tick, scheduling, execute/data/return");
            out.println("resources: " + String.join(", ", RmlCompiler.getResourceTypes()));
            out.println("groups: recursive ordered registry tags; structures: compressed 1.12.2 NBT; assets: exact copy");
            out.println("Native definitions use shared typed decoders. Installed providers validate native identifiers and extension codecs.");
            return 0;
        }
        boolean check = args.length == 2 && "check".equals(args[0]);
        boolean compile = args.length == 3 && "compile".equals(args[0]);
        if (!check && !compile) {
            error.println("Usage: java -jar rml-compiler.jar compile <source-directory-or-zip> <output.zip>");
            error.println("       java -jar rml-compiler.jar check <source-directory-or-zip>");
            error.println("       java -jar rml-compiler.jar capabilities");
            return 2;
        }
        try {
            Path source = Paths.get(args[1]).toAbsolutePath().normalize();
            Path destination = compile ? Paths.get(args[2]).toAbsolutePath().normalize() : null;
            if (compile && (source.equals(destination) || Files.exists(destination) && Files.isSameFile(source, destination))) {
                throw new IllegalArgumentException("The output must not replace its source project");
            }
            CompiledPack result = RmlCompiler.compile(source);
            if (compile) result.write(destination);
            out.println((compile ? "Compiled" : "Checked") + " " + result.getResourceCount() + " resources, "
                    + result.getFunctionCount() + " functions" + (compile ? " -> " + destination : ""));
            return 0;
        } catch (CompileException e) {
            error.println(e.toString());
            return 1;
        } catch (Exception e) {
            error.println("Compilation failed: " + e.getMessage());
            return 1;
        }
    }
}
