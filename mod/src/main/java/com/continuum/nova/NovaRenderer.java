package com.continuum.nova;

import com.continuum.nova.chunks.IGeometryFilter;
import com.continuum.nova.gui.NovaDraw;
import com.continuum.nova.interfaces.INovaDynamicTexture;
import com.continuum.nova.interfaces.INovaEntityRenderer;
import com.continuum.nova.interfaces.INovaTextureAtlasSprite;
import com.continuum.nova.interfaces.INovaTextureMap;
import com.continuum.nova.system.MinecraftAtlasTexture;
import com.continuum.nova.system.MinecraftTextureAtlasLocation;
import com.continuum.nova.system.NovaNative;
import com.continuum.nova.system.WindowSize;
import com.continuum.nova.texture.INovaTextureManager;
import com.continuum.nova.utils.Profiler;
import com.continuum.nova.utils.Utils;
import com.sun.jna.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.LoaderExceptionModCrash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.rmi.runtime.Log;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;

import static com.continuum.nova.NovaConstants.*;
import static com.continuum.nova.utils.Utils.getImageData;

public class NovaRenderer implements IResourceManagerReloadListener {

    private Logger logger;

    private boolean firstLoad = true;

    private static final List<ResourceLocation> GUI_COLOR_TEXTURES_LOCATIONS = new ArrayList<>();
    private TextureMap guiAtlas = new TextureMap("textures");
    private INovaTextureMap novaGuiAtlas = (INovaTextureMap) guiAtlas;
    private Map<ResourceLocation, TextureAtlasSprite> guiSpriteLocations = new HashMap<>();

    private static final List<ResourceLocation> BLOCK_COLOR_TEXTURES_LOCATIONS = new ArrayList<>();

    private static final List<ResourceLocation> FONT_COLOR_TEXTURES_LOCATIONS = new ArrayList<>();
    private TextureMap fontAtlas = new TextureMap("textures");
    private Map<ResourceLocation, TextureAtlasSprite> fontSpriteLocations = new HashMap<>();

    private static final List<ResourceLocation> FREE_TEXTURES = new ArrayList<>();

    private int height;
    private int width;

    private boolean resized;
    private int scalefactor;

    private IResourceManager resourceManager;

    private HashMap<String, IGeometryFilter> filterMap;

    private static NovaRenderer instance;

    public static INovaTextureManager getTextureManager() {
        return (INovaTextureManager) Minecraft.getMinecraft().getTextureManager();
    }

