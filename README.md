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

生成物: `build/libs/astranautics-0.3.0-mc1.20.1.jar`

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
レバーのように**物理演算を ON / OFF** するアイテムです。

| 操作 | 動作 |
| --- | --- |
| ブロックに右クリック | **レバー ON（上）**: そのブロックに物理演算を追加（元の見た目のまま落下・滑走） |
| **空気**（空中）に右クリック | **空中にブロック化**: その場に物理コアが出現し、そのまま落下 |
| 物理化ブロックに右クリック | **レバー OFF（下）**: 物理演算を停止して元の位置に固定 |
| レバー OFF のブロックに右クリック | 再びレバー ON（物理演算を再開） |
| 物理化ブロックに**スニーク＋右クリック** | 元のブロックへ復元 |

- **回転はしません**。姿勢を保持したまま、平行移動だけで物理挙動を表現します。
- 落下・バウンス・接地摩擦・スリープ・物理化ブロック同士の接触インパルスに対応。

## 独自の物理エンジン「AstraLite」

Create: Aeronautics 系の重量級シミュレーションとは別系統の、**超軽量オリジナルエンジン**です。

| 設計 | 内容 |
| --- | --- |
| グリッド非移動 | ブロックはその場に置いたまま、**描画オフセットだけ**を動かす（チャンク再メッシュ・ブロック更新なし） |
| 軸分離 + サブステップ | 1 tick あたり最大 4 サブステップの判定のみ。X/Y/Z を個別に解決 |
| スリープ | 接地して静止したら 20 tick で計算停止。静止中のコストは実質ゼロ |
| 低速同期 | 専用パケット（`SyncPhysicsPacket`）で 1/512 ブロック精度・**近くのプレイヤーのみ**に送信 |
| 回転なし | 姿勢を保持（要望どおり）。行列計算もブロックモデル再構築も不要 |
| 接触インパルス | ぶつかった物理化ブロックへ力を伝播（物理強化） |

```
src/main/java/com/astranautics/physics/LightPhysicsEngine.java   # エンジン本体
src/main/java/com/astranautics/network/SyncPhysicsPacket.java    # 軽量同期
```

### 物理コア — `astranautics:physics_core`
空中で物理化したときに出現するブロックです（テクスチャ: `images/physics_core.png`）。

### テクスチャ
| ファイル | 内容 |
| --- | --- |
| `images/physicalizer_block.png` | 物理化装置のブロックテクスチャ（16x16 原本） |
| `images/physicalizer_block_preview.png` | 8 倍拡大プレビュー |
| `images/physics_core.png` | 物理コアのテクスチャ（16x16 原本） |
| `images/physics_core_preview.png` | 物理コアの 8 倍拡大プレビュー |
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
├── physics/                   # 独自軽量物理エンジン AstraLite
├── network/                   # 軽量同期パケット
└── client/                    # クライアント専用（レンダラー・セットアップ）
src/main/resources/
├── META-INF/mods.toml    # MOD メタデータ
├── pack.mcmeta           # リソースパック定義
└── assets/astranautics/lang/  # 言語ファイル (en_us / ja_jp)
```

## 今後の予定（例）

- [x] 専用クリエイティブタブの追加
- [x] 物理化装置（ブロックに物理演算を追加）
- [x] 物理演算の ON/OFF（レバー式）・空中ブロック化
- [x] 独自軽量物理エンジン「AstraLite」
- [ ] 宇宙船・帆船系のアイテム／ブロック
- [ ] ディメンション（宇宙）の追加
- [ ] カスタム GUI・進捗

## ライセンス

`gradle.properties` の `mod_license`（現在: All Rights Reserved）に従います。

## images/ の画像一覧（追加時は必ず PNG を置く）

| ファイル | 内容 |
| --- | --- |
| `images/physicalizer_block.png` | 物理化装置のブロックテクスチャ（原本 16x16） |
| `images/lever_on.png` | レバー **ON（上）** の状態 |
| `images/lever_off.png` | レバー **OFF（下）** の状態 |
| `images/lever_on_off_preview.png` | ON / OFF の比較プレビュー |
| `images/physicalized.png` | 物理化されたブロックのイメージ（ブロック＋レバー合成） |
| `images/physics_core.png` | 物理コアのテクスチャ |

- 各 PNG には 8 倍拡大の `*_preview.png` も生成されます。
- すべて `tools/generate_textures.py` で再生成できます。
- **新しいアイテム／ブロックを追加したら、必ず PNG を作って `images/` に保存する**（`AGENTS.md` にも記載）。

### レバーの表示
物理化されたブロックの上面に、状態に応じたレバー（ON=上 / OFF=下）が表示されます。
テクスチャは `assets/astranautics/textures/block/lever_on.png` / `lever_off.png` を使用し、
`LeverOverlayRenderer` が 2 枚の直交板として軽量に描画します。

### 3D レバー（v0.3.2〜）
物理化されたブロックの上には、**本物の 3D レバー**が載ります。

- 台座（真鍮の立方体）＋ 持ち手（回転する角柱）＋ ノブで構成された純粋な 3D ジオメトリ
- **ON** では持ち手が立ち上がって発光、**OFF** では横倒し（回転は描画時のみで滑らかに補間）
- 使用テクスチャ: `images/lever_base.png` / `images/lever_handle_on.png` / `images/lever_handle_off.png`

| ファイル | 内容 |
| --- | --- |
| `images/lever_base.png` | 台座のマテリアル |
| `images/lever_handle_on.png` | 持ち手（ON・シアン） |
| `images/lever_handle_off.png` | 持ち手（OFF・グレー） |
| `images/lever_3d_parts_preview.png` | 台座＋持ち手の見本 |
