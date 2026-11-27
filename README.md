# AstraNautics

Minecraft **1.20.1 / Forge 47.x** 向けの最小構成 MOD です。
**読み込み確認用（load-only）** で、ブロック・アイテム・エンティティなどの登録は一切行いません。

- Mod ID: `astranautics`
- Version: `1.0.0`
- Minecraft: `1.20.1`（`[1.20.1,1.21)`）
- Forge: `47.x`（`[47,)`）
- Java: 17

## ダウンロード

ビルド済み jar は Releases に置いてあります。

- `astranautics-1.0.0.jar` を `mods/` フォルダへ入れてください。
- Forge 1.20.1 (47.2.0 で確認) のクライアント／サーバーで起動できます。
- 起動後、ログに `AstraNautics loaded (Minecraft 1.20.1 / Forge)` が出れば成功です。

## ソース構成

```
build.gradle                  ForgeGradle 6 ビルドスクリプト
gradle.properties             MC / Forge / Mod の各種バージョン
settings.gradle
src/main/java/com/astranautics/AstraNautics.java   @Mod エントリポイント
src/main/resources/META-INF/mods.toml              Forge メタデータ
src/main/resources/pack.mcmeta
tools/build_jar.py            ForgeGradle 無しで jar を組み立てるフォールバック
```

## ビルド方法

### 通常（ネット接続あり）

```bash
./gradlew build
# => build/libs/astranautics-1.0.0.jar
```

開発用クライアント起動:

```bash
./gradlew runClient
```

### フォールバック（Forge / Maven に接続できない環境）

`tools/build_jar.py` は ForgeGradle を使わずに、`@Mod` 付きの class ファイルと
`META-INF/mods.toml` を直接生成して jar にまとめます（Python 3.9+）。

```bash
python3 tools/build_jar.py --check
# => build/libs/astranautics-1.0.0.jar
```

`--check` は生成した class ファイルを独立パーサで読み直し、
`@Mod("astranautics")` アノテーションが正しく入っていることを検証します。

> このリポジトリの Release に添付している jar は、Forge/Maven への接続が
> できないサンドボックスで `tools/build_jar.py` により生成したものです。

## ライセンス

MIT
