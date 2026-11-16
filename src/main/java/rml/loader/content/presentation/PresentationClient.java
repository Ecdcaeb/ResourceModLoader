package rml.loader.content.presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IResource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Client-only renderers, authored surface lookups, and the interactive dialog. */
@SideOnly(Side.CLIENT)
final class PresentationClient {
    private static final Map<Integer, VariantState> VARIANTS = new HashMap<>();
    private static final Map<Integer, VariantState> SOUND_VARIANTS = new HashMap<>();
    private static final Map<ResourceLocation, PresentationCodecs.SurfaceDefinition> SURFACES = new HashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> DEFAULT_TEXTURES = new HashMap<>();
    private static final Map<ResourceLocation, PresentationCodecs.PaintingVariant> PAINTINGS = new HashMap<>();
    private static final Map<ResourceLocation, PresentationCodecs.TrimMaterial> TRIM_MATERIALS = new HashMap<>();
    private static final Map<ResourceLocation, PresentationCodecs.TrimPattern> TRIM_PATTERNS = new HashMap<>();
    private static final Map<RenderLivingBase<?>, PresentationVariantRenderer> VARIANT_RENDERERS = new WeakHashMap<>();
    private static final Map<RenderLivingBase<?>, Boolean> TRIM_LAYERS = new WeakHashMap<>();
    private static final Map<ResourceLocation, Map<ResourceLocation, ResourceLocation>> TRIM_TEXTURES = new HashMap<>();
    private static final Map<ResourceLocation, Map<ResourceLocation, ResourceLocation>> TRIM_LEGGINGS_TEXTURES = new HashMap<>();
    private static final java.util.Set<ResourceLocation> SOUNDS = new java.util.HashSet<>();
    private static boolean renderingVariant;

    private PresentationClient() { }

    static void configure(Map<ResourceLocation, PresentationCodecs.SurfaceDefinition> surfaces,
                          Map<ResourceLocation, PresentationCodecs.PaintingVariant> paintings,
                          Map<ResourceLocation, PresentationCodecs.TrimMaterial> trimMaterials,
                          Map<ResourceLocation, PresentationCodecs.TrimPattern> trimPatterns,
                          Map<ResourceLocation, ResourceLocation> defaultTextures, java.util.Set<ResourceLocation> sounds) {
        SURFACES.clear(); SURFACES.putAll(surfaces);
        PAINTINGS.clear(); PAINTINGS.putAll(paintings);
        TRIM_MATERIALS.clear(); TRIM_MATERIALS.putAll(trimMaterials);
        TRIM_PATTERNS.clear(); TRIM_PATTERNS.putAll(trimPatterns);
        DEFAULT_TEXTURES.clear(); DEFAULT_TEXTURES.putAll(defaultTextures);
        TRIM_TEXTURES.clear();
        TRIM_LEGGINGS_TEXTURES.clear();
        SOUNDS.clear(); SOUNDS.addAll(sounds);
    }

