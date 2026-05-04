# HelloToggleCxrl

**グラス主導アプリのテンプレート**。Rokid RG-glasses (Hi Rokid OS) を主役にして、スマホは「最初に 1 回だけ触って後は忘れていい companion」として裏方に徹する構成のサンプル。

## このプロジェクトの狙い

スマホを意識せずにグラスアプリを使えるテンプレートを目指す。

- **初回セットアップ** (ユーザーがスマホを触るのはここだけ): Hi Rokid 認証 → `[接続開始]` を 1 回押す
- **以降**: スマホは Foreground Service で CXRL 接続を常駐させ続ける。アプリを閉じてもバックグラウンドで生きてる
- **通常運用**: ユーザーはグラスだけを使う。グラスの操作ログは自動でスマホ側に蓄積されていく
- スマホアプリは「接続の番人 + データの受け皿」。UI として意識する必要はない

本リポジトリの機能自体は「Hello World 表示のトグル / メッセージ切替」だけの最小サンプルだが、構成要素 (CXR セッション、application-level handshake、heartbeat、状態同期、Foreground Service 常駐) はそのままグラス主導の他アプリにも流用できることを意図している。

## 端末構成

| | phone | glass |
|---|---|---|
| 端末 | Pixel 8 (Android 14+) | Rokid RG-glasses (Hi Rokid OS / Android 12) |
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

ジェスチャ→キーコードの対応根拠 / Hi Rokid OS の予約ジェスチャ一覧は `GlassGestureProbe/GLASS_GESTURES.md` (別 repo) を参照。

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

## 必要な依存

- Android Studio (Kotlin 2.2.10 / AGP 9.2.0 / Compose BOM 2026.02.01)
- **phone**:
  - `com.example.cxrglobal:lib` (隣接ディレクトリの `../CxrGlobal` を `includeBuild`)
  - `com.rokid.cxr:client-l:1.0.1` (Caps シリアライザ、Rokid maven)
  - `androidx.security:security-crypto:1.1.0-alpha06`
- **glass**:
  - `com.rokid.cxr:cxr-service-bridge:1.0-20260212.103714-88` (Rokid maven)

## ビルド・実行

```bash
export JAVA_HOME=/path/to/android-studio/jbr

# phone
cd phone && ./gradlew installDebug

# glass
cd glass && ./gradlew installDebug
```

事前条件:
- スマホに Hi Rokid (`com.rokid.sprite.global.aiapp`) インストール済 + グラスとペアリング済
- グラスは Hi Rokid OS が稼働中

実行手順:
1. スマホでアプリ起動 → `[認証]` (初回のみ、Hi Rokid の認証ダイアログが出る)
2. `[接続開始]` → 通知バーに Foreground Service の通知が出る
3. グラス側に `com.example.hellotoggle.glass` が自動起動して `Hello World` が表示される
4. グラスのジェスチャ操作がスマホのログタイムラインに反映される

## 既知の制限

- BT 物理切断 → 復帰時の自動再接続なし (手動で `[接続停止]` → `[接続開始]`)
- token 期限切れの自動検出なし
- Hi Rokid 未インストール時のストア誘導 UI なし
- グラス用 APK の自動デプロイは未実装 (手動 `adb install` 想定)
