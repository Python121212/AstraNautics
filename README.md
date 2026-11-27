# AstraNautics

Minecraft **1.20.1 / Forge 47.x** 向けの MOD です。

- Mod ID: `astranautics`
- Version: `2.0.0`
- Minecraft: `1.20.1`（`[1.20.1,1.21)`）
- Forge: `47.x`（`[47,)`）
- Java: 17

## 収録ブロック

### グラビティ・アンカー（Gravity Anchor）

周囲のブロック構造を**剛体として切り離し、物理演算で動かす**ブロックです
（Create: Aeronautics / Valkyrien Skies 系）。

- `enabled=true`（物理演算 ON）にすると、アンカーに繋がっているブロック群を
  半径 `assemblyRadius`（既定 8）以内で BFS 探索し、1 つの剛体（`GravityShip`）として
  ワールドから切り離します。
- 切り離された構造物は質量・慣性テンソルを持ち、重力・摩擦・反発・回転を伴って
  落下・滑走・衝突します。
- `enabled=false`（OFF）にすると構造物はその場でブロックに戻ります（`dissolve`）。

### グラビティ・トグルスイッチ（Gravity Toggle Switch）

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
| 描画 | `GravityShipRenderer` がクォータニオンを適用し全ボクセルを描画 |
| 保存 | 構造と剛体状態をエンティティ NBT に保存 |

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
    block/entity/GravityAnchorBlockEntity.java   アンカーと船のリンク管理
    block/entity/ShipAssembler.java   構造物の切り離し（BFS）
    entity/GravityShip.java           物理演算される構造物エンティティ
    entity/ModEntities.java           エンティティ登録
    physics/RigidBody.java            剛体（質量・慣性・力積）
    physics/ShipStructure.java        ボクセル構造と質量特性
    physics/CollisionDetector.java    衝突検出
    physics/Obb.java                  OBB / SAT
    physics/ShipPhysics.java          逐次インパルスソルバ
    physics/BlockCollisionSource.java 衝突形状の供給元（テスト可能）
    physics/LevelCollisionSource.java 実ワールド版
    physics/ShipStructureCodec.java   構造のシリアライズ
    client/GravityShipRenderer.java   船の描画
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

## 既知の制限

`docs/PHYSICS.md` の「既知の制限」に詳細があります。要点:

- 船 vs エンティティ / 船 vs 船の衝突は未実装（ワールドとのみ衝突）
- 浮力・空気抵抗・推力（プロペラ）などの空気力学は未実装
- 物理はサーバースレッドで計算（スレッディングなし）
- 船上でのブロック操作・レッドストーン・インベントリは未対応

## ライセンス

MIT
