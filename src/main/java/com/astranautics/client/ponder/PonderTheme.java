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

    /** 気密外皮・組み立て台：布の生成り／空色。 */
    public static final PonderTheme AERO = new PonderTheme(
            0xFF8FDCF0, 0xFF1A2E38, 0xFF101E26,
            0xFF0D1A22, 0xFF1D3442, 0xFFEAF8FD, 0xFFB8EAF8, 0xFF7FD8F0);

    /** 熱気バーナー：炎の橙／気球の生成り。 */
    public static final PonderTheme AERO_HOT = new PonderTheme(
            0xFFFFA85A, 0xFF38220F, 0xFF20150A,
            0xFF241608, 0xFF452A0E, 0xFFFFF4E2, 0xFFFFC489, 0xFFFFB166);

    /** 蒸気ベント：蒸気の白／銅。 */
    public static final PonderTheme AERO_STEAM = new PonderTheme(
            0xFFE8E2D4, 0xFF2E2A24, 0xFF1C1A16,
            0xFF191712, 0xFF332F26, 0xFFFBF8F2, 0xFFE6DCC8, 0xFFD8CFB8);

    /** レビタイト：紫の結晶。 */
    public static final PonderTheme AERO_LEVITITE = new PonderTheme(
            0xFFC79AFF, 0xFF2A1B3C, 0xFF170F22,
            0xFF130C1C, 0xFF2B1D42, 0xFFF2E9FF, 0xFFD9C0FF, 0xFFBE93FF);

    /** 艦橋：宇宙の紺／シアン。 */
    public static final PonderTheme STARSHIP = new PonderTheme(
            0xFF56E2F6, 0xFF0E1C26, 0xFF08121A,
            0xFF050C12, 0xFF132430, 0xFFE6F8FD, 0xFFA8E8F8, 0xFF56E2F6);

    /** 動力炉：コアの水色。 */
    public static final PonderTheme STARSHIP_POWER = new PonderTheme(
            0xFF7FF4FF, 0xFF0B2028, 0xFF07141A,
            0xFF041016, 0xFF102A34, 0xFFE8FEFF, 0xFFA6F4FF, 0xFF7FF4FF);

    /** レーダー：緑のスキャン。 */
    public static final PonderTheme STARSHIP_SCAN = new PonderTheme(
            0xFF80F6C8, 0xFF0C231C, 0xFF061512,
            0xFF04110E, 0xFF0F2A22, 0xFFE9FFF6, 0xFFA8F6DC, 0xFF80F6C8);

    /** 砲塔：警戒の赤。 */
    public static final PonderTheme STARSHIP_WEAPON = new PonderTheme(
            0xFFFF6A50, 0xFF2A1110, 0xFF1A0A0A,
            0xFF120707, 0xFF301312, 0xFFFFEDE9, 0xFFFFB0A2, 0xFFFF8A72);

    /** 装甲：無骨な鋼。 */
    public static final PonderTheme STARSHIP_ARMOR = new PonderTheme(
            0xFFB0BAC6, 0xFF1C2026, 0xFF11151A,
            0xFF0D1116, 0xFF232A32, 0xFFF2F6FA, 0xFFCBD4DE, 0xFFB0BAC6);

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
