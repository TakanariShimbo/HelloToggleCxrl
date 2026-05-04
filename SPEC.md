# HelloToggleCxrl — 仕様書

スマホ ⇄ Rokid グラス を CXRL で繋ぎ、**グラス側のジェスチャで挨拶メッセージを操作** (タップで表示/非表示、スワイプで次/前のメッセージ)、その操作ログをスマホ側で確認できる最小サンプルアプリ。

## 0. 現状 (2026-05-04 時点)

実装済み:
- **グラス側**:
  - Compose スケルトン (黒背景、緑テキスト)
  - ジェスチャハンドリング (`dispatchKeyEvent` で 3 種を捕捉)
    - タップ (`KEYCODE_ENTER`) → 表示/非表示トグル
    - 前スワイプ (`KEYCODE_DPAD_RIGHT`) → 次メッセージ
    - 後スワイプ (`KEYCODE_DPAD_LEFT`) → 前メッセージ
    - メッセージ: `Hello World` / `こんにちは` / `Bonjour` / `안녕`
    - DPAD_UP/DOWN (副キー) は消費して無視、BACK は通して閉じる
- **スマホ側**:
  - Compose UI: 接続状態カード + 認証/接続ボタン + ログタイムライン
  - Hi Rokid インストール検出 (`PackageManager` + `<queries>`)
  - `ConnectionService` Foreground Service (`dataSync` 型) — 常駐通知、`POST_NOTIFICATIONS` 要求、`StateFlow<Boolean>` で UI と連動
  - 認証フロー: `AuthorizationHelper` 経由で Hi Rokid に auth、token を `TokenStore` (EncryptedSharedPreferences) で永続化

未実装:
- CXRLink 接続 (token はあるが `connect()` していない)
- グラス側 `CXRServiceBridge` 統合
- メッセージ送受信
- 接続状態に応じた UI 切替 ("Phone not connected" 表示)

> ジェスチャ→キーコードの対応根拠は `../GlassGestureProbe/GLASS_GESTURES.md` 参照。実機の `/system/usr/keylayout/Generic.kl` で確認済み。

## 1. 概要

- **目的**: グラス主導の最小双方向通信サンプル。CXRL の認証・CUSTOMAPP セッション・カスタムメッセージング・ジェスチャ入力・バックグラウンド常駐を一通り通す。
- **構成**: スマホ用 APK (1) + グラス用 APK (1) の合計 2 アプリ。CXRL CUSTOMAPP の作法に従い、**グラス用 APK はスマホ経由でグラスへインジェクトされる**前提 (`/sdcard/DCIM/Rokid/cxrL.apk` からインストール、cxrlsample101 の慣行どおり)。
- **参照プロジェクト**:
  - スマホ側 ベース: `../cxrlsample101` (`com.rokid.cxrlsample`)
  - グラス側 ベース: `../sSDKSampleforCXR` (`com.rokid.cxrswithcxrl`)

## 2. 役割分担

| | スマホ側 (Companion) | グラス側 (Glass) |
| --- | --- | --- |
| パッケージ案 | `com.example.hellotoggle.phone` | `com.example.hellotoggle.glass` |
| SDK | `CXRLink` + `AuthorizationHelper` | `CXRServiceBridge` |
| 主な責務 | 初回認証 / 接続維持 / ログ表示 | UI 描画 / ジェスチャ入力 / 表示状態管理 |
| UI のメイン | 接続状態 + 操作ログのタイムライン | 現在のメッセージ or "未接続" の単一画面 |
| 状態の真実点 | (持たない、ログのみ) | **可視/非可視 + メッセージ index を保持** |
| バックグラウンド | **Foreground Service で常駐** | 通常 Activity (Hi Rokid 管理下なので常駐は CXR ランタイム任せ) |

> 真実点をグラス側に置く理由: ユーザー操作はグラスで起こり、UI もグラスで完結するため。スマホはあくまで観測役。

## 3. ユーザーフロー

### 3.1 初回セットアップ (スマホで一度だけ)

