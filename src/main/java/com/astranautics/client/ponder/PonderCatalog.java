package com.astranautics.client.ponder;

import com.astranautics.client.ponder.step.EnginePartSteps;
import com.astranautics.client.ponder.step.HelmSteps;
import com.astranautics.client.ponder.step.ThrusterSteps;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * アイテム／ブロック → Ponder シーンの対応表。
 *
 * <p>各ブロックに <b>専用の Ponder 画面</b>（配色テーマも個別）を割り当てます。
 * 新しいブロックを足すときは、ここに 1 行追加するだけで Ponder が生えます。</p>
 */
public final class PonderCatalog {

    /** アイテム ID → シーン。 */
    private static final Map<ResourceLocation, PonderScene> BY_ITEM = new LinkedHashMap<>();

    private PonderCatalog() {
    }

    /** アイテム ID からシーンを引く（無ければ null）。 */
    public static PonderScene forItem(ResourceLocation itemId) {
        init();
        return BY_ITEM.get(itemId);
    }

    /** ブロック用のシーン一覧（物理化装置・接着剤を除く）。 */
    public static List<PonderScene> blockScenes() {
        init();
        return List.copyOf(BY_ITEM.values());
    }

    /** そのアイテムが最初に現れるシーンのインデックス（PonderScene 内の並びに合わせる）。 */
    public static int firstIndex(ResourceLocation itemId) {
        init();
        int index = 2;   // 物理化装置(0) と 接着剤(1) の後ろから
        for (Map.Entry<ResourceLocation, PonderScene> entry : BY_ITEM.entrySet()) {
            if (entry.getKey().equals(itemId)) {
                return index;
            }
            index++;
        }
        return 0;
    }

    private static boolean initialised;

    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        BY_ITEM.clear();

        // --- スラスター ---
        register("thruster", ThrusterSteps.create());

        // --- 舵輪 ---
        register("ship_helm", HelmSteps.create());

        // --- エンジン部品 7 種 ---
        for (var part : com.astranautics.engine.EnginePart.values()) {
            register("engine_" + part.id, EnginePartSteps.create(part));
        }
    }

    private static void register(String path, PonderScene scene) {
        BY_ITEM.put(new ResourceLocation("astranautics", path), scene);
    }

    /** デバッグ用：登録数を返す。 */
    public static int size() {
        init();
        return BY_ITEM.size();
    }

    /** 全アイテムの ID（ツールチップのヒント表示用）。 */
    public static java.util.Set<ResourceLocation> items() {
        init();
        return BY_ITEM.keySet();
    }
}
