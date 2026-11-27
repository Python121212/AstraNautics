# AstraNautics

Minecraft **1.20.1 / Forge 47.x** 向けの MOD です。

- Mod ID: `astranautics`
- Version: `3.0.0`
- Minecraft: `1.20.1`（`[1.20.1,1.21)`）
- Forge: `47.x`（`[47,)`）
- Java: 17

## 収録ブロック

### 物理アセンブラ（Physics Assembler / `astranautics:physics_assembler`）

> 旧称「グラビティ・アンカー」。v3.0.0 でブロック ID・名称を物理アセンブラに改名しました。

周囲のブロック構造を**剛体として切り離し、物理演算で動かす**ブロックです
（Create: Aeronautics / Valkyrien Skies 系）。

- `enabled=true`（物理演算 ON）にすると、アンカーに繋がっているブロック群を
  半径 `assemblyRadius`（既定 8）以内で BFS 探索し、1 つの剛体（`PhysicsShip`）として
  ワールドから切り離します。
- 切り離された構造物は質量・慣性テンソルを持ち、重力・摩擦・反発・回転を伴って
  落下・滑走・衝突します。
- `enabled=false`（OFF）にすると構造物はその場でブロックに戻ります（`dissolve`）。

### 物理トグルスイッチ（Physics Toggle Switch / `astranautics:physics_switch`）

アンカーに**隣接**させて設置するスイッチです。**右クリック**で隣接する
（6 方向すべての）アンカーの物理演算を一括 ON/OFF します。

## 物理演算エンジン

詳細は [docs/PHYSICS.md](docs/PHYSICS.md) を参照してください。概要:

| 要素 | 実装 |
|---|---|
| 剛体 | 位置・姿勢クォータニオン・線形/角速度、6 DOF |
| 質量特性 | ブロック硬さベースの質量 + 平行軸の定理による慣性テンソル |
| 衝突 | 表面ボクセル OBB × ワールドブロック AABB の **SAT（15軸）** |
| 求解 | 逐次インパルス法（累積インパルス + クランプ、クーロン摩擦、Baumgarte 補正） |
| 積分 | 半陰的オイラー、1 tick あたり可変サブステップ（高速時は自動で増加） |
| 同期 | 位置=バニラ同期 / 姿勢=SynchedEntityData(FLOAT×4) / 構造=Base64文字列 |
| 描画 | `PhysicsShipRenderer` がクォータニオンを適用し全ボクセルを描画 |
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
# => build/libs/astranautics-2.0.0.jar
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

## 既知の制限

`docs/PHYSICS.md` の「既知の制限」に詳細があります。要点:

- 浮力・空気抵抗・推力（プロペラ）などの空気力学は未実装（Phase 3 候補）
- 物理はサーバースレッドで計算（船単位の並列化は未着手）
- 船上でのブロック操作・レッドストーン・インベントリは未対応
- エンティティは運動学的（プレイヤーが船を押し返すことはできない）

## 変更履歴

- **v3.0.0** — ブロックを `physics_assembler` / `physics_switch` に改名（= 物理アセンブラ）、
  船 vs 船 / 船 vs エンティティ衝突、スリープ、ウォームスタートなどの軽量化
- **v2.0.0** — 剛体物理エンジン実装
- **v1.1.0** — アンカー + トグルスイッチ（エンティティ引力方式）
- **v1.0.0** — 読み込み確認用の最小 MOD

## ライセンス

MIT
