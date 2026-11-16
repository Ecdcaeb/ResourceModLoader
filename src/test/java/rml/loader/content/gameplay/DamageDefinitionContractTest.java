package rml.loader.content.gameplay;

import com.google.gson.JsonParser;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.DamageSource;
import rml.deserializer.DeserializerManager;

/** Native damage behavior from standard data-pack definitions. */
public final class DamageDefinitionContractTest {
    private DamageDefinitionContractTest() { }

    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        DeserializerManager manager = new DeserializerManager("rml");
        DamageSource never = source(manager, "{\"scaling\":\"never\",\"exhaustion\":0.35,\"magic\":true,\"bypasses_armor\":true}");
        check(!never.isDifficultyScaled(), "never damage must bypass difficulty scaling");
        check(never.isMagicDamage() && never.isUnblockable(), "native damage flags must survive decoding");
        check(Float.compare(0.35F, never.getHungerDamage()) == 0, "native exhaustion must use the configured per-hit value");
        check(source(manager, "{\"scaling\":\"always\"}").isDifficultyScaled(), "always must scale even without an attacker");
        check(!source(manager, "{\"scaling\":\"when_caused_by_living_non_player\"}").isDifficultyScaled(), "environmental damage must not use conditional difficulty scaling");
        System.out.println("DamageDefinitionContractTest passed");
    }

    private static DamageSource source(DeserializerManager manager, String json) throws Exception {
        return GameplayModule.DamageDefinition.decode(manager, new JsonParser().parse(json).getAsJsonObject()).source("demo:arcane", null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
