package rml.loader.runtime;

import net.minecraft.advancements.FunctionManager;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import rml.loader.api.event.FunctionLoadEvent;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.content.CompiledTagModule;
import rml.loader.content.ContentCatalog;
import rml.loader.content.ContentProviders;
import rml.loader.content.DetachedCompiledPack;
import rml.loader.content.DetachedCompiledPackContractTest;
import rml.runtime.ContentException;

import java.util.concurrent.atomic.AtomicReference;

/** Exercises the actual content runtime and tag provider, with no pretend Forge containers. */
public final class DetachedImportContractTest {
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        ModuleType.register(new ResourceLocation("rml", "functions"), "functions", false);
        ModuleType.register(ContentCatalog.MODULE, "content", false);
        Logger logger = LogManager.getLogger(DetachedImportContractTest.class);
        PackRegistry packs = new PackRegistry();
        ModuleLoader modules = new ModuleLoader(packs, logger);
        ContentCatalog catalog = new ContentCatalog(modules);
        ContentProviders providers = new ContentProviders();
        providers.registerStartup("item");
        CompiledTagModule tags = new CompiledTagModule();
        FunctionModule functions = new FunctionModule(packs, modules, catalog, providers, tags, null, logger);
        FunctionManager manager = new FunctionManager(null, null);
        FunctionLoadEvent load = new FunctionLoadEvent(manager);
        functions.onLoad(load);
        check(functions.getSessionId() != null && functions.getLastError() == null, "Native function session initialized");
        String session = functions.getSessionId();
        String packId = DetachedCompiledPackContractTest.PACK;
        ResourceLocation tag = new ResourceLocation("fixture:blocks");
        ResourceLocation stone = new ResourceLocation("minecraft:stone");
        DetachedCompiledPack pack = DetachedCompiledPack.read(DetachedCompiledPackContractTest.archive(packId, "tag/item", tag.toString(), "[\"minecraft:stone\"]"), packId);
        try {
            long revision = functions.getRevision();
            FunctionModule.CheckResult checked = functions.checkCompiled(pack);
            check(functions.getRevision() == revision && functions.snapshot().isEmpty(), "Detached checking does not activate content");
            check(packs.all().isEmpty(), "Import does not create a Forge mod container");
            AtomicReference<Throwable> rejected = new AtomicReference<>();
            Thread foreign = new Thread(() -> {
                try { functions.checkCompiled(pack); } catch (Throwable error) { rejected.set(error); }
            });
            foreign.start();
            foreign.join();
            check(rejected.get() instanceof IllegalStateException, "Import rejects foreign-thread preparation");
            rejects(() -> functions.apply(checked.getCandidateId(), revision + 1), "revision_conflict");
            functions.apply(checked.getCandidateId(), revision);
            check(tags.contains("item", tag, stone), "Apply activates the native tag provider");
            rejects(() -> functions.apply(checked.getCandidateId(), functions.getRevision()), "stale_candidate");
            functions.onLoad(load);
            check(session.equals(functions.getSessionId()) && functions.getLastError() == null, "Vanilla reload keeps the world session");
            check(tags.contains("item", tag, stone) && functions.snapshot().size() == 1, "Vanilla reload preserves the imported pack");
            DetachedCompiledPack startup = DetachedCompiledPack.read(DetachedCompiledPackContractTest.archive(packId, "item", "fixture:new_item", "{}"), packId);
            long before = functions.getRevision();
            rejects(() -> functions.checkCompiled(startup), "requires_restart");
            check(functions.getRevision() == before && tags.contains("item", tag, stone), "New native definitions do not mutate active content");
            rejects(() -> functions.deactivate("other-session", before, java.util.Collections.singleton(packId)), "stale_session");
            functions.deactivate(session, before, java.util.Collections.singleton(packId));
            functions.onLoad(load);
            check(functions.snapshot().isEmpty(), "Deactivation also removes the session import retained for reload");
        } finally {
            functions.close();
        }
        check(packs.all().isEmpty(), "The complete import lifecycle never registers a pretend mod");
        System.out.println("DetachedImportContractTest passed");
    }

    private static void rejects(Runnable operation, String code) {
        try { operation.run(); } catch (ContentException expected) {
            check(code.equals(expected.getCode()), "Expected " + code + ", received " + expected.getCode());
            return;
        }
        throw new AssertionError("Expected " + code);
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
