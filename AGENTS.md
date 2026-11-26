# AstraNautics 作業ルール（毎回必ず確認する）

このファイルはこのリポジトリで作業するエージェント／開発者が毎回読むルールです。

## 0. 思考言語

- **Thought（思考）は必ず日本語で書く。**

## 1. テクスチャ（PNG）のルール

- **アイテム・ブロックを新規追加したら、必ず PNG を作って `images/` に保存する。**
  - 生成スクリプト: `tools/generate_textures.py`（Pillow 使用）
  - 原本: `images/<name>.png`（16x16）
  - 確認用プレビュー: `images/<name>_preview.png`（8倍拡大・NEAREST）
  - MOD 側: `src/main/resources/assets/astranautics/textures/item/` または `textures/block/`
- PNG は手作業で生成せず、スクリプトに追記して再現可能にすること。

```bash
python3 -m venv .venv && .venv/bin/pip install Pillow
.venv/bin/python tools/generate_textures.py
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
