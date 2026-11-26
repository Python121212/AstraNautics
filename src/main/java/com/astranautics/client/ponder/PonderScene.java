package com.astranautics.client.ponder;

import com.astranautics.client.ponder.step.AdhesiveSteps;
import com.astranautics.client.ponder.step.PhysicalizerSteps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Ponder（思考 UI）の 1 シーン。
 *
 * <p>Create の Ponder と同じ「アイテムにカーソルを当てて W 長押し」で開く
 * チュートリアルです。アニメーションは「キーフレーム」ではなく
 * <b>経過時間（tick）から直接計算</b>するため、巻き戻し・ループが軽いのが特徴です。</p>
 */
public final class PonderScene {

    /** 1 シーンの総時間（tick）。 */
    public final int durationTicks;
    public final Component title;
    public final List<Component> texts = new ArrayList<>();
    public final ResourceLocation icon;

    /** 描画処理（tick を受け取って等角キャンバスに描く）。 */
    public final BiConsumer<PonderContext, IsoCanvas> renderer;
    /** シーン開始時のアイテムスロット（物理化装置 or 接着剤）。 */
    public final PonderContext.ItemKind itemOnHotbar;

    /** このシーンの配色テーマ（ブロックごとに UI が変わる）。 */
    public final PonderTheme theme;

    public PonderScene(Component title, ResourceLocation icon, int durationTicks,
                       PonderContext.ItemKind itemOnHotbar,
                       BiConsumer<PonderContext, IsoCanvas> renderer) {
        this(title, icon, durationTicks, itemOnHotbar, renderer, PonderTheme.DEFAULT);
    }

    public PonderScene(Component title, ResourceLocation icon, int durationTicks,
                       PonderContext.ItemKind itemOnHotbar,
                       BiConsumer<PonderContext, IsoCanvas> renderer,
                       PonderTheme theme) {
        this.title = title;
        this.icon = icon;
        this.durationTicks = durationTicks;
        this.itemOnHotbar = itemOnHotbar;
        this.renderer = renderer;
        this.theme = theme == null ? PonderTheme.DEFAULT : theme;
    }

    /** テキストを追加（順番に表示される）。 */
    public PonderScene text(Component line) {
        this.texts.add(line);
        return this;
    }

    /** 現在の tick で表示すべきテキストのインデックス。 */
    public int textIndexAt(int tick) {
        if (this.texts.isEmpty()) {
            return -1;
        }
        // シーン後半ほど進む（等分割）
        int per = Math.max(24, this.durationTicks / (this.texts.size() + 1));
        int index = tick / per;
        return Math.min(this.texts.size() - 1, index);
    }

    /* ------------------------------------------------------------------ */
    /* シーン一覧                                                          */
    /* ------------------------------------------------------------------ */

    private static final List<PonderScene> SCENES = new ArrayList<>();
    private static boolean initialised;

    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        SCENES.clear();
        // 物理化装置：石ブロック → 装置 → ON → ポンと落ちる
        SCENES.add(PhysicalizerSteps.create());
        // 接着剤：3 ブロック → 右クリックで選択 → 左クリック確定 → 装置 ON → 3 つとも落ちる
        SCENES.add(AdhesiveSteps.create());
        SCENES.add(AdhesiveSteps.createCombineScene());
        // 各ブロック（スラスター・舵輪・エンジン部品 7 種）
        SCENES.addAll(PonderCatalog.blockScenes());
    }

    public static List<PonderScene> scenes() {
        init();
        return SCENES;
    }

    public static PonderScene byIndex(int index) {
        List<PonderScene> list = scenes();
        if (list.isEmpty()) {
            return null;
        }
        return list.get(Math.max(0, Math.min(list.size() - 1, index)));
    }

    public static int count() {
        return scenes().size();
    }

    /** カタログから引く（アイテム → 最初のシーン）。 */
    public static int indexOf(ResourceLocation itemId) {
        return PonderCatalog.firstIndex(itemId);
    }
}
