# AstraNautics 作業ルール（毎回必ず確認する）

このファイルはこのリポジトリで作業するエージェント／開発者が毎回読むルールです。

## 0. 思考言語

- **Thought（思考）は必ず日本語で書く。**

## 1. テクスチャ（PNG）のルール

- **`images/` には、アイテム／ブロックごとに等角図プレビュー PNG を 1 枚だけ置く。**
  - 命名: `images/<アイテムまたはブロック名>.png`（例: `images/physicalizer.png`）
  - 追加したアイテム／ブロックが 1 つだけなら、`images/` の中身も 1 枚だけにする。
  - 状態違い・パーツ違いの PNG を `images/` に増やさない（等角図 1 枚に合成する）。
- **プレビューは Python でテクスチャから組み立てて描く**（手描きしない）。
  - `tools/generate_textures.py` … MOD 用テクスチャの生成（games 側リソースへ出力）
  - `tools/render_preview.py` … 等角投影で組み立てた状態のプレビューを `images/` に出力
- MOD 用の素材テクスチャは `src/main/resources/assets/astranautics/textures/{block,item}/` にのみ置く。
- 複数パーツで構成する 3D オブジェクトは **1 枚のアトラス**にまとめる（現在: `lever_atlas.png` 64x32）。

```bash
python3 -m venv .venv && .venv/bin/pip install Pillow
.venv/bin/python tools/generate_textures.py
.venv/bin/python tools/render_preview.py
```

## 2. バージョン・リリース

- 機能追加ごとに `gradle.properties` の `mod_version` を上げる。
- `main` にマージ後、Release ワークフローを実行して **GitHub Releases に jar を添付**する。
- 作業は `arena/*` ブランチ → PR → `main` マージ の順。

## 3. 物理エンジン方針

- 独自の超軽量エンジン **AstraLite**（`physics/LightPhysicsEngine.java`）を使う。
- ブロックはグリッドに置いたまま、描画オフセットだけを動かす（チャンク再メッシュ禁止）。
- **回転させない**（姿勢保持）。
- 同期は `network/SyncPhysicsPacket`（近傍プレイヤーのみ・1/512 精度）。
