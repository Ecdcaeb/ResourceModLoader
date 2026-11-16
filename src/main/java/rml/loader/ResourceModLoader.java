package rml.loader;

import org.apache.logging.log4j.Logger;
import rml.loader.api.annotations.PublicAPI;
import rml.loader.core.RMLFMLLoadingPlugin;
import rml.loader.runtime.LoaderRuntime;

/** Forge service entry point. The standalone runtime does not depend on this class. */
@PublicAPI
public final class ResourceModLoader {
    public static final int PACK_VERSION = 3;
    public static final String MODID = "rml";
    public static final String VERSION = "1.1.3";
    public static final Logger LOGGER = RMLFMLLoadingPlugin.LOGGER;
    private static final LoaderRuntime RUNTIME = new LoaderRuntime(LOGGER);

    private ResourceModLoader() {
    }

    public static LoaderRuntime runtime() {
        return RUNTIME;
    }
}
