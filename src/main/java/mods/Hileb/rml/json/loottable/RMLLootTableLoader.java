package mods.Hileb.rml.json.loottable;

import mods.Hileb.rml.api.event.LootTableRegistryEvent;
import mods.Hileb.rml.api.file.FileHelper;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.commons.io.FilenameUtils;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/15 12:52
 **/
public class RMLLootTableLoader {
    public static void load(LootTableRegistryEvent event){
        for(ModContainer modContainer: Loader.instance().getActiveModList()){
            Loader.instance().setActiveModContainer(modContainer);

            FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/loot_tables",null,
                    (root, file) -> {
                        String relative = root.relativize(file).toString();
                        if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                            return true;
                        String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                        ResourceLocation key = new ResourceLocation(modContainer.getModId(), name);
                        event.register(key);
                        return true;
                    },true, true);
            Loader.instance().setActiveModContainer(RMLFMLLoadingPlugin.Container.INSTANCE);
        }
    }
}
