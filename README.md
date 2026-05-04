# HelloToggleCxrl

**グラス主導アプリのテンプレート**。Rokid Glasses (YodaOS SPRITE) を主役にして、スマホは「最初に 1 回だけ触って後は忘れていい companion」として裏方に徹する構成のサンプル。

## このプロジェクトの狙い

スマホを意識せずにグラスアプリを使えるテンプレートを目指す。

- **初回セットアップ** (ユーザーがスマホを触るのはここだけ): Hi Rokid 認証 → `[接続開始]` を 1 回押す
- **以降**: スマホは Foreground Service で CXRL 接続を常駐させ続ける。アプリを閉じてもバックグラウンドで生きてる
- **通常運用**: ユーザーはグラスだけを使う。グラスの操作ログは自動でスマホ側に蓄積されていく
- スマホアプリは「接続の番人 + データの受け皿」。UI として意識する必要はない

本リポジトリの機能自体は「Hello World 表示のトグル / メッセージ切替」だけの最小サンプルだが、構成要素 (CXR セッション、application-level handshake、heartbeat、状態同期、Foreground Service 常駐) はそのままグラス主導の他アプリにも流用できることを意図している。

## このリポジトリと依存リポジトリ

本リポは **CxrGlobal** ライブラリに依存している。CXR-L の公式 SDK (`com.rokid.cxr:client-l`) は中国版 Hi Rokid (`com.rokid.sprite.aiapp`) にハードコードされていてグローバル版環境では動かないため、グローバル版 (`com.rokid.sprite.global.aiapp`) で動作するよう CxrGlobal が薄いラッパーとして橋渡しをしている。

```
┌──────────────────────────────────────────┐
│ HelloToggleCxrl  ← このリポジトリ
│   phone/  : スマホアプリ (Compose)
│   glass/  : グラスアプリ (Compose)
└────┬───────────────────────────┬─────────┘
     │ ① depends on              │ ② Caps シリアライザ / グラス側 Bridge
     │ (Gradle composite build)  │ (Rokid maven)
     ▼                            ▼
   CxrGlobal              com.rokid.cxr:client-l (phone)
   (Hi Rokid global       com.rokid.cxr:cxr-service-bridge (glass)
    対応の薄いラッパー)
```

