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

## 浮力（アルキメデス／排水量ベース）v0.5.0

素材の重さではなく **押しのけた水の量** で浮力が決まります。

```
浮力 = 排水セル数 × 水の密度(=1.0)
重さ = セルの質量の合計
浮く ⇔ 浮力 > 重さ
```

- **石の桶・中空の船体でも浮く**：外壁の内側の空洞（空気＋水）を排水量として数えるため、石（質量1.6）でも 2×2×2 の桶なら浮きます
- **木材・羊毛・雪は素材単体でも浮く**（`buoyant`）
- 浮いている間は水面でわずかに波打ち、沈むものはゆっくり沈みます（`Buoyancy.java`）
- `/astranautics cluster` で「排水セル数・質量・浮力比・浮く/沈む」を確認できます

### 浮く・沈むの目安
| 形 | 質量 | 排水 | 結果 |
| --- | --- | --- | --- |
| 石 1 個 | 1.6 | 1 | 沈む |
| 石の桶（2×2×2 中空） | 6.4 | 8 | **浮く** |
| 石の箱（3×3×3 中空） | 14.4 | 27 | **浮く** |
| 鉄の桶（2×2×2） | 9.6 | 8 | 沈む（鉄は重い） |
| 木の板 16 枚 | 9.6 | 16 | **浮く** |

## 全ブロック対応・他 MOD 互換（v0.5.0）

### すべてのブロックを物理化できる
- **BlockEntity を持つブロックも物理化可能**（チェスト・かまど・他 MOD のマシン等）。中身は NBT ごと退避し、**復元時に書き戻します**
- 対象外はごく一部のみ（岩盤・ポータル・バリア・コマンド/ストラクチャーブロック・流体など）

### 物性の上書き（データパック）
`data/<namespace>/astranautics/material_rules.json` を置くだけで、**他 MOD のブロックにも質量・摩擦・反発・浮力を指定できます**。

```json
{
  "rules": [
    { "match": { "tag": "forge:storage_blocks" }, "mass": 2.4, "friction": 0.45 },
    { "match": { "namespace": "create" }, "mass": 1.8 },
    { "match": { "id": "create:mechanical_bearing" }, "mass": 2.0, "restitution": 0.05 }
  ]
}
```
条件は `id`（完全一致）／`tag`（タグ）／`namespace`（MOD 単位）。上から順に評価し、最初に一致したものを採用します。当 MOD も `forge:*` タグを使った既定ルールを同梱しています。

### Forge 共通タグによる自動判別
`forge:stone` / `forge:logs` / `forge:storage_blocks` / `forge:ores` / `forge:glass` / `forge:wool` / `forge:sand` / `forge:gravel` / `forge:dirt` / `forge:chests` / `forge:planks` などに対応。**タグを持っている MOD のブロックは自動でそれらしい物性になります。**

### コンフィグ（`config/astranautics-common.toml`）
```toml
[compatibility]
  blockBlacklist = ["create:mechanical_bearing"]
  modBlacklist   = ["some_heavy_mod"]
  maxClusterSize = 512      # 宇宙船を作るなら 1024〜2048 まで
  enableBuoyancy = true
```

### コマンド
```
/astranautics material      # 見ているブロックの物性を表示（他 MOD ブロックの確認用）
/astranautics physicalize   # 見ているブロックをその場で物理化
```

## 物理演算の強化（v0.4.0〜）

### 素材別の物性（`MaterialProfile`）
ブロックの種類から質量・摩擦・反発・空気抵抗・浮力を推定します。

| 素材 | 質量 | 摩擦 | 反発 | 特徴 |
| --- | --- | --- | --- | --- |
| 鉄・金属 | 2.4 | 0.45 | 0.12 | 重い・滑る・ほぼ弾まない |
| 石・レンガ | 2.0 | 0.80 | 0.10 | 重い・よく止まる |
| 木材 | 0.6 | 0.65 | 0.28 | 軽い・水に浮きやすい |
| 羊毛 | 0.35 | 0.95 | 0.02 | とても軽い・よく止まる |
| 氷 | 0.9 | **0.02** | 0.18 | ほぼ滑る |
| スライム | 1.1 | 0.90 | **0.85** | よく跳ねる |
| ハチミツ | 1.2 | 0.98 | 0.02 | ほぼくっつく |

### クラスタ基盤（将来の「船 = 1 剛体」）
- `PhysicsCluster` … 連結した物理化ブロックのまとまり。合計質量・平均摩擦・平均反発、質量重み付き代表速度を持つ
- `ClusterManager` … 連結成分の再構築（物理化・レバー操作・破壊時のみ／毎 tick の探索なし、最大 512 セル）
- 接触力はセス質量比で配分し、クラスタ全体にも揺れを伝播

### 相互作用
- **プレイヤーが触ると押される**（質量が軽いほど大きく動く。プレイヤー側も少し押し返される）
- **素手で殴ると視線方向へ発射**（`message.astranautics.smack` に質量を表示）
- **爆発**で範囲内の物理化ブロックが吹き飛ぶ（爆発力は距離減衰）
- **水流・浮力**（木や羊毛は浮きやすい）、**レッドストーン信号でレバー自動 ON/OFF**

### コマンド
```
/astranautics stats              # 覚醒セル・スリープセル・クラスタ数
/astranautics cluster            # 見ているブロックのクラスタ情報
/astranautics push <x> <y> <z> <power>   # 任意方向へ押す
/astranautics boom <radius>      # 視線先で爆発相当の力
```

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

## images/ のプレビュー画像（1 枚だけ）

`images/` には **そのアイテム／ブロックの等角図プレビュー 1 枚だけ**を置きます。
（現時点で追加済みは Physicalizer のみなので、`images/` にあるのは 1 枚だけです）

| ファイル | 内容 |
| --- | --- |
| `images/physicalizer.png` | 物理化装置（レバー）の等角図プレビュー（透過 PNG） |

- 生成: `tools/render_preview.py` … MOD 側テクスチャを読み込み、**等角投影で組み立てた状態**に描画
- 素材テクスチャは `src/main/resources/assets/astranautics/textures/` のみに置きます（`images/` には置きません）

```bash
python3 -m venv .venv && .venv/bin/pip install Pillow
.venv/bin/python tools/generate_textures.py   # MOD 用テクスチャを生成
.venv/bin/python tools/render_preview.py      # images/ に等角図 1 枚を生成
```

### レバーの表示（ゲーム内）
物理化されたブロックの上面に 3D レバーが載り、ON で立ち上がって発光、OFF で横倒しになります。
テクスチャは **1 枚のアトラス**（`textures/block/lever_atlas.png` 64x32）で、UV を切り替えて組み立てます。

| アトラス | 用途 |
| --- | --- |
| 1 列目 | 台座の上面（木材） |
| 2 列目 | 持ち手 ON（金属・シアン） |
| 3 列目 | 持ち手 OFF（金属・グレー） |
| 4 列目 | 台座の側面（木材・縦木目） |

3D ジオメトリは「木の 2 段台座 → 金属ブラケット＋ピボット → 3 段テーパーシャフト（継ぎ目リング付き）→ 4 段の球体風ノブ」で、
`images/physicalizer.png` の等角図とまったく同じ形です。