1. ユーザーがスマホで HelloToggleCxrl (Companion) を起動
2. Hi Rokid (`com.rokid.sprite.global.aiapp`) インストール確認
3. 「認証」ボタン → `AuthorizationHelper.requestAuthorization(activity, REQ)`
4. 結果を `AuthorizationHelper.parseAuthorizationResult` で受けて token 取得 → **EncryptedSharedPreferences に永続化**
5. 「接続開始」ボタン → ConnectionService (Foreground Service) を起動
6. Service が `CXRLink` を生成 → `configCXRSession(CUSTOMAPP, "com.example.hellotoggle.glass")` → `connect(token)`
7. 接続確立後、Hi Rokid 経由で**グラス側 APK が自動デプロイ + 起動**(cxrlsample101 と同じ流れ)
8. ユーザーはスマホ画面を閉じてよい (Activity destroy しても Service 継続)

### 3.2 通常運用

1. グラスを掛ける → グラスにアプリが立ち上がる
2. **接続あり**: 中央に現在のメッセージ (例 "Hello World") を表示。非表示状態のときは黒画面
3. **接続なし**: 中央に "Phone not connected" を赤色で常時表示
4. グラス側面を以下のジェスチャで操作:
   - **シングルタップ** (`KEYCODE_ENTER`) → 表示/非表示トグル
   - **前スワイプ** (`KEYCODE_DPAD_RIGHT`) → 次のメッセージへ (リストの末尾でループ)
   - **後スワイプ** (`KEYCODE_DPAD_LEFT`) → 前のメッセージへ
5. 各操作のたびにグラスは `rk_custom_key` チャンネルでスマホへ状態スナップショット送信
6. スマホ側 ConnectionService が受信 → 通知バーの常駐通知に最新ログ反映 + Activity 起動中ならログ画面に追記
7. ダブルタップ (BACK) でグラス側アプリを閉じる (アプリ側で消費しないので system が処理)

> ジェスチャの根拠は `GlassGestureProbe/GLASS_GESTURES.md` の「アプリで使える操作 (3 種、推奨)」表を参照。それ以外 (1本指長押し / 2本指系 / カメラボタン) は Hi Rokid OS が消費するためアプリでは使わない。

### 3.3 復帰フロー

- スマホ再起動: ユーザーが Companion を起動するまで Service は復帰しない (Boot 完了起動はやらない、明示起動のみ)
- 接続が一時切断 → 自動再接続 (`CXRLink` の reconnect、もしくは `connect(token)` をバックオフ付きで再試行)
- token 期限切れ → 認証画面へ誘導 (Service は通知タップで Activity 復帰)

## 4. メッセージング仕様

### 4.1 チャンネル

| チャンネル名 | 方向 | 用途 |
| --- | --- | --- |
| `rk_custom_client` | スマホ → グラス | (今回は未使用、将来拡張余地として確保) |
| `rk_custom_key` | グラス → スマホ | ジェスチャ操作の状態スナップショット送信 |

> サンプルの命名慣行をそのまま踏襲。送信側 API: スマホ `cxrLink.sendCustomCmd(key, bytes)`、グラス `cxrBridge.sendMessage(key, bytes)`。

### 4.2 ペイロード (Caps バイナリ)

**グラス → スマホ (`rk_custom_key`)**

ジェスチャ発生のたびに「直後の状態スナップショット」を送る。イベント種別 (tap/swipe_next/swipe_prev) も付け、スマホ側ログを richer にする。

```
Caps {
    write("event")            // 種別タグ
    write(<event>)            // "tap" | "swipe_next" | "swipe_prev"
    write("visible")
    write(<Boolean as Int>)   // 1 = 表示中, 0 = 非表示
    write("index")
    write(<Int>)              // 0..3 (現在のメッセージ index)
    write("message")
    write(<String>)            // 現在のメッセージ文字列 (デバッグ容易化)
    write("ts")
    write(<Long>)             // epoch millis
}
```

スマホ側 `Caps.fromBytes(payload)` → `at(i)` を順に読む (cxrlsample101 の `CustomCmdViewModel` と同じ作法)。

### 4.3 接続状態の検知 (グラス側)

- `cxrBridge.setStatusListener(...)` の `onConnected` / `onDisconnected` / `onConnecting` を ViewModel の StateFlow に反映
- StateFlow を Compose で購読 → 切断時に "Phone not connected" 表示に切替

## 5. 画面仕様

### 5.1 スマホ Companion

**MainActivity** (起動時の単一画面、Compose)

