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
    private static boolean renderingVariant;

    private PresentationClient() { }

    static void configure(Map<ResourceLocation, PresentationCodecs.SurfaceDefinition> surfaces,
                          Map<ResourceLocation, PresentationCodecs.PaintingVariant> paintings,
                          Map<ResourceLocation, PresentationCodecs.TrimMaterial> trimMaterials,
                          Map<ResourceLocation, PresentationCodecs.TrimPattern> trimPatterns,
                          Map<ResourceLocation, ResourceLocation> defaultTextures) {
        SURFACES.clear(); SURFACES.putAll(surfaces);
        PAINTINGS.clear(); PAINTINGS.putAll(paintings);
        TRIM_MATERIALS.clear(); TRIM_MATERIALS.putAll(trimMaterials);
        TRIM_PATTERNS.clear(); TRIM_PATTERNS.putAll(trimPatterns);
        DEFAULT_TEXTURES.clear(); DEFAULT_TEXTURES.putAll(defaultTextures);
        TRIM_TEXTURES.clear();
        TRIM_LEGGINGS_TEXTURES.clear();
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
            ResourceLocation paletteId = material.paletteId;
            String paletteName = paletteId.getPath().substring(paletteId.getPath().lastIndexOf('/') + 1);
            ResourceLocation paletteTexture = new ResourceLocation(paletteId.getNamespace(), "textures/trims/color_palettes/" + paletteName + ".png");
            ResourceLocation patternTexture = new ResourceLocation(pattern.assetId.getNamespace(),
                    "textures/trims/models/armor/" + pattern.assetId.getPath() + (leggings ? "_leggings" : "") + ".png");
            BufferedImage palette = readTexture(paletteTexture), mask = readTexture(patternTexture);
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
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BufferedImage readTexture(ResourceLocation texture) throws IOException {
        String path = texture.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        try (IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation(texture.getNamespace(), path))) {
            return ImageIO.read(resource.getInputStream());
        }
    }

    static ResourceLocation entityTexture(EntityLivingBase entity) {
        VariantState variant = VARIANTS.get(entity.getEntityId());
        if (variant != null && variant.texture != null) return variant.texture;
        ResourceLocation type = EntityList.getKey(entity);
        return type == null ? null : DEFAULT_TEXTURES.get(type);
    }

    static void open(ResourceLocation id, String title, String body, String[] actions) {
        Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().displayGuiScreen(new Dialog(id, title, body, actions)));
    }
    static void installNetworkHandler() {
        PresentationNetwork.installClientHandler(new PresentationNetwork.ClientHandler() {
            @Override public void open(ResourceLocation id, String title, String body, String[] actions) {
                PresentationClient.open(id, title, body, actions);
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

    private static final class Dialog extends GuiScreen {
        private final ResourceLocation id;
        private final String title;
        private final String body;
        private final String[] actions;
        private int bodyScroll;
        private int actionScroll;
        private int visibleActions;

        Dialog(ResourceLocation id, String title, String body, String[] actions) {
            this.id = id; this.title = title; this.body = body; this.actions = actions;
        }

        @Override public void initGui() {
            buttonList.clear();
            int panelWidth = Math.min(320, Math.max(1, width - 16));
            visibleActions = Math.max(1, Math.min(actions.length, Math.max(1, (height - 150) / 22)));
            actionScroll = Math.max(0, Math.min(actionScroll, actions.length - visibleActions));
            int left = (width - panelWidth) / 2;
            int firstY = height / 2 + 50 - visibleActions * 11;
            for (int i = actionScroll; i < Math.min(actions.length, actionScroll + visibleActions); i++) {
                buttonList.add(new GuiButton(i, left + 8, firstY + (i - actionScroll) * 22, panelWidth - 16, 20, actions[i]));
            }
            if (actionScroll > 0) buttonList.add(new GuiButton(-2, left + 8, firstY - 21, 20, 18, "↑"));
            if (actionScroll + visibleActions < actions.length) buttonList.add(new GuiButton(-3, left + 8, firstY + visibleActions * 22, 20, 18, "↓"));
        }

        @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            int panelWidth = Math.min(320, Math.max(1, width - 16));
            int panelHeight = Math.min(height - 16, Math.max(140, Math.min(height - 16, fontRenderer.listFormattedStringToWidth(body, panelWidth - 24).size() * 10 + visibleActions * 22 + 84)));
            int left = (width - panelWidth) / 2, top = (height - panelHeight) / 2;
            drawRect(left, top, left + panelWidth, top + panelHeight, 0xFF282828);
            drawRect(left + 2, top + 2, left + panelWidth - 2, top + panelHeight - 2, 0xFF777777);
            drawRect(left + 4, top + 4, left + panelWidth - 4, top + panelHeight - 4, 0xFF3C3C3C);
            fontRenderer.drawString(title, left + 12, top + 12, 0xFFFFFF);
            List<String> lines = fontRenderer.listFormattedStringToWidth(body, panelWidth - 24);
            int bodyTop = top + 32;
            int bodyBottom = Math.min(top + panelHeight - visibleActions * 22 - 18, height / 2 + 34 - visibleActions * 11);
            int lineCount = Math.max(1, (bodyBottom - bodyTop) / 10);
            bodyScroll = Math.max(0, Math.min(bodyScroll, Math.max(0, lines.size() - lineCount)));
            for (int i = bodyScroll; i < lines.size() && i < bodyScroll + lineCount; i++)
                fontRenderer.drawString(lines.get(i), left + 12, bodyTop + (i - bodyScroll) * 10, 0xE0E0E0);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override protected void actionPerformed(GuiButton button) {
            if (button.id == -2) { actionScroll--; initGui(); return; }
            if (button.id == -3) { actionScroll++; initGui(); return; }
            PresentationNetwork.choose(id, button.id);
            mc.displayGuiScreen(null);
        }

        @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
            if (keyCode == 1) { PresentationNetwork.choose(id, -1); mc.displayGuiScreen(null); return; }
            if (keyCode == 200) bodyScroll = Math.max(0, bodyScroll - 1);
            else if (keyCode == 208) bodyScroll++;
            else if (keyCode == 201) { actionScroll = Math.max(0, actionScroll - visibleActions); initGui(); }
            else if (keyCode == 209) { actionScroll = Math.min(Math.max(0, actions.length - visibleActions), actionScroll + visibleActions); initGui(); }
            else if (keyCode == 28 || keyCode == 57) {
                for (GuiButton button : buttonList) if (button.id >= 0) { actionPerformed(button); break; }
            } else super.keyTyped(typedChar, keyCode);
        }

        @Override public void handleMouseInput() throws IOException {
            super.handleMouseInput();
            int wheel = org.lwjgl.input.Mouse.getEventDWheel();
            if (wheel != 0) bodyScroll = Math.max(0, bodyScroll + (wheel > 0 ? -2 : 2));
        }

        @Override public boolean doesGuiPauseGame() { return false; }
    }
}