| 役割 | リポジトリ / 依存 | 説明 |
|---|---|---|
| ① ライブラリ | [TakanariShimbo/CxrGlobal](https://github.com/TakanariShimbo/CxrGlobal) | グローバル版 Hi Rokid 対応の CXR-L 薄いラッパー。本リポは Gradle composite build (`includeBuild("../../CxrGlobal")`) で取り込む |
| 本体 | **HelloToggleCxrl** (このリポ) | スマホ + グラスの双方向通信サンプル (グラス主導アプリのテンプレート) |
| ② Caps (phone) | `com.rokid.cxr:client-l:1.0.1` (Rokid maven) | Wire 互換のため本家 SDK の Caps シリアライザだけ借用 |
| ② Bridge (glass) | `com.rokid.cxr:cxr-service-bridge:1.0-20260212.103714-88` (Rokid maven) | グラス側の `CXRServiceBridge` 実装 |

> 同じ依存関係を使った別の参考実装に [cxrlsample101-global](https://github.com/TakanariShimbo/cxrlsample101-global) がある。Rokid 公式 `CXRLSample` をグローバル対応させたデモで、CustomView / CustomApp / Audio / Photo / CustomCmd の全機能を網羅している。

## 端末構成

| | phone | glass |
|---|---|---|
| 端末 | Pixel 8 (Android 14+) | Rokid Glasses (YodaOS SPRITE / Android 12 ベース) |
| パッケージ | `com.example.hellotoggle.phone` | `com.example.hellotoggle.glass` |
| 役割 | 認証 / 接続維持 / ログ受信表示 (バックグラウンド) | UI 描画 / ジェスチャ入力 / イベント送信 (主役) |

## 動作概要

### グラス側
- 中央に現在のメッセージを緑で表示 (`Hello World` / `こんにちは` / `Bonjour` / `안녕`)
- スマホとの接続が切れたら赤の **"Phone not connected"** に切替

| ジェスチャ | キーコード | 動作 |
|---|---|---|
| シングルタップ | `KEYCODE_ENTER` | 表示/非表示トグル |
| 前スワイプ | `KEYCODE_DPAD_RIGHT` | 次のメッセージへ |
| 後スワイプ | `KEYCODE_DPAD_LEFT` | 前のメッセージへ |
| ダブルタップ | `KEYCODE_BACK` | アプリ終了 (system に通す) |

ジェスチャ→キーコードの対応根拠 / YodaOS SPRITE の予約ジェスチャ一覧は `GlassGestureProbe/GLASS_GESTURES.md` (別 repo) を参照。

### スマホ側
- Hi Rokid 認証 → token を `EncryptedSharedPreferences` に永続化
- Foreground Service (`dataSync` 型) が CXRL 接続を常駐
- 接続状態カード + 操作ボタン + ログタイムライン
- グラスからのジェスチャイベントを 200 件まで保持

## アーキテクチャ

```
phone:                                     glass:
┌──────────────┐  rk_custom_client  ┌──────────────┐
│   Compose    │   session_open     │              │
│   MainAct    │   ping (5s)        │              │
│      │       │   session_close    │              │
│      ▼       │ ─────────────────▶ │   Compose    │
│ ConnectionSv │                    │   MainAct    │
│ (Foreground) │                    │      ▲       │
│   CXRLink    │                    │      │       │
│      ▲       │  rk_custom_key     │ GlassBridge  │
│      │       │  ◀───────────────  │ CXRServiceBr │
│              │  gesture event     │              │
└──────────────┘                    └──────────────┘
       │                                   │
       └───────── Hi Rokid (BT) ───────────┘
```

### セッション handshake & heartbeat
BT 接続だけでは「app 同士が話せる状態か」までは判らないので、CXR session の上にもう一段 application-level セッションを乗せている。

- phone が `appStart` 成功後に `session_open` を送信し、以降 5 秒ごとに `ping` を流す
- glass は `session_open` または `ping` を受け取るたびに 12 秒の watchdog を再 arm
- 12 秒無受信なら glass は「phone がサイレント kill された」とみなして UI を `Phone not connected` に
- phone の `[接続停止]` 操作時は `onDestroy` で明示的に `session_close` を送ってから `disconnect()`

## 通信プロトコル

| チャンネル | 方向 | 用途 |
|---|---|---|
| `rk_custom_client` | phone → glass | `event` ∈ {`session_open`, `session_close`, `ping`} |
| `rk_custom_key` | glass → phone | ジェスチャイベント |

ペイロードは `com.rokid.cxr.Caps` を positional で書き込み (キー文字列とその値が交互に並ぶ)。例: グラス→スマホのジェスチャイベント:

```
write("event"),   write(<"tap"|"swipe_next"|"swipe_prev">)
write("visible"), writeInt32(<0|1>)
write("index"),   writeInt32(<0..3>)
write("message"), write(<message>)
write("ts"),      writeInt64(<epoch ms>)
```

## 動作要件

| カテゴリ | 必要条件 | 動作確認済み |
|---|---|---|
| スマホ | Android (minSdk 31 / compileSdk 36) | Google Pixel 8 / Android 16 (SDK 36) |
| グラス | スマホとペアリング済みであること | Rokid Glasses / YodaOS SPRITE 1.18.007-20260427-150201 |
| Hi Rokid アプリ | グローバル版 (`com.rokid.sprite.global.aiapp`) インストール済み | G1.5.9.0408 (versionCode 10050009) |
| ビルド環境 | Android Studio (Kotlin 2.2.10 / AGP 9.2.0 / Compose BOM 2026.02.01) | — |

## セットアップ

### 1. 隣接配置で 2 リポジトリを clone

CxrGlobal は Gradle composite build (`includeBuild("../../CxrGlobal")`) で参照するので **同じ親ディレクトリに並べて** clone する:

```bash
cd ~/AndroidStudioProjects
git clone https://github.com/TakanariShimbo/CxrGlobal.git
git clone https://github.com/TakanariShimbo/HelloToggleCxrl.git
# → CxrGlobal / HelloToggleCxrl が並ぶ
```

### 2. SDK パスを設定

`phone/local.properties` と `glass/local.properties` のそれぞれに:

```properties
sdk.dir=/path/to/Android/Sdk
```

### 3. JDK は Android Studio バンドル JBR を使う

```bash
export JAVA_HOME=/opt/android-studio/jbr
export PATH=$JAVA_HOME/bin:$PATH
```

### 4. グラス側アプリをビルド & グラスへ投入

```bash
cd HelloToggleCxrl/glass
./gradlew installDebug
# (もしくは ./gradlew assembleDebug → adb install で手動)
```

### 5. スマホ側アプリをビルド & スマホへ投入

```bash
cd ../phone
./gradlew installDebug
```

## 使い方

1. スマホでアプリ起動 → `[認証]` (初回のみ、グローバル版 Hi Rokid の認証ダイアログが出る)
2. `[接続開始]` → 通知バーに Foreground Service の通知が出る (アプリを閉じても接続は維持される)
3. グラス側に `com.example.hellotoggle.glass` が自動起動して `Hello World` が表示される
4. グラスのジェスチャ操作 (タップ / スワイプ) がスマホのログタイムラインに反映される

接続を切るときはスマホで `[接続停止]`、または通知から戻ってボタン操作。再認証したいときは `[再認証]` で token を破棄。

## トラブルシューティング

- **Hi Rokid 行が `not installed`**: グローバル版 (`com.rokid.sprite.global.aiapp`) がインストールされていない、または `phone/app/src/main/AndroidManifest.xml` の `<queries>` 漏れ
- **`[接続開始]` 後にグラス画面が変わらない**: token 期限切れ、もしくは Hi Rokid Service がまだグラスとペアリング状態になっていない。Hi Rokid アプリ側でペアリングを再確認、必要なら `[再認証]` → `[認証]`
- **グラス側が `Phone not connected` のまま**: phone 側 Foreground Service が起動していない、または phone がサイレント kill された (例: `am force-stop`)。スマホで `[接続開始]` を押し直す
- **ビルド時に `Could not resolve com.example.cxrglobal:lib`**: CxrGlobal リポを並列に clone していない、または `phone/settings.gradle.kts` の `includeBuild` パスが合っていない

## 既知の制限

- BT 物理切断 → 復帰時の自動再接続なし (手動で `[接続停止]` → `[接続開始]`)
- token 期限切れの自動検出なし
- Hi Rokid 未インストール時のストア誘導 UI なし
- グラス用 APK の自動デプロイは未実装 (手動 `adb install` 想定)
