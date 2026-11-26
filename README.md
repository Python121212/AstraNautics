# AstraNautics

Minecraft **Forge 1.20.1** 用の MOD プロジェクトです。
専用クリエイティブタブ **AstraNautics** と、ブロックに物理演算を追加する **物理化装置（Physicalizer）** を実装しています。

| 項目 | 値 |
| --- | --- |
| MOD ID | `astranautics` |
| 対応バージョン | Minecraft 1.20.1 |
| MOD ローダー | Forge 47.x（開発: 47.2.20） |
| Java | 17 |
| Gradle | 8.1.1（Wrapper） |
| ModDevGradle | ForgeGradle `[6.0,6.2)` |

## ビルド方法

### ローカルでビルド

```bash
# 初回は Forge の依存解決に時間がかかります
./gradlew build
```

生成物: `build/libs/astranautics-0.2.1-mc1.20.1.jar`

> `build/libs` には `-sources` などの追加 jar が出力されることがあります。
> ゲームに導入するのは **`astranautics-<version>-mc1.20.1.jar`** です。

### GitHub Actions（自動ビルド）

`.github/workflows/build.yml` により、以下のタイミングで自動ビルドされます。

- `main` / `master` / `arena/**` ブランチへの push
- `main` / `master` 宛の Pull Request
- 手動実行（Actions タブ → Build → Run workflow）

ビルドされた jar は Actions の実行結果ページ下部 **Artifacts → `astranautics-jar`** からダウンロードできます。

### リリース（jar 配布）

`.github/workflows/release.yml` により、jar が GitHub Releases に添付されます。

- **手動実行**: Actions タブ → **Release** → Run workflow（`version` を空にすると `gradle.properties` の `mod_version` を使用）
- **タグ push**: `v0.1.0` のようなタグを push すると自動でそのバージョンのリリースを作成

作成されるリリース: `https://github.com/Python121212/AstraNautics/releases`

## 収録コンテンツ

### 専用クリエイティブタブ「AstraNautics」
クリエイティブ表示時に左側のタブに専用タブが追加されます（アイコンは物理化装置）。

### 物理化装置（Physicalizer）— `astranautics:physicalizer`
ブロックに使用すると、そのブロックに**物理演算（浮遊・回転アニメーション）**を追加します。

- ブロックに**右クリック** → 対象ブロックが「物理化されたブロック」に変化し、元ブロックの見た目を保ったまま宙に浮いてゆっくり回転します
  - 通常のブロックをそのまま置き換えるため、向き（階段・原木など）や種類もそのまま反映されます
- 物理化されたブロックに**右クリック** → 元のブロックに戻ります
- **スニーク＋右クリック** → 使い方のヘルプを表示
- 設置時に任意位置（`/setblock` など）でも動作します

> 実装メモ: 物理化されたブロックは `astranautics:physicalized`（BlockEntity + カスタムレンダラー）。
> 元ブロックの `BlockState` は NBT に保存され、描画は `PhysicalizedRenderer` が浮遊・回転・拡縮を加えて行います。
> サーバー側では軽量（見た目のみ）で動作するため、ラグの原因になりにくい設計です。
> 物理化されたブロックを破壊すると、物理化装置が 1 個ドロップします（ルートテーブル）。

### テクスチャ
| ファイル | 内容 |
| --- | --- |
| `images/physicalizer_block.png` | 物理化装置のブロックテクスチャ（16x16 原本） |
| `images/physicalizer_block_preview.png` | 8 倍拡大プレビュー |
| `src/main/resources/assets/astranautics/textures/block/physicalizer.png` | MOD 側で使用するテクスチャ |

`tools/generate_textures.py`（Pillow 使用）で再生成できます。

```bash
pip install Pillow
python tools/generate_textures.py
```

## 開発環境での実行

```bash
./gradlew runClient   # クライアント起動
./gradlew runServer   # サーバー起動
```

## ディレクトリ構成

```
src/main/java/com/astranautics/
├── AstraNautics.java          # メインクラス（@Mod エントリポイント）
├── Config.java                # 共通コンフィグ
├── ModListHelper.java         # MOD 情報ヘルパー
├── registry/                  # レジストリ登録（Blocks / Items / BlockEntities / CreativeTabs）
├── block/                     # 物理化ブロックと BlockEntity
├── item/                      # 物理化装置
└── client/                    # クライアント専用（レンダラー・セットアップ）
src/main/resources/
├── META-INF/mods.toml    # MOD メタデータ
├── pack.mcmeta           # リソースパック定義
└── assets/astranautics/lang/  # 言語ファイル (en_us / ja_jp)
```

## 今後の予定（例）

- [x] 専用クリエイティブタブの追加
- [x] 物理化装置（ブロックに物理演算を追加）
- [ ] 宇宙船・帆船系のアイテム／ブロック
- [ ] ディメンション（宇宙）の追加
- [ ] カスタム GUI・進捗

## ライセンス

`gradle.properties` の `mod_license`（現在: All Rights Reserved）に従います。
