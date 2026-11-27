# AstraNautics

Minecraft **1.20.1 / Forge 47.x** 向けの MOD です。

- Mod ID: `astranautics`
- Version: `1.1.0`
- Minecraft: `1.20.1`（`[1.20.1,1.21)`）
- Forge: `47.x`（`[47,)`）
- Java: 17

## 収録ブロック

### グラビティ・アンカー（Gravity Anchor）

周囲のエンティティに重力を及ぼすブロックです。

- `enabled=true`（物理演算 ON）のとき、半径 `radius`（既定 12 ブロック）以内の
  エンティティをアンカー中心へ向かって加速させます（距離による線形減衰つき）。
- `enabled=false`（OFF）のときは何もせず、バニラの物理演算のみになります。
- 状態はブロックステート `enabled` で保持され、ON のときは発光（明るさ 10）します。
- 描画は通常のブロックモデル（BE はサーバー側の物理演算専用）。

### グラビティ・トグルスイッチ（Gravity Toggle Switch）

アンカーに**隣接**させて設置するスイッチです。

- **右クリック**で、隣接する（6 方向すべての）グラビティ・アンカーの
  物理演算をまとめて ON / OFF します。
- 隣接アンカーが無い場合はアクションバーにメッセージを表示します。
- スイッチ自体も `enabled` ブロックステートを持ち、ON でランプが緑・明るさ 7 になります。

### 物理演算の内容

`GravityAnchorBlockEntity#serverTick` が 2 tick に 1 回（10 回/秒）実行されます。

1. `enabled` が false なら何もしない
2. 半径内の（スペクテイター以外の）生存エンティティを取得
3. 中心からの距離で減衰する加速度を加算
4. 速度が `maxSpeed` を超えないようにクランプしてクライアントへ同期

### 設定（`config/astranautics-common.toml`）

| キー | 既定値 | 説明 |
|---|---|---|
| `gravity_anchor.radius` | `12.0` | 重力場の半径（ブロック） |
| `gravity_anchor.strength` | `0.08` | 1 ステップあたりの加速度 |
| `gravity_anchor.maxSpeed` | `1.2` | 加速後の最大速度（blocks/tick） |

## レシピ

**グラビティ・アンカー**（1 個）

```
鉄  ネザライト  鉄
レッドストーン エンダーアイ レッドストーン
鉄  黒曜石     鉄
```

**グラビティ・トグルスイッチ**（1 個）

```
レバー
レッドストーン
石
```

## ダウンロード

ビルド済み jar は Releases に置いてあります。

- jar を `.minecraft/mods/` に入れて Forge 1.20.1 で起動してください。
- 起動ログに `AstraNautics loaded (Minecraft 1.20.1 / Forge)` が出れば読み込み成功です。

## ソース構成

```
build.gradle                  ForgeGradle 6 ビルドスクリプト
gradle.properties             MC / Forge / Mod の各種バージョン
settings.gradle
src/main/java/com/astranautics/
    AstraNautics.java                 @Mod エントリポイント
    ModCreativeTabs.java              専用クリエイティブタブ
    block/ModBlocks.java              ブロック登録
    block/GravityAnchorBlock.java     グラビティ・アンカー
    block/GravitySwitchBlock.java     トグルスイッチ
    block/entity/ModBlockEntities.java
    block/entity/GravityAnchorBlockEntity.java   物理演算本体
    config/AstraConfig.java           共通コンフィグ
    item/ModItems.java                ブロックアイテム登録
src/main/resources/META-INF/mods.toml
src/main/resources/assets/astranautics/...   モデル / テクスチャ / 言語ファイル
src/main/resources/data/astranautics/...     ルートテーブル / レシピ
tools/gen_textures.py         ブロックテクスチャ(16x16 PNG)生成スクリプト
tools/build_jar.py            ForgeGradle 無しで最小 jar を作るフォールバック
```

## ビルド

```bash
./gradlew build
# => build/libs/astranautics-1.1.0.jar
```

開発用クライアント起動:

```bash
./gradlew runClient
```

テクスチャを作り直す場合:

```bash
python3 tools/gen_textures.py
```

### GitHub Actions

- `.github/workflows/build.yml` — push / PR ごとにコンパイルとリソース JSON 検証
- `.github/workflows/release.yml` — タグ push または手動実行で jar をビルドし Release に添付

## ライセンス

MIT