    static void receiveVariant(int entityId, ResourceLocation variant, String type, ResourceLocation entity, ResourceLocation texture) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            VariantState state = new VariantState(variant, type, entity, texture);
            if (type.endsWith("_sound_variant")) SOUND_VARIANTS.put(entityId, state);
            else VARIANTS.put(entityId, state);
        });
    }
    static VariantState getVariant(int entityId) { return VARIANTS.get(entityId); }
    static VariantState getSoundVariant(int entityId) { return SOUND_VARIANTS.get(entityId); }
    static PresentationCodecs.PaintingVariant painting(ResourceLocation id) { return id == null ? null : PAINTINGS.get(id); }
    static PresentationCodecs.SurfaceDefinition surface(String type, ResourceLocation id) {
        return id == null ? null : SURFACES.get(PresentationIds.key(type, id));
    }
    static ResourceLocation surfaceTexture(String type, ResourceLocation id) {
        PresentationCodecs.SurfaceDefinition surface = surface(type, id);
        return surface == null ? null : surface.texture;
    }
    static void bindSurface(ResourceLocation texture) { Minecraft.getMinecraft().getTextureManager().bindTexture(texture); }

    static boolean trimDecal(ResourceLocation id) {
        PresentationCodecs.TrimPattern pattern = TRIM_PATTERNS.get(PresentationIds.key("trim_pattern", id));
        return pattern != null && pattern.decal;
    }

    @SubscribeEvent
    public static void validateAssets(net.minecraftforge.client.event.TextureStitchEvent.Pre event) {
        for (Map<ResourceLocation, ResourceLocation> textures : TRIM_TEXTURES.values()) for (ResourceLocation texture : textures.values())
            if (texture != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(texture);
        for (Map<ResourceLocation, ResourceLocation> textures : TRIM_LEGGINGS_TEXTURES.values()) for (ResourceLocation texture : textures.values())
            if (texture != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(texture);
        TRIM_TEXTURES.clear(); TRIM_LEGGINGS_TEXTURES.clear();
        try {
            for (PresentationCodecs.SurfaceDefinition surface : SURFACES.values()) readTexture(surface.texture);
            for (PresentationCodecs.PaintingVariant painting : PAINTINGS.values()) readTexture(painting.texture);
            for (ResourceLocation texture : DEFAULT_TEXTURES.values()) readTexture(texture);
            for (PresentationCodecs.TrimMaterial material : TRIM_MATERIALS.values()) readTexture(material.paletteTexture);
            for (PresentationCodecs.TrimPattern pattern : TRIM_PATTERNS.values()) { readTexture(pattern.texture); readTexture(pattern.leggingsTexture); }
        } catch (IOException ex) { throw new IllegalArgumentException("Missing or invalid presentation texture", ex); }
    }

    @SubscribeEvent
    public static void validateSounds(net.minecraftforge.client.event.sound.SoundLoadEvent event) {
        for (ResourceLocation sound : SOUNDS) if (Minecraft.getMinecraft().getSoundHandler().getAccessor(sound) == null)
            throw new IllegalArgumentException("Missing presentation sound resource " + sound);
    }

    @SubscribeEvent
    public static void trimTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        net.minecraft.nbt.NBTTagCompound tag = event.getItemStack().getTagCompound();
        if (tag == null || !tag.hasKey("rml_trim", 10)) return;
        net.minecraft.nbt.NBTTagCompound trim = tag.getCompoundTag("rml_trim");
        PresentationCodecs.TrimMaterial material = TRIM_MATERIALS.get(PresentationIds.key("trim_material", new ResourceLocation(trim.getString("material"))));
        PresentationCodecs.TrimPattern pattern = TRIM_PATTERNS.get(PresentationIds.key("trim_pattern", new ResourceLocation(trim.getString("pattern"))));
        if (pattern != null) event.getToolTip().add(trimDescription(pattern.description, pattern.assetId));
        if (material != null) event.getToolTip().add(trimDescription(material.description, material.paletteId));
    }

    private static String trimDescription(PresentationCodecs.TrimDescription description, ResourceLocation fallback) {
        net.minecraft.util.text.TextFormatting selected = net.minecraft.util.text.TextFormatting.WHITE;
        int distance = Integer.MAX_VALUE;
        for (net.minecraft.util.text.TextFormatting color : net.minecraft.util.text.TextFormatting.values()) if (color.isColor()) {
            String code = color.toString(); int rgb = Minecraft.getMinecraft().fontRenderer.getColorCode(code.charAt(1));
            int red = ((rgb >> 16) & 255) - ((description.color >> 16) & 255);
            int green = ((rgb >> 8) & 255) - ((description.color >> 8) & 255);
            int blue = (rgb & 255) - (description.color & 255);
            int candidate = red * red + green * green + blue * blue;
            if (candidate < distance) { selected = color; distance = candidate; }
        }
        return selected + (description.translate.isEmpty() ? fallback.toString() : net.minecraft.client.resources.I18n.format(description.translate));
    }

    static ResourceLocation trimTexture(ResourceLocation materialId, ResourceLocation patternId, boolean leggings) {
        Map<ResourceLocation, Map<ResourceLocation, ResourceLocation>> cache = leggings ? TRIM_LEGGINGS_TEXTURES : TRIM_TEXTURES;
        Map<ResourceLocation, ResourceLocation> byPattern = cache.get(materialId);
        if (byPattern != null && byPattern.containsKey(patternId)) return byPattern.get(patternId);
        PresentationCodecs.TrimMaterial material = TRIM_MATERIALS.get(PresentationIds.key("trim_material", materialId));
        PresentationCodecs.TrimPattern pattern = TRIM_PATTERNS.get(PresentationIds.key("trim_pattern", patternId));
        ResourceLocation result = material != null && pattern != null ? composeTrimTexture(materialId, material, patternId, pattern, leggings) : null;
        if (byPattern == null) { byPattern = new HashMap<>(); cache.put(materialId, byPattern); }
        byPattern.put(patternId, result);
        return result;
    }

    private static ResourceLocation composeTrimTexture(ResourceLocation materialId, PresentationCodecs.TrimMaterial material,
                                                        ResourceLocation patternId, PresentationCodecs.TrimPattern pattern, boolean leggings) {
        try {
            BufferedImage palette = readTexture(material.paletteTexture), mask = readTexture(leggings ? pattern.leggingsTexture : pattern.texture);
            int width = mask.getWidth(), height = mask.getHeight();
            DynamicTexture combined = new DynamicTexture(width, height);
            int[] pixels = combined.getTextureData();
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int maskPixel = mask.getRGB(x, y);
                int index = (((maskPixel >> 16) & 255) * (palette.getWidth() - 1) + 127) / 255;
                int swatch = palette.getRGB(index, Math.min(palette.getHeight() - 1, palette.getHeight() / 2));
                int alpha = ((maskPixel >>> 24) * (swatch >>> 24)) / 255;
                pixels[y * width + x] = (alpha << 24) | (swatch & 0xFFFFFF);
            }
            combined.updateDynamicTexture();
            String name = "rml_trim_" + (leggings ? "leggings_" : "") + materialId.toString().replace(':', '_').replace('/', '_')
                    + "_" + patternId.toString().replace(':', '_').replace('/', '_');
            return Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation(name, combined);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Missing trim texture for " + materialId + " / " + patternId, ex);
        }
    }

    private static BufferedImage readTexture(ResourceLocation texture) throws IOException {
        String path = texture.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        try (IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation(texture.getNamespace(), path))) {
            BufferedImage image = ImageIO.read(resource.getInputStream());
            if (image == null) throw new IOException("Invalid PNG " + texture);
            return image;
        }
    }

    static ResourceLocation entityTexture(EntityLivingBase entity) {
        VariantState variant = VARIANTS.get(entity.getEntityId());
        return variant == null ? null : variant.texture;
    }

    static void installNetworkHandler() {
        PresentationPlayback.install(message -> Minecraft.getMinecraft().addScheduledTask(() ->
                Minecraft.getMinecraft().getSoundHandler().playSound(new PresentationPlayingSound(message))));
        PresentationNetwork.installClientHandler(new PresentationNetwork.ClientHandler() {
            @Override public void open(PresentationNetwork.OpenMessage message) {
                Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().displayGuiScreen(new PresentationDialogScreen(message)));
            }
            @Override public void variant(int entityId, ResourceLocation id, String type, ResourceLocation entity, ResourceLocation texture) {
                PresentationClient.receiveVariant(entityId, id, type, entity, texture);
            }
        });
    }

    static void registerModels() {
        MinecraftForge.EVENT_BUS.register(PresentationClient.class);
        RenderingRegistry.registerEntityRenderingHandler(EntityPresentationFrog.class,
                manager -> new PresentationAquaticRenderer<>(manager, false));
        RenderingRegistry.registerEntityRenderingHandler(EntityPresentationZombieNautilus.class,
                manager -> new PresentationAquaticRenderer<>(manager, true));
        RenderingRegistry.registerEntityRenderingHandler(EntityPresentationPainting.class, PresentationPaintingRenderer::new);
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityPresentationPot.class, new PresentationPotRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBanner.class, new PresentationBannerRenderer());
    }

    @SubscribeEvent
    public static void registerRecordModels(ModelRegistryEvent event) {
        net.minecraft.block.Block jukebox = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("rml", "presentation_jukebox"));
        if (jukebox != null) ModelLoader.setCustomStateMapper(jukebox, new net.minecraft.client.renderer.block.statemap.StateMapperBase() {
            @Override protected ModelResourceLocation getModelResourceLocation(net.minecraft.block.state.IBlockState state) {
                return new ModelResourceLocation("minecraft:jukebox", "normal");
            }
        });
        net.minecraft.block.Block pot = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("rml", "decorated_pot"));
        if (pot != null) ModelLoader.setCustomStateMapper(pot, block -> java.util.Collections.emptyMap());
        for (net.minecraft.item.Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
            ResourceLocation id = item.getRegistryName();
            if (id == null || !"rml".equals(id.getNamespace())) continue;
            if (id.getPath().startsWith("record/") || id.getPath().startsWith("instrument/") || id.getPath().startsWith("banner_pattern/"))
                ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(id, "inventory"));
            else if ("decorated_pot".equals(id.getPath()))
                ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(new ResourceLocation("minecraft", "flower_pot"), "inventory"));
        }
    }

    @SubscribeEvent
    public static void renderVariant(RenderLivingEvent.Pre<?> event) {
        RenderLivingBase renderer = event.getRenderer();
        attachTrimLayer(renderer);
        if (renderingVariant) return;
        EntityLivingBase entity = event.getEntity();
        VariantState state = VARIANTS.get(entity.getEntityId());
        if (state == null || state.texture == null) return;
        PresentationVariantRenderer variantRenderer = VARIANT_RENDERERS.get(renderer);
        if (variantRenderer == null) {
            ModelBase model = renderer.getMainModel();
            variantRenderer = new PresentationVariantRenderer(Minecraft.getMinecraft().getRenderManager(), model);
            VARIANT_RENDERERS.put(renderer, variantRenderer);
        }
        event.setCanceled(true);
        renderingVariant = true;
        try {
            variantRenderer.doRender(entity, event.getX(), event.getY(), event.getZ(), entity.rotationYaw, event.getPartialRenderTick());
        } finally {
            renderingVariant = false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void attachTrimLayer(RenderLivingBase renderer) {
        if (TRIM_LAYERS.put(renderer, Boolean.TRUE) == null) renderer.addLayer(new PresentationTrimLayer());
    }

    @SubscribeEvent
    public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        VARIANTS.clear();
        SOUND_VARIANTS.clear();
        VARIANT_RENDERERS.clear();
        TRIM_LAYERS.clear();
    }

    static final class VariantState {
        final ResourceLocation id, entity, texture;
        final String type;
        VariantState(ResourceLocation id, String type, ResourceLocation entity, ResourceLocation texture) {
            this.id = id; this.type = type; this.entity = entity; this.texture = texture;
        }
    }

}
