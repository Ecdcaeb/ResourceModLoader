package rml.layer.compat.groovyscripts;

import net.minecraft.util.ResourceLocation;

import java.util.Arrays;
import java.util.Objects;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/8/21 12:52
 **/
public class NamedScript {
    private final ResourceLocation name;
    private final byte[] file;

    public NamedScript(ResourceLocation name, byte[] file){
        this.name = name;
        this.file = file;
    }

    public ResourceLocation getName() {
        return name;
    }

    public byte[] getFile() {
        return file;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NamedScript)) return false;
        NamedScript that = (NamedScript) o;
        return Objects.equals(name, that.name) && Arrays.equals(file, that.file);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name);
        result = 31 * result + Arrays.hashCode(file);
        return result;
    }
}
