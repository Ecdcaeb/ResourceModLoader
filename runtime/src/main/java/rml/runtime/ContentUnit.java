package rml.runtime;

import java.util.Collection;

/** A unit whose preparation is free of externally visible runtime effects. */
public interface ContentUnit {
    String getId();

    Collection<String> getDependencies();

    PreparedUnit prepare() throws Exception;
}
