package rml.runtime;

/**
 * Registers reversible resources in an inactive scope. Registered callbacks must
 * check {@link ResourceScope#isActive()} before performing work.
 */
@FunctionalInterface
public interface PreparedUnit {
    void activate(ResourceScope scope) throws Exception;
}
