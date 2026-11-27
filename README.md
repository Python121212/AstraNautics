# AstraNautics

Minecraft **1.20.1 / Forge 47.x** 向けの MOD です。

- Mod ID: `astranautics`
- Version: `3.1.1`
- Minecraft: `1.20.1`（`[1.20.1,1.21)`）
- Forge: `47.x`（`[47,)`）
- Java: 17

## 収録ブロック

![ブロックテクスチャ](docs/images/block_textures.png)

左から: 物理アセンブラ（OFF / ON）、物理トグルスイッチ（OFF / ON）。


### 物理アセンブラ（Physics Assembler / `astranautics:physics_assembler`）

> 旧称「グラビティ・アンカー」。v3.0.0 でブロック ID・名称を物理アセンブラに改名しました。

周囲のブロック構造を**剛体として切り離し、物理演算で動かす**ブロックです
（Create: Aeronautics / Valkyrien Skies 系）。

- `enabled=true`（物理演算 ON）にすると、アセンブラに繋がっているブロック群を
  半径 `assemblyRadius`（既定 8）以内で BFS 探索し、1 つの剛体（`PhysicsShip`）として
  ワールドから切り離します。
- **物理演算できるブロックはすべて対象**です。岩盤・コマンドブロック・チェストなど、
  クリエイティブ専用ブロックや BlockEntity を持つブロックも剛体になります
  （空気、当たり判定の無いブロック（草花・たいまつ・流体）、この MOD 自身の
  アセンブラ／スイッチだけが対象外）。
- 切り離された構造物は質量・慣性テンソルを持ち、重力・摩擦・反発・回転を伴って
  落下・滑走・衝突します。
- `enabled=false`（OFF）にすると構造物はその場でブロックに戻ります（`dissolve`）。
- **右クリック（素手）**で、ON/OFF・船の UUID・対象ブロック数・直近の結果を
  チャットに表示します（`/astranautics diag` でも周辺の状態を一覧できます）。
- アセンブラ自体は構造物に含まれないため、切り離し後もその場に残ります。
  アセンブラを**壊すと**、その船はブロックに戻ります（ブロックが消えません）。

#### 使い方

1. 物理アセンブラを、物理演算したい構造物に**接触**させて置く（地面には付けない）。
2. そのアセンブラに隣接させて物理トグルスイッチを置く。
3. スイッチを**右クリック**すると、その場で組み立てが走り、結果がチャットに出る。
   - 成功: `物理演算 ON — N ブロックを剛体にしました`
   - 失敗: `アセンブルできません: 理由`（状態は ON のまま維持され、60 tick 後に再試行）
4. もう一度スイッチを右クリックすると、ブロックに戻ります。

### 物理トグルスイッチ（Physics Toggle Switch / `astranautics:physics_switch`）

アセンブラに**隣接**させて設置するスイッチです。**右クリック**で隣接する
（6 方向すべての）アセンブラの物理演算を一括 ON/OFF し、結果をチャットに表示します。

## 物理演算エンジン

詳細は [docs/PHYSICS.md](docs/PHYSICS.md) を参照してください。概要:

| 要素 | 実装 |
|---|---|
| 剛体 | 位置・姿勢クォータニオン・線形/角速度、6 DOF |
| 質量特性 | ブロック硬さベースの質量 + 平行軸の定理による慣性テンソル |
| 衝突 | 表面ボクセル OBB × ワールドブロック AABB の **SAT（15軸）** |
| 求解 | 逐次インパルス法（累積インパルス + クランプ、クーロン摩擦、Baumgarte 補正） |
| 積分 | 半陰的オイラー、1 tick あたり可変サブステップ（高速時は自動で増加） |
| 同期 | 位置=バニラ同期 / 姿勢=SynchedEntityData(FLOAT×4) / 構造=Base64文字列（スポーン時は `IEntityAdditionalSpawnData` でも送信） |
| 描画 | `PhysicsShipRenderer` がクォータニオンを適用し表面ボクセルを描画 |
| 当たり判定 | 構造の回転後 AABB を実寸で確保（1×1×1 ではない） |
| 保存 | 構造と剛体状態をエンティティ NBT に保存 |
| 船 vs 船 | 空間インデックス近傍の OBB 衝突 + 2体ソルバ（1 tick 1回） |
| 船 vs エンティティ | 押し出し + 上面では乗って移動（1 tick 1回） |
| 軽量化 | ウォームスタート / スリープ / 表面ボクセルのみ / クエリのメモ化 / 可変サブステップ |

### 検証（GitHub Actions 上のユニットテスト）

`src/test/java/com/astranautics/physics/` に物理コアのテストがあり、
`./gradlew test` で CI 上で実行されています（合成ボクセルワールドでの実シミュレーション）。

- 立方体が床の上で静止する（sink/float しない）
- 高速落下でも床を突き抜けない
- 壁に衝突して停止する
- 角から落ちると落下する
- 運動学的（OFF）ボディは動かない
- 貫通時の法線・深さ（SAT）、点速度・角運動量・クォータニオン正規化