セクション順:

1. **接続状態カード**
   - "Hi Rokid: installed/not installed"
   - "Authorization: yes/no"
   - "Connection: connected / connecting / disconnected"
2. **操作ボタン**
   - `[認証]` (未認証時のみ活性)
   - `[接続開始]` / `[接続停止]` (Service の起動/停止)
   - `[再認証]` (token 破棄)
3. **ログタイムライン** (LazyColumn)
   - 最大 200 件、新しいものが上
   - 各行: `HH:mm:ss.SSS  [tap] visible=true index=0 "Hello World"`
   - "Clear" ボタンで空に

### 5.2 グラス Glass

**MainActivity** (Compose、黒背景)

- 接続あり & 可視: 中央に大きく現在の **メッセージ** (緑 #00AF00、`MESSAGES[index]`)
- 接続あり & 非可視: 何も表示しない (黒画面)
- 接続なし: 中央に **"Phone not connected"** (赤 #C04040、点滅なし)
- 右下小さく現在の接続状態テキスト (デバッグ用、release ではフラグで非表示可)
- メッセージリスト: `["Hello World", "こんにちは", "Bonjour", "안녕"]` (将来は phone から push する余地あり)

## 6. ステートマシン

### 6.1 グラス側 (UI 状態)

ローカル状態は `(visible: Boolean, index: Int)` の 2 つ。各ジェスチャがそれぞれ片方を変える。

```
   tap          : visible = !visible
   swipe_next   : index   = (index + 1) mod N
   swipe_prev   : index   = (index - 1) mod N

  どちらの状態でも、phone が disconnect になると → DISCONNECTED 表示にオーバレイ
  reconnect すると元の (visible, index) に戻る (状態は保持)
```

- 真実点はグラス側 ViewModel の `state: StateFlow<UiState>` (`visible`, `index`)
- 状態変化のたびにスマホへ snapshot 送信

### 6.2 スマホ側 (Service 状態)

```
  IDLE ── startService ──► AUTHENTICATED ── connect ──► CONNECTED
                                  ▲                        │
                                  └──── disconnect ────────┘
                                      (auto-retry w/ backoff)
```

## 7. バックグラウンド要件 (スマホ側)

CXRL 接続を Activity から切り離して常駐させるため、**Foreground Service** を新設する。サンプル (`cxrlsample101`) は Application シングルトン (`sharedCxrLink`) のみで Activity destroy 時の挙動が未定義なので、ここを明確化するのが本仕様の主眼の一つ。

### 7.1 Service 仕様

- クラス: `ConnectionService : Service`
- 種別: Foreground Service (`startForeground` 必須)
- `foregroundServiceType`: **`dataSync`**
  - 当初 `connectedDevice` を試したが、Android 14+ の制約で BLUETOOTH_* / WIFI_* / USB 等の物理接続系 permission を最低 1 つ要求される (我々は使わない)。CXR は実態としてデバイス間データ同期なので `dataSync` で適合
- 通知チャンネル: `cxrl_connection` (低優先、消音)
- 通知内容: タイトル "HelloToggleCxrl 接続中"、本文に最新ログ (例: `12:34:56  visible=true`)、タップで Activity 復帰
- 通知は `NO_CLEAR | FOREGROUND_SERVICE`、ユーザーがスワイプで消せない

### 7.2 マニフェスト

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".ConnectionService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

### 7.3 ライフサイクル

| トリガー | 挙動 |
| --- | --- |
| Activity の `[接続開始]` | `startForegroundService(intent)` → Service 内で `CXRLink` 生成 + `connect(token)` |
| Activity destroy | Service は継続 |
| Service onDestroy | `cxrLink.disconnect()` → `cxrLink.release()` |
| ユーザーが通知の `[切断]` アクション | `stopSelf()` |
| OS が low-memory で kill | `START_STICKY` で再生成、token があれば自動再接続 |

### 7.4 token の扱い

- 永続化: `EncryptedSharedPreferences` (key: `cxrl_token`)
- Service 起動時に読む → 無ければ Activity に「要認証」状態を通知して終了
- 接続失敗で auth エラー系コードが返ったら token を破棄して同様

## 8. エッジケース

- **グラス未装着 / Hi Rokid 未起動**: スマホ側 `connect` が pending → 一定時間 (例: 30s) で timeout、Service は再試行を継続
- **タップ連打**: 100ms 未満は無視 (グラス側でデバウンス)
- **トグル送信中に切断**: メッセージは破棄。グラスのローカル状態は変えてしまって OK (UI 整合優先)。再接続後の同期は将来課題
- **複数のスマホでの再認証**: token は端末固有、Hi Rokid 側のペアリング作法に従う
- **権限拒否 (POST_NOTIFICATIONS)**: 通知は出ないが Service は動く。Activity 上に警告表示

## 9. ディレクトリ構成 (予定)

```
HelloToggleCxrl/
├── SPEC.md                    ← 本ファイル
├── phone/                     ← スマホ用 Android プロジェクト (Studio で初期化)
│   └── app/...
│       ├── MainActivity.kt
│       ├── connection/
│       │   ├── ConnectionService.kt
│       │   ├── ConnectionManager.kt   (CXRLink ラッパ)
│       │   └── TokenStore.kt
│       └── ui/
│           ├── StatusCard.kt
│           └── LogTimeline.kt
└── glass/                     ← グラス用 Android プロジェクト (Studio で初期化)
    └── app/...
        ├── MainActivity.kt
        ├── bridge/
        │   ├── BridgeManager.kt       (CXRServiceBridge ラッパ)
        │   └── KeyReceiver.kt         (sSDKSampleforCXR から流用)
        └── ui/
            ├── HelloText.kt
            └── DisconnectedOverlay.kt
```

> Android プロジェクト本体は `./gradlew` 単独初期化が公式に存在しないため、**Android Studio の New Project から phone/ glass/ それぞれ作成する** (CLAUDE.md の方針)。

## 10. 実装順序

### 完了済み
1. ✅ グラス側 `MainActivity` の Compose スケルトン (黒背景、緑テキストで "Hello World")
2. ✅ グラス側ジェスチャハンドリング (tap / swipe_next / swipe_prev → ローカル状態更新、`dispatchKeyEvent` で 3 種を捕捉、BACK は通す)
3. ✅ スマホ側 UI スケルトン: 接続状態カード + 認証/接続ボタン (ダミー) + ログタイムライン + Hi Rokid インストール検出
4. ✅ Foreground Service の雛形 (`ConnectionService`): token・接続なしで `dataSync` 型 FGS として起動、常駐通知、`POST_NOTIFICATIONS` ランタイム権限要求
5. ✅ スマホ側認証フロー: `AuthorizationHelper` で Hi Rokid に auth リクエスト、`onActivityResult` (deprecated) で結果受け、`TokenStore` (EncryptedSharedPreferences) で token 永続化、再起動後も復元

### これから
6. **CXRLink 接続**: Service が token を使って `configCXRSession(CUSTOMAPP, glassPkg)` → `connect(token)`。接続成功で通知本文を更新
7. **グラス側 CXRServiceBridge**: `setStatusListener` でスマホ接続状態を StateFlow に流す → 切断時 "Phone not connected" 表示にオーバレイ
8. **メッセージ送信**: グラスの状態変化のたびに `sendMessage("rk_custom_key", caps)` を送出 (Caps の組み立ては §4.2)
9. **メッセージ受信**: スマホ側 Service の `ICustomCmdCbk.onCustomCmdResult` でデコード、Activity (起動中なら) と通知へログを伝搬
10. **仕上げ**: 自動再接続・token 破棄・エラー UI

## 11. オープン課題

- **token の有効期限**: サンプルは検証していない。実機で長期保持できるか確認が必要。期限がある場合、無効化判定の API があるか調査
- **グラス側 APK の自動デプロイ経路**: cxrlsample101 が `/sdcard/DCIM/Rokid/cxrL.apk` を Hi Rokid に渡してインストールさせている流れをそのまま踏襲できるか、別ルートが必要か未確認
- ~~`foregroundServiceType` の選択~~ → 解決: `dataSync` に決定 (`connectedDevice` は Android 14+ で BLUETOOTH/WIFI/USB 系 permission を要求されるため不適合)
- **Boot 起動**: 仕様上は対象外。ユーザーから要望が出たら `RECEIVE_BOOT_COMPLETED` + `BootReceiver` を追加する想定
