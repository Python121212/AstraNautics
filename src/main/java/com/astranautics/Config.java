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

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("AstraNautics の共通設定").push("general");

            enableDebugLogging = builder
                    .comment("デバッグ用の詳細ログを出力するかどうか")
                    .define("enableDebugLogging", false);

            builder.pop();
        }
    }
}
