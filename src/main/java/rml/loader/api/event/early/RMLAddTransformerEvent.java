package rml.loader.api.event.early;


import rml.loader.api.bus.DefaultEventBus;
import org.objectweb.asm.tree.ClassNode;

import java.util.function.ToIntFunction;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/4/13 10:46
 **/
public class RMLAddTransformerEvent {
    public static final DefaultEventBus<RMLAddTransformerEvent> BUS = DefaultEventBus.of();

    protected boolean cancel = false;

    public void cancel(boolean cancel) {
        this.cancel = cancel;
    }

    public boolean isCanceled() {
        return cancel;
    }

    public static class SingleTransformer extends RMLAddTransformerEvent {
        public static final DefaultEventBus<SingleTransformer> BUS = DefaultEventBus.of(RMLAddTransformerEvent.BUS);

        private final ToIntFunction<ClassNode> transformer;
        private final String target;
        public SingleTransformer(ToIntFunction<ClassNode> transformer, String target) {
            super();
            this.transformer = transformer;
            this.target = target;
        }

        public String getTarget() {
            return target;
        }

        public ToIntFunction<ClassNode> getTransformer() {
            return transformer;
        }
    }

    public static class GlobalTransformer extends RMLAddTransformerEvent {
        public static final DefaultEventBus<GlobalTransformer> BUS = DefaultEventBus.of(RMLAddTransformerEvent.BUS);

        private final rml.loader.core.GlobalTransformer transformer;
        public GlobalTransformer(rml.loader.core.GlobalTransformer transformer) {
            super();
            this.transformer = transformer;

        }

        public rml.loader.core.GlobalTransformer getTransformer() {
            return transformer;
        }
    }
}
