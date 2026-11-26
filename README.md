# AstraNautics

Minecraft **Forge 1.20.1** 用の MOD プロジェクトです。
現在は **「読み込み確認用（最小構成）」** の状態で、専用アイテムやブロックはまだありません。
起動するとログに `AstraNautics` の読み込みメッセージが出力されます。

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

生成物: `build/libs/astranautics-0.1.0-mc1.20.1.jar`

> `build/libs` には `-sources` などの追加 jar が出力されることがあります。
> ゲームに導入するのは **`astranautics-<version>-mc1.20.1.jar`** です。

### GitHub Actions（自動ビルド）

`.github/workflows/build.yml` により、以下のタイミングで自動ビルドされます。

- `main` / `master` / `arena/**` ブランチへの push
- `main` / `master` 宛の Pull Request
- 手動実行（Actions タブ → Build → Run workflow）

ビルドされた jar は Actions の実行結果ページ下部 **Artifacts → `astranautics-jar`** からダウンロードできます。

## 開発環境での実行

```bash
./gradlew runClient   # クライアント起動
./gradlew runServer   # サーバー起動
```

## ディレクトリ構成

```
src/main/java/com/astranautics/
├── AstraNautics.java     # メインクラス（@Mod エントリポイント）
├── Config.java           # 共通コンフィグ
└── ModListHelper.java    # MOD 情報ヘルパー
src/main/resources/
├── META-INF/mods.toml    # MOD メタデータ
├── pack.mcmeta           # リソースパック定義
└── assets/astranautics/lang/  # 言語ファイル (en_us / ja_jp)
```

## 今後の予定（例）

- [ ] 専用クリエイティブタブの追加
- [ ] 宇宙船・帆船系のアイテム／ブロック
- [ ] ディメンション（宇宙）の追加
- [ ] カスタム GUI・進捗

## ライセンス

`gradle.properties` の `mod_license`（現在: All Rights Reserved）に従います。
