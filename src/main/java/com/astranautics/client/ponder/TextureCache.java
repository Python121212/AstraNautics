package com.astranautics.client.ponder;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ponder 用のテクスチャキャッシュ。
 *
 * <p>ブロックのテクスチャ PNG（16x16）を直接読み込んでラスタライザに渡します。
 * リソースパックにも追従します。</p>
 */
public final class TextureCache {

    private static final Map<ResourceLocation, IsoCanvas.Sampler> CACHE = new ConcurrentHashMap<>();

    private TextureCache() {
    }

    /** テクスチャ ID からサンプラを取得（失敗時はグレー単色）。 */
    public static IsoCanvas.Sampler block(Minecraft minecraft, ResourceLocation textureId) {
        IsoCanvas.Sampler cached = CACHE.get(textureId);
        if (cached != null) {
            return cached;
        }
        IsoCanvas.Sampler sampler = load(minecraft, textureId);
        CACHE.put(textureId, sampler);
        return sampler;
    }

    private static IsoCanvas.Sampler load(Minecraft minecraft, ResourceLocation textureId) {
        if (minecraft.getResourceManager() == null) {
            return fallback();
        }
        ResourceLocation png = new ResourceLocation(textureId.getNamespace(),
                "textures/" + textureId.getPath() + ".png");
        try {
            Optional<Resource> resource = minecraft.getResourceManager().getResource(png);
            if (resource.isEmpty()) {
                return fallback();
            }
            try (InputStream stream = resource.get().open();
                 NativeImage image = NativeImage.read(stream)) {
                return IsoCanvas.fromImage(image);
            }
        } catch (IOException | RuntimeException exception) {
            return fallback();
        }
    }

    private static IsoCanvas.Sampler fallback() {
        return (u, v) -> 0xFF8A8A8A;
    }

    /** リロード時に呼ぶ。 */
    public static void invalidate() {
        CACHE.clear();
    }

    /** ブロック ID → テクスチャ ID（簡易：block/<path>）。 */
    public static ResourceLocation blockTexture(String namespace, String path) {
        return new ResourceLocation(namespace, "block/" + path);
    }
}