    public static NovaRenderer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Tried to access NovaRenderer before it was created");
        }
        return instance;
    }

    public static void create(Logger logger) {
        if (instance != null) {
            throw new IllegalStateException("Instance already created");
        }
        instance = new NovaRenderer(logger);
    }

    private NovaRenderer(Logger loggerger) {
        this.logger = loggerger;
        // I put these in Utils to make this class smaller
        Utils.initBlockTextureLocations(BLOCK_COLOR_TEXTURES_LOCATIONS);
        Utils.initGuiTextureLocations(GUI_COLOR_TEXTURES_LOCATIONS);
        Utils.initFontTextureLocations(FONT_COLOR_TEXTURES_LOCATIONS);
        Utils.initFreeTextures(FREE_TEXTURES);
    }

    public HashMap<String, IGeometryFilter> getFilterMap(){
      return this.filterMap;
    }

    @Override
    public void onResourceManagerReload(@Nonnull IResourceManager resourceManager) {
        this.resourceManager = resourceManager;

        if (firstLoad) {
            firstLoad = false;
        }

        NovaNative.resetTextureManager();

        addGuiAtlas(resourceManager);
        addFontAtlas(resourceManager);
        addFreeTextures(resourceManager);
    }

    /**
     * Adds the textures that just hang out without a texture atlas
     *
     * @param resourceManager The IResoruceManager to get the textures from
     */
    private void addFreeTextures(IResourceManager resourceManager) {
        for (ResourceLocation loc : FREE_TEXTURES) {
            try {
                IResource texture = resourceManager.getResource(loc);
                BufferedInputStream in = new BufferedInputStream(texture.getInputStream());
                BufferedImage image = ImageIO.read(in);
                if (image != null) {
                    loadTexture(loc, image);
                } else {
                    logger.error("Free texture " + loc + " has no data!");
                }
            } catch (IOException e) {
                logger.error("Could not load free texture " + loc, e);
            }
        }
    }

    private void addGuiAtlas(@Nonnull IResourceManager resourceManager) {
        novaGuiAtlas.createWhiteTexture(WHITE_TEXTURE_GUI_LOCATION);
        addAtlas(resourceManager, guiAtlas, GUI_COLOR_TEXTURES_LOCATIONS, guiSpriteLocations, GUI_ATLAS_NAME);
        logger.debug("Created GUI atlas");
    }

    private void addFontAtlas(@Nonnull IResourceManager resourceManager) {
        addAtlas(resourceManager, fontAtlas, FONT_COLOR_TEXTURES_LOCATIONS, fontSpriteLocations, FONT_ATLAS_NAME);
        logger.debug("Created font atlas");
    }

    public void addTerrainAtlas(@Nonnull TextureMap blockColorMap) {
        // Copy over the atlas
        MinecraftAtlasTexture blockColorTexture = getFullImage(((INovaTextureMap) blockColorMap).getWidth(), ((INovaTextureMap) blockColorMap).getHeight(), ((INovaTextureMap) blockColorMap).getMapUploadedSprites().values());
        blockColorTexture.name = BLOCK_COLOR_ATLAS_NAME;
        NovaNative.addTexture(blockColorTexture);

        // Copy over all the icon locations
        for (String spriteName : ((INovaTextureMap) blockColorMap).getMapUploadedSprites().keySet()) {
            TextureAtlasSprite sprite = blockColorMap.getAtlasSprite(spriteName);
            MinecraftTextureAtlasLocation location = new MinecraftTextureAtlasLocation(
                    sprite.getIconName(),
                    sprite.getMinU(),
                    sprite.getMinV(),
                    sprite.getMaxU(),
                    sprite.getMaxV()
            );

            NovaNative.addTextureLocation(location);
        }
    }

    private void addAtlas(@Nonnull IResourceManager resourceManager, TextureMap atlas, List<ResourceLocation> resources,
                          Map<ResourceLocation, TextureAtlasSprite> spriteLocations, String textureName) {
        atlas.loadSprites(resourceManager, textureMapIn -> resources.forEach(location -> {
            TextureAtlasSprite textureAtlasSprite = textureMapIn.registerSprite(location);
            spriteLocations.put(location, textureAtlasSprite);
        }));

        Optional<TextureAtlasSprite> whiteImage = ((INovaTextureMap) atlas).getWhiteImage();
        whiteImage.ifPresent(image -> spriteLocations.put(((INovaTextureAtlasSprite) image).getLocation(), image));

        MinecraftAtlasTexture atlasTexture = getFullImage(((INovaTextureMap) atlas).getWidth(), ((INovaTextureMap) atlas).getHeight(), spriteLocations.values());
        atlasTexture.setName(textureName);

        logger.info("Adding atlas texture {}", atlasTexture);
        NovaNative.addTexture(atlasTexture);

        for (TextureAtlasSprite sprite : spriteLocations.values()) {
            MinecraftTextureAtlasLocation location = new MinecraftTextureAtlasLocation(
                    sprite.getIconName(),
                    sprite.getMinU(),
                    sprite.getMinV(),
                    sprite.getMaxU(),
                    sprite.getMaxV()
            );

            NovaNative.addTextureLocation(location);
        }
    }

    private MinecraftAtlasTexture getFullImage(int atlasWidth, int atlasHeight, Collection<TextureAtlasSprite> sprites) {
        byte[] imageData = new byte[atlasWidth * atlasHeight * 4];

        for (TextureAtlasSprite sprite : sprites) {
            int startY = sprite.getOriginY() * atlasWidth * 4;
            int startPos = sprite.getOriginX() * 4 + startY;

            if (sprite.getFrameCount() > 0) {
                int[] data = sprite.getFrameTextureData(0)[0];
                for (int y = 0; y < sprite.getIconHeight(); y++) {
                    for (int x = 0; x < sprite.getIconWidth(); x++) {
                        // Reverse the order of the color channels
                        int pixel = data[y * sprite.getIconWidth() + x];

                        byte red = (byte) (pixel & 0xFF);
                        byte green = (byte) ((pixel >> 8) & 0xFF);
                        byte blue = (byte) ((pixel >> 16) & 0xFF);
                        byte alpha = (byte) ((pixel >> 24) & 0xFF);

                        int imageDataBasePos = startPos + x * 4 + y * atlasWidth * 4;
                        imageData[imageDataBasePos] = blue;
                        imageData[imageDataBasePos + 1] = green;
                        imageData[imageDataBasePos + 2] = red;
                        imageData[imageDataBasePos + 3] = alpha;
                    }
                }
            }
        }

        return new MinecraftAtlasTexture(
                atlasWidth,
                atlasHeight,
                4,
                imageData
        );
    }

    public void preInit() {
        System.getProperties().setProperty("jna.dump_memory", "false");
        String pid = ManagementFactory.getRuntimeMXBean().getName();
        logger.info("PID: " + pid + " TID: " + Thread.currentThread().getId());
        try {
            Utils.copyDefaults();
        } catch (IOException e) {
            throw new LoaderExceptionModCrash("Nova failed to copy defaults", e);
        }
        try {
            installNative();
        } catch (IOException e) {
            throw new LoaderExceptionModCrash("Nova renderer failed to load native library", e);
        }
        NovaNative.initialize(logger);
        logger.info("Native code initialized");
        updateWindowSize();
    }

    private void installNative() throws IOException {
        if((Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment")) {
            logger.info("Nova is very likely running in a development environment, trying to load native from run directory...");
            try {
                if (Platform.isWindows()) {
                } else {
                }
                logger.info("Succeeded in loading nova from run directory.");
                return;
            } catch (Throwable e) {
                logger.warn("Failed to load nova from run directory", e);
            }
        }

        File nativeExtractDir = new File("mods/nova-natives");
        if(!nativeExtractDir.exists() || !nativeExtractDir.isDirectory()) {
            if(!nativeExtractDir.mkdirs()) {
                throw new IOException("Failed to create directory " + nativeExtractDir.getAbsolutePath());
            }
        }

        if(Platform.isWindows()) {
            if(NovaRenderer.class.getResource("/nova-renderer.dll") == null) {
                throw new IllegalStateException("Windows is not supported by the current nova build");
            }
            Utils.extractResource("/nova-renderer.dll", nativeExtractDir.toPath(), true);
            Utils.extractResource("/libwinpthread-1.dll", nativeExtractDir.toPath(), true);
            Utils.extractResource("/libwinpthread-license.txt", nativeExtractDir.toPath(), true); // not sure if this is needed, but its only a few kb so
        } else {
            if(NovaRenderer.class.getResource("/libnova-renderer.so") == null) {
                throw new IllegalStateException("Linux is not supported by the current nova build");
            }
            Utils.extractResource("/libnova-renderer.so", nativeExtractDir.toPath(), true);
        }
        nativeExtractDir.deleteOnExit();

        System.setProperty("java.library.path", System.getProperty("java.library.path") + File.pathSeparator + nativeExtractDir.getAbsolutePath()); // Make sure java can load dependencies
        System.setProperty("jna.library.path", System.getProperty("java.library.path"));

    }

    private void updateWindowSize() {
        WindowSize size = NovaNative.getWindowSize();
        int oldHeight = height;
        int oldWidth = width;
        resized = oldHeight != size.height || oldWidth != size.width;
        height = size.height;
        width = size.width;

    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public boolean wasResized() {
        return resized;
    }

    public void updateCameraAndRender(float renderPartialTicks, Minecraft mc) {
        if (NovaNative.shouldClose()) {
            mc.shutdown();
        }

        EntityRenderer entityRenderer = Minecraft.getMinecraft().entityRenderer;

        boolean shouldUpdateLightmap = ((INovaEntityRenderer) entityRenderer).isLightmapUpdateNeeded();
        ((INovaEntityRenderer) entityRenderer).updateLightmapNOVA(renderPartialTicks);
        if (shouldUpdateLightmap) {
            sendLightmapTexture(((INovaEntityRenderer) entityRenderer).getLightmapTexture());
        }


        Profiler.start("render_gui");
        if (mc.currentScreen != null) {

            NovaDraw.novaDrawScreen(mc.currentScreen, renderPartialTicks, filterMap);

        }
        Profiler.end("render_gui");

        Profiler.start("update_player");
        EntityPlayerSP viewEntity = mc.player;
        if (viewEntity != null) {
            float pitch = viewEntity.rotationPitch;
            float yaw = viewEntity.rotationYaw;
            double x = viewEntity.posX;
            double y = viewEntity.posY + viewEntity.getEyeHeight();
            double z = viewEntity.posZ;
            NovaNative.setPlayerCameraTransform(x, y, z, yaw, pitch);
        }
        Profiler.end("update_player");

        Profiler.start("executeFrame");
        NovaNative.executeFrame();
        Profiler.end("executeFrame");

        Profiler.start("update_window");
        updateWindowSize();
        Profiler.end("update_window");
        int scalefactor = new ScaledResolution(mc).getScaleFactor() * 2;
        if (scalefactor != this.scalefactor) {
            NovaNative.setFloatSetting("scalefactor", scalefactor);
            this.scalefactor = scalefactor;
        }

        printProfilerData();
    }

    private void sendLightmapTexture(DynamicTexture lightmapTexture) {
        int[] data = lightmapTexture.getTextureData();
        NovaNative.sendLightmapTexture(data, data.length, ((INovaDynamicTexture) lightmapTexture).getWidth(), ((INovaDynamicTexture) lightmapTexture).getHeight());
    }

    private void printProfilerData() {
        Profiler.logData();
    }

    /**
     * Loads the specified texture, adding it to Minecraft as a texture outside of an atlas
     *
     * @param location The location of the texture
     * @param image    The texture itself
     */
    public void loadTexture(ResourceLocation location, BufferedImage image) {
        if (resourceManager == null) {
            logger.error("Trying to load texture " + location + " but there's no resource manager");
            return;
        }

        byte[] imageData = getImageData(image);

        MinecraftAtlasTexture tex = new MinecraftAtlasTexture(image.getWidth(), image.getHeight(), 4, imageData);
        tex.setName(location.toString());
        NovaNative.addTexture(tex);

        MinecraftTextureAtlasLocation loc = new MinecraftTextureAtlasLocation(location.toString(), 0, 0, 1, 1);
        NovaNative.addTextureLocation(loc);
    }

    public static String atlasTextureOfSprite(ResourceLocation texture) {
        ResourceLocation strippedLocation = new ResourceLocation(texture.getResourceDomain(), texture.getResourcePath().replace(".png", "").replace("textures/", ""));

        if (BLOCK_COLOR_TEXTURES_LOCATIONS.contains(strippedLocation)) {
            return BLOCK_COLOR_ATLAS_NAME;
        } else if (GUI_COLOR_TEXTURES_LOCATIONS.contains(strippedLocation) || texture == WHITE_TEXTURE_GUI_LOCATION) {
            return GUI_ATLAS_NAME;
        } else if (FONT_COLOR_TEXTURES_LOCATIONS.contains(strippedLocation)) {
            return FONT_ATLAS_NAME;
        }

        return texture.toString();
    }

    public void loadShaderpack(String shaderpackName, BlockColors blockColors) {
        Profiler.start("load_shaderpack");
        NovaNative.setStringSetting("loadedShaderpack", shaderpackName);

        String filters = NovaNative.getMaterialsAndFilters();
        String[] filtersSplit = filters.split("\n");
        Profiler.end("load_shaderpack");

        logger.info("Received filters `{}`", filters);

        if (filtersSplit.length < 2 || filtersSplit.length % 2 != 0) {
            throw new IllegalStateException("Must have a POT number of filters and shader names");
        }

        Profiler.start("build_filters");
        logger.debug("Filters: '{}'", String.join(", ", filtersSplit));
        logger.debug("Received {} shaders with filters", filtersSplit.length);

        filterMap = new HashMap<>();
        for (int i = 0; i < filtersSplit.length; i += 2) {
            String filterName = filtersSplit[i];
            IGeometryFilter filter = IGeometryFilter.parseFilterString(filtersSplit[i + 1]);
            filterMap.put(filterName, filter);
        }
        Profiler.end("build_filters");
    }

    public Logger getLog() {
        return logger;
    }
}


