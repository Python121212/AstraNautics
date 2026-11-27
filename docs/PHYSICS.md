# AstraNautics Physics Engine

「グラビティ・アンカー」で切り離したブロック構造物を 6 自由度の剛体として
シミュレーションする、Valkyrien Skies / Create: Aeronautics 系の物理実装です。

現在の実装は **Phase 1（剛体コア）** です。

## アーキテクチャ

```
GravityAnchorBlock (enabled ブロックステート)
   │  隣接する GravitySwitchBlock を右クリックで ON/OFF
   ▼
GravityAnchorBlockEntity … 4 tick ごとに状態と船の有無を比較
   │   ON  → ShipAssembler.assemble()
   │   OFF → GravityShip.dissolve()
   ▼
ShipAssembler … アンカーから 6 方向 BFS。半径/ブロック数上限つき
   │   対象: 衝突形状を持ち、BE を持たず、流体でないブロック
   │   アンカー / スイッチ自身は除外
   ▼
ShipStructure … ローカル座標のボクセルグリッド
   │   質量 = Σ ブロック質量（硬さベース: 0.4 + 0.35 × hardness、上限 6）
   │   慣性テンソル = Σ [m/6·E + m(r²E − r rᵀ)] （平行軸の定理）
   ▼
RigidBody … 位置・姿勢(クォータニオン)・線形/角速度・力/トルク
   ▼
ShipPhysics.step() … 固定ステップ (1/20 s ÷ substeps)
   1. 重力を力として加算
   2. 速度積分（減衰つき）
   3. CollisionDetector で接触生成
   4. 逐次インパルス法で接触解決（法線 + クーロン摩擦、Baumgarte 補正）
   5. 位置積分
   6. 最深貫通に対する位置補正
   ▼
GravityShip (Entity) … サーバーで物理を進め、位置と姿勢を同期
   ▼
GravityShipRenderer … クォータニオンで姿勢を適用し、全ボクセルを描画
```

## 衝突検出

- 構造物の**表面ボクセルだけ**を対象（内部は絶対に接触しない）
- 各ボクセルは OBB（辺 1 ブロック）として扱い、ワールドのブロック衝突形状
  （`VoxelShape#toAabbs`）と **SAT（分離軸定理, 15 軸）** で判定
- 得られた最小貫通軸を接触法線とし、貫通量とともに接触点を生成
- 接触は貫通量の大きい順に最大 48 点まで

## 同期

| データ | 手段 |
|---|---|
| 位置 | バニラのエンティティ位置同期（`Entity#setPos`） |
| 姿勢 | `SynchedEntityData` の FLOAT × 4（クォータニオン） |
| ボクセル構造 | `SynchedEntityData` の STRING（Base64 化したバイナリ） |
| セーブ | `GravityShip#addAdditionalSaveData`（構造 + 剛体状態） |

クライアントは受け取ったクォータニオンを `slerp` で補間して描画します。

## 設定 (`config/astranautics-common.toml`)

| キー | 既定値 | 説明 |
|---|---|---|
| `gravity_anchor.assemblyRadius` | 8 | 構造物として切り離す半径 |
| `gravity_anchor.maxBlocks` | 512 | 1 隻あたりの最大ブロック数 |
| `physics.gravity` | 32.0 | 重力 (blocks/s²) |
| `physics.substeps` | 2 | 1 tick あたりの物理サブステップ数 |
| `physics.friction` | 0.6 | 摩擦係数 |
| `physics.restitution` | 0.05 | 反発係数 |
| `physics.linearDamping` | 0.999 | 線形減衰 |
| `physics.angularDamping` | 0.995 | 角減衰 |

## 既知の制限（Phase 2 以降）

1. **エンティティとの衝突なし** — プレイヤー / MOB は船の上に乗れない
2. **船同士の衝突なし** — 船はワールドとのみ衝突する
3. **空気力学なし** — 浮力・抗力・プロペラ推力・翼揚力は未実装
4. **スレッディングなし** — 物理はサーバースレッドで計算
5. **スリープなし** — 静止していても毎 tick 計算する
6. 描画はボクセル単位の `renderSingleBlock` で、大きな船では描画コストが高い
7. クライアント補間は姿勢のみ（位置はバニラの補間に依存）
8. 船の上でのブロック設置・破壊、レッドストーン、インベントリ等は未対応

### Phase 2 で入れる予定

- 船 vs エンティティ / 船 vs 船の接触
- スリープ＋ウェイク（静止船のコスト削減）
- 浮力・抗力・推力ブロック（プロペラ / スラスター）
- チャンク単位のメッシュ化による描画最適化
- 物理のワーカースレッド分離とサブステップの可変長化
