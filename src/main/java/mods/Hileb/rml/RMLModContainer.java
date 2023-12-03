package mods.Hileb.rml;

import com.google.common.eventbus.EventBus;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.InjectedModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;

import java.io.File;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 10:19
 **/
public class RMLModContainer extends InjectedModContainer {
    public RMLModContainer(ModMetadata metadata,File source) {
        super(new DummyModContainer(metadata), source);
        ResourceModLoader.containers.add(this);
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        return true;
    }

    @Override
    public Class<?> getCustomResourcePackClass()
    {
        try
        {
            return getSource().isDirectory() ? Class.forName("net.minecraftforge.fml.client.FMLFolderResourcePack", true, getClass().getClassLoader()) : Class.forName("net.minecraftforge.fml.client.FMLFileResourcePack", true, getClass().getClassLoader());
        }
        catch (ClassNotFoundException e)
        {
            return null;
        }
    }
}
