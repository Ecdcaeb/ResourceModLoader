package rml.deserializer.value;

/** A level-dependent value shared by enchantments, providers and gameplay effects. */
@FunctionalInterface
public interface LevelBasedValue {
    float calculate(int level);
}
