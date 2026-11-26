package com.astranautics.client.ponder;

/**
 * Ponder 画面の配色テーマ。ブロックごとに UI の色を変えるための定義。
 *
 * <p>「そのブロックらしさ」が UI から伝わるように、外枠・ヘッダー・背景・
 * アクセント色をブロックごとに持たせます。</p>
 */
public record PonderTheme(
        int accent,       // 外枠・強調線
        int header,       // ヘッダー背景
        int panel,        // パネル背景
        int sceneTop,     // シーン背景（上）
        int sceneBottom,  // シーン背景（下）
        int text,         // 本文
        int title,        // 見出し
        int progress      // 進捗バー
) {

    /** 物理化装置：シアン／真鍮。 */
    public static final PonderTheme PHYSICALIZER = new PonderTheme(
            0xFF2AC8D2, 0xFF16303C, 0xFF0F1C25,
            0xFF0E1A22, 0xFF1B3340, 0xFFDFF8FA, 0xFF8FE7EE, 0xFF5FE0EA);

    /** 接着剤：接着剤の琥珀／緑。 */
    public static final PonderTheme ADHESIVE = new PonderTheme(
            0xFF5FD98A, 0xFF1A3324, 0xFF10201A,
            0xFF0E1F16, 0xFF1C3A2A, 0xFFE6FBEE, 0xFF9BF0B8, 0xFF6FE39A);

    /** スラスター：炎のオレンジ。 */
    public static final PonderTheme THRUSTER = new PonderTheme(
            0xFFFF9A3C, 0xFF3A2314, 0xFF20140C,
            0xFF221309, 0xFF43280F, 0xFFFFEEDC, 0xFFFFC07A, 0xFFFFB05A);

    /** 舵輪：木の茶／真鍮。 */
    public static final PonderTheme SHIP_HELM = new PonderTheme(
            0xFFD0A860, 0xFF332614, 0xFF1E170D,
            0xFF241B0F, 0xFF40301A, 0xFFFFF0D8, 0xFFE8C98E, 0xFFFFC46A);

    /** エンジン部品：金属の鋼色／冷却の青。 */
    public static final PonderTheme ENGINE = new PonderTheme(
            0xFF9AB6C8, 0xFF1B2730, 0xFF121A20,
            0xFF0F161C, 0xFF22323D, 0xFFEAF4FA, 0xFFB8D4E4, 0xFF7FD0E8);

    /** 燃焼室だけは熱の赤。 */
    public static final PonderTheme ENGINE_HOT = new PonderTheme(
            0xFFFF7A5A, 0xFF331A14, 0xFF1F0F0B,
            0xFF1F0E0A, 0xFF3D1A12, 0xFFFFEDE6, 0xFFFFAE94, 0xFFFF8A6A);

    /** デフォルト。 */
    public static final PonderTheme DEFAULT = PHYSICALIZER;
}
