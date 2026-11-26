package com.astranautics.physics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.astranautics.AstraNautics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * データパックで物性を上書きできる仕組み（全ブロック・他 MOD 対応の要）。
 *
 * <p>{@code data/<namespace>/astranautics/material_rules.json} を読み込みます。
 * 他 MOD のブロックも、その MOD のデータパックでこの JSON を置けば
 * 質量・摩擦・反発・浮力を指定できます。</p>
 *
 * <pre>
 * {
 *   "rules": [
 *     { "match": { "tag": "forge:storage_blocks" }, "mass": 2.4, "friction": 0.45 },
 *     { "match": { "id": "create:mechanical_bearing" }, "mass": 2.0, "restitution": 0.05 },
 *     { "match": { "namespace": "create" }, "mass": 1.8 },
 *     { "match": { "id": "minecraft:stone" }, "mass": 1.6, "friction": 0.8 }
 *   ]
 * }
 * </pre>
 *
 * <p>条件は {@code id}（完全一致）/ {@code tag} / {@code namespace} のいずれか。
 * 上から順に評価し、<b>最初に一致したルールを採用</b>します。</p>
 */
public final class MaterialRules extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    public static final String DIRECTORY = "astranautics";
    public static final String SUFFIX = ".json";

    /** 読み込まれたルール（上から順）。 */
    private static final List<Rule> RULES = new ArrayList<>();
    /** 計算済みキャッシュ。 */
    private static final Map<Block, MaterialProfile> CACHE = new ConcurrentHashMap<>();
    /** データパックが壊れていてもゲームを止めないため、エラーはここに記録。 */
    private static int loadedRuleCount;

    public MaterialRules() {
        super();
    }

    /** 1 本のルール。未指定の項目は「推定値を使用」を意味する null。 */
    private record Rule(
            ResourceLocation id,
            ResourceLocation tag,
            String namespace,
            Double mass,
            Double friction,
            Double restitution,
            Double drag,
            Boolean buoyant
    ) {
        boolean matches(Block block) {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
            if (key == null) {
                return false;
            }
            if (this.id != null && !this.id.equals(key)) {
                return false;
            }
            if (this.namespace != null && !this.namespace.equals(key.getNamespace())) {
                return false;
            }
            if (this.tag != null) {
                TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, this.tag);
                BlockState state = block.defaultBlockState();
                if (!state.is(tagKey)) {
                    return false;
                }
            }
            return true;
        }

        MaterialProfile apply(MaterialProfile base) {
            return new MaterialProfile(
                    this.mass == null ? base.mass() : this.mass,
                    this.friction == null ? base.friction() : this.friction,
                    this.restitution == null ? base.restitution() : this.restitution,
                    this.drag == null ? base.drag() : this.drag,
                    this.buoyant == null ? base.buoyant() : this.buoyant);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 参照                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * ブロックの物性を返す（ルール → 推定 → 既定値の順）。
     * 重い処理なので BlockEntity 側でキャッシュすること。
     */
    public static MaterialProfile profile(BlockState state) {
        if (state == null || state.isAir()) {
            return MaterialProfile.DEFAULT;
        }
        Block block = state.getBlock();
        MaterialProfile cached = CACHE.get(block);
        if (cached != null) {
            return cached;
        }
        MaterialProfile base = MaterialProfile.estimate(state);
        MaterialProfile result = base;
        for (Rule rule : RULES) {
            if (rule.matches(block)) {
                result = rule.apply(base);
                break;
            }
        }
        CACHE.put(block, result);
        return result;
    }

    /** 読み込まれたルール数（デバッグ用）。 */
    public static int loadedRuleCount() {
        return loadedRuleCount;
    }

    /** キャッシュ破棄（リロード時）。 */
    public static void invalidateCache() {
        CACHE.clear();
    }

    /* ------------------------------------------------------------------ */
    /* 読み込み                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> found = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> resources =
                manager.listResources(DIRECTORY, path -> path.getPath().endsWith("material_rules" + SUFFIX));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                found.put(entry.getKey(), JsonParser.parseReader(reader));
            } catch (Exception exception) {
                AstraNautics.LOGGER.warn("[AstraNautics] material_rules.json を読めませんでした: {}",
                        entry.getKey(), exception);
            }
        }
        return found;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                         ProfilerFiller profiler) {
        RULES.clear();
        invalidateCache();
        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            try {
                parseFile(entry.getValue());
            } catch (Exception exception) {
                AstraNautics.LOGGER.warn("[AstraNautics] material_rules の解析に失敗: {}",
                        entry.getKey(), exception);
            }
        }
        loadedRuleCount = RULES.size();
        AstraNautics.LOGGER.info("[AstraNautics] 物性ルールを {} 件読み込みました", loadedRuleCount);
    }

    private static void parseFile(JsonElement element) {
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject root = element.getAsJsonObject();
        JsonArray rules = root.has("rules") && root.get("rules").isJsonArray()
                ? root.getAsJsonArray("rules") : new JsonArray();
        for (JsonElement ruleElement : rules) {
            if (!ruleElement.isJsonObject()) {
                continue;
            }
            JsonObject json = ruleElement.getAsJsonObject();
            JsonObject match = json.has("match") && json.get("match").isJsonObject()
                    ? json.getAsJsonObject("match") : new JsonObject();

            RULES.add(new Rule(
                    match.has("id") ? parseId(match.get("id").getAsString()) : null,
                    match.has("tag") ? parseId(match.get("tag").getAsString()) : null,
                    match.has("namespace") ? match.get("namespace").getAsString() : null,
                    getDouble(json, "mass"),
                    getDouble(json, "friction"),
                    getDouble(json, "restitution"),
                    getDouble(json, "drag"),
                    json.has("buoyant") ? json.get("buoyant").getAsBoolean() : null));
        }
    }

    private static Double getDouble(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        try {
            return json.get(key).getAsDouble();
        } catch (Exception exception) {
            return null;
        }
    }

    private static ResourceLocation parseId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("不正な ID: " + value);
        }
        return id;
    }

    /* ------------------------------------------------------------------ */
    /* 一覧（コマンド表示用）                                               */
    /* ------------------------------------------------------------------ */

    /** 現在のルール一覧（デバッグ表示用の文字列）。 */
    public static Optional<String> describeFirstRules(int limit) {
        if (RULES.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder builder = new StringBuilder();
        int n = 0;
        for (Rule rule : RULES) {
            if (n++ >= limit) {
                break;
            }
            builder.append("\n  ")
                    .append(rule.id() != null ? "id=" + rule.id() : "")
                    .append(rule.tag() != null ? "tag=" + rule.tag() : "")
                    .append(rule.namespace() != null ? "ns=" + rule.namespace() : "")
                    .append(" m=").append(rule.mass())
                    .append(" f=").append(rule.friction());
        }
        return Optional.of(builder.toString());
    }

    /** 登録されているブロック数（対応確認用）。 */
    public static int supportedBlockCount() {
        int count = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!block.defaultBlockState().isAir()) {
                count++;
            }
        }
        return count;
    }

    /** 未使用警告を避けるためのヘルパー（登録 ID の逆引き）。 */
    public static ResourceLocation idOf(Block block) {
        return ForgeRegistries.BLOCKS.getKey(block);
    }
}
