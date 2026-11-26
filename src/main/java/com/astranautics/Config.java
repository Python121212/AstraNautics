package com.astranautics;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/** AstraNautics のコンフィグ定義。 */
public final class Config {

    public static final Common COMMON;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        SPEC = pair.getRight();
    }

    private Config() {
    }

    /** サーバー・クライアント共通コンフィグ。 */
    public static final class Common {

        public final ForgeConfigSpec.BooleanValue enableDebugLogging;

        /* --- 互換性（他 MOD のブロック対応） --- */
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> blockBlacklist;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> modBlacklist;
        public final ForgeConfigSpec.IntValue maxClusterSize;
        public final ForgeConfigSpec.BooleanValue enableBuoyancy;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("AstraNautics の共通設定").push("general");

            enableDebugLogging = builder
                    .comment("デバッグ用の詳細ログを出力するかどうか")
                    .define("enableDebugLogging", false);

            builder.pop();

            builder.comment("物理演算の互換性設定（他 MOD のブロック対応）").push("compatibility");

            blockBlacklist = builder
                    .comment("物理化させないブロック ID（例: \"create:mechanical_bearing\"）")
                    .defineListAllowEmpty("blockBlacklist", java.util.List.of(),
                            entry -> entry instanceof String);

            modBlacklist = builder
                    .comment("物理化させない MOD ID 単位の指定（例: \"create\"）")
                    .defineListAllowEmpty("modBlacklist", java.util.List.of(),
                            entry -> entry instanceof String);

            maxClusterSize = builder
                    .comment("1 つの剛体（クラスタ）として扱う最大セル数。大きいほど重い",
                            "既定 512。宇宙船を作るなら 1024〜2048 まで上げられます")
                    .defineInRange("maxClusterSize", 512, 16, 8192);

            enableBuoyancy = builder
                    .comment("排水量ベースの浮力を有効にする（石の桶でも浮く）")
                    .define("enableBuoyancy", true);

            builder.pop();
        }
    }
}