## レシピ

**物理アセンブラ**（1 個）

```
鉄  ネザライト  鉄
レッドストーン エンダーアイ レッドストーン
鉄  黒曜石     鉄
```

**物理トグルスイッチ**（1 個）

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
    block/PhysicsAssemblerBlock.java     物理アセンブラ
    block/PhysicsSwitchBlock.java     トグルスイッチ
    block/entity/ModBlockEntities.java
    block/entity/PhysicsAssemblerBlockEntity.java   アンカーと船のリンク管理
    block/entity/ShipAssembler.java   構造物の切り離し（BFS）
    entity/PhysicsShip.java           物理演算される構造物エンティティ
    entity/ModEntities.java           エンティティ登録
    physics/RigidBody.java            剛体（質量・慣性・力積）
    physics/ShipStructure.java        ボクセル構造と質量特性
    physics/CollisionDetector.java    衝突検出
    physics/Obb.java                  OBB / SAT
    physics/ShipPhysics.java          逐次インパルスソルバ
    physics/BlockCollisionSource.java 衝突形状の供給元（テスト可能）
    physics/LevelCollisionSource.java 実ワールド版
    physics/ShipStructureCodec.java   構造のシリアライズ
    client/PhysicsShipRenderer.java   船の描画
    client/AstraClientEvents.java     クライアント登録
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
# => build/libs/astranautics-3.1.1.jar
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

## 軽量化のポイント

| 手法 | 内容 |
|---|---|
| ウォームスタート | 前ステップの接触インパルスを引き継ぎ、ソルバ反復 8 → 4 でも同精度 |
| スリープ | 静止した船（既定 40 tick）は物理計算を完全にスキップ。近接物体で自動起床 |
| 空間インデックス | 表面ボクセルを 2 ブロック格子に索引化し、船同士・エンティティ・描画の判定を高速化 |
| クエリのメモ化 | 1 ステップ内のワールドブロック参照をキャッシュ |
| 表面のみ | 衝突・描画とも内部ボクセルを除外（コストは体積ではなく表面積に比例） |
| 可変サブステップ | 速度に応じてだけ分割数を増やす |

## トラブルシューティング

| 症状 | 原因 / 対処 |
|---|---|
| 組み立てた瞬間にゲームが落ちる（v3.1.0） | 船のバウンディングボックスがコンストラクタ実行前に参照されていたため。**v3.1.1 で修正** |
| スイッチを押すと青く光ってすぐ消える（v3.0.0 まで） | アセンブルに失敗したときにブロック状態を即 OFF に戻していたため。**v3.1.0 で修正**し、失敗時は ON を維持して理由をチャット・ログに出すようにしました |
| アセンブラを ON にしても何も起きない | 右クリックで状態を確認。`対象ブロック -`／失敗理由が出ます。ログの `[astranautics]` 行にも詳細が出ます |
| 地面ごと巻き込まれて穴が空く | アセンブラが地形に接触していると、繋がっている地形も構造物として切り離されます。アセンブラは地面から離して置くか、`assemblyRadius` / `maxBlocks` を小さくしてください |
| 船が見えない | v3.1.0 でスポーンパケットに構造を含めるよう修正（後から近づいたプレイヤーにも表示されます） |
| 何ブロック拾ったか知りたい | 素手でアセンブラを右クリック、または `/astranautics diag` |

## 既知の制限

`docs/PHYSICS.md` の「既知の制限」に詳細があります。要点:

- 浮力・空気抵抗・推力（プロペラ）などの空気力学は未実装（Phase 3 候補）
- 物理はサーバースレッドで計算（船単位の並列化は未着手）
- 船上でのブロック操作・レッドストーン・インベントリは未対応
- エンティティは運動学的（プレイヤーが船を押し返すことはできない）

## 変更履歴

- **v3.1.1** — 組み立て時のクラッシュ（`NullPointerException: halfExtents`）を修正。
  船の当たり判定の初期化順の問題で、ブロックエンティティ tick 中の例外がサーバーを落としていました。
  併せて、物理 tick・組み立て・復元の各処理に例外ガードを追加（今後は該当の船だけを停止してログに残し、
  ワールドは落ちません）。失敗理由の日本語化も実施
- **v3.1.0** — 全ブロック（岩盤・BlockEntity 持ち含む）を物理演算対象に、
  アセンブル失敗時の点滅を修正（ON 維持 + 理由表示）、スイッチ／右クリックでの即時実行と結果表示、
  船の実寸 AABB、スポーン時の構造同期、テクスチャ刷新、`/astranautics diag`
- **v3.0.0** — ブロックを `physics_assembler` / `physics_switch` に改名（= 物理アセンブラ）、
  船 vs 船 / 船 vs エンティティ衝突、スリープ、ウォームスタートなどの軽量化
- **v2.0.0** — 剛体物理エンジン実装
- **v1.1.0** — アセンブラ前身 + トグルスイッチ（エンティティ引力方式）
- **v1.0.0** — 読み込み確認用の最小 MOD

## ライセンス

MIT
