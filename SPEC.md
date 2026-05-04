# HelloToggleCxrl — 仕様書

スマホ ⇄ Rokid グラス を CXRL で繋ぎ、**グラス側のサイドタップで Hello World 表示/非表示をトグル**、その操作ログをスマホ側で確認できる最小サンプルアプリ。

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
| 主な責務 | 初回認証 / 接続維持 / ログ表示 | UI 描画 / タップ入力 / トグル状態管理 |
| UI のメイン | 接続状態 + トグル操作ログのタイムライン | "Hello World" or "未接続" の単一画面 |
| 状態の真実点 | (持たない、ログのみ) | **トグル ON/OFF を保持** |
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
2. **接続あり**: 中央に "Hello World" (もしくは非表示) の現在状態が表示される
3. **接続なし**: 中央に "Phone not connected" を赤色で常時表示
4. グラス側面を**シングルタップ** → 表示/非表示トグル
5. グラスはトグルと同時に `rk_custom_key` チャンネルでスマホへイベント送信
6. スマホ側 ConnectionService が受信 → 通知バーの常駐通知に最新ログ反映 + Activity 起動中ならログ画面に追記

### 3.3 復帰フロー

- スマホ再起動: ユーザーが Companion を起動するまで Service は復帰しない (Boot 完了起動はやらない、明示起動のみ)
- 接続が一時切断 → 自動再接続 (`CXRLink` の reconnect、もしくは `connect(token)` をバックオフ付きで再試行)
- token 期限切れ → 認証画面へ誘導 (Service は通知タップで Activity 復帰)

## 4. メッセージング仕様

### 4.1 チャンネル

| チャンネル名 | 方向 | 用途 |
| --- | --- | --- |
| `rk_custom_client` | スマホ → グラス | (今回は未使用、将来拡張余地として確保) |
| `rk_custom_key` | グラス → スマホ | トグルイベント送信 |

> サンプルの命名慣行をそのまま踏襲。送信側 API: スマホ `cxrLink.sendCustomCmd(key, bytes)`、グラス `cxrBridge.sendMessage(key, bytes)`。

### 4.2 ペイロード (Caps バイナリ)

**グラス → スマホ (`rk_custom_key`)**

```
Caps {
    write("event")            // 種別タグ ("toggle")
    write("toggle")
    write("visible")          // フィールド名
    write(<Boolean as Int>)   // 1 = 表示中, 0 = 非表示
    write("ts")
    write(<epoch millis>)     // long
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
   - 各行: `HH:mm:ss.SSS  visible=true`
   - "Clear" ボタンで空に

### 5.2 グラス Glass

**MainActivity** (Compose、黒背景)

- 接続あり & トグル ON: 中央に大きく **"Hello World"** (緑 #00AF00、サンプルの `GreenText` 流用)
- 接続あり & トグル OFF: 何も表示しない (黒画面)
- 接続なし: 中央に **"Phone not connected"** (赤 #C04040、点滅なし)
- 右下小さく現在の接続状態テキスト (デバッグ用、release ではフラグで非表示可)

## 6. ステートマシン

### 6.1 グラス側 (UI 状態)

```
            tap (CLICK)
   HIDDEN  ───────────►  VISIBLE
      ▲                     │
      └─────── tap ─────────┘

  どちらの状態でも、phone が disconnect になると → DISCONNECTED 表示にオーバレイ
  reconnect すると元の HIDDEN/VISIBLE に戻る (状態は保持)
```

- 真実点はグラス側 ViewModel の `visible: StateFlow<Boolean>`
- タップ受信 → `visible = !visible` → スマホへ送信

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
- `foregroundServiceType`: **`connectedDevice`** (`0x00000010`)
  - Hi Rokid 本体のフォアグラウンドサービスと同種別 (今日 dumpsys で確認した型)
- 通知チャンネル: `cxrl_connection` (低優先、消音)
- 通知内容: タイトル "HelloToggleCxrl 接続中"、本文に最新ログ (例: `12:34:56  visible=true`)、タップで Activity 復帰
- 通知は `NO_CLEAR | FOREGROUND_SERVICE`、ユーザーがスワイプで消せない

### 7.2 マニフェスト

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".connection.ConnectionService"
    android:foregroundServiceType="connectedDevice"
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

## 10. 実装順序 (推奨)

1. グラス側 `MainActivity` に静的な "Hello World" (緑) と "Phone not connected" (赤) のスイッチ表示を作る
2. グラス側で `KeyReceiver` を組み込み、CLICK でローカルにトグル
3. スマホ側に Foreground Service の雛形 (token なし、接続もしないが常駐するだけ) を作って通知が出ることを確認
4. スマホ側 `MainActivity` に認証フローを移植 (`cxrlsample101/MainViewModel.kt` 参照)、token を `TokenStore` に保存
5. Service が token を使って `CXRLink.connect()` する経路を作る、connect 成功で通知本文を更新
6. グラス側 `CXRServiceBridge` をセットアップ、`StatusListener` でスマホ接続状態をフックして UI を切替
7. グラス側からトグル時に `sendMessage("rk_custom_key", caps)` を送出
8. スマホ側 Service の `ICustomCmdCbk.onCustomCmdResult` で受けて、Activity (起動中なら) と通知へログを伝搬
9. 自動再接続・token 破棄・エラー UI を仕上げ

## 11. オープン課題

- **token の有効期限**: サンプルは検証していない。実機で長期保持できるか確認が必要。期限がある場合、無効化判定の API があるか調査
- **グラス側 APK の自動デプロイ経路**: cxrlsample101 が `/sdcard/DCIM/Rokid/cxrL.apk` を Hi Rokid に渡してインストールさせている流れをそのまま踏襲できるか、別ルートが必要か未確認
- **`foregroundServiceType=connectedDevice`** が CXRL 経由通信に正しいか (Bluetooth/USB 等の物理接続を伴う想定の type なので、ガイドライン上は `dataSync` のほうが適合する可能性あり)。Play 配布しないならどちらでも動くが要確認
- **Boot 起動**: 仕様上は対象外。ユーザーから要望が出たら `RECEIVE_BOOT_COMPLETED` + `BootReceiver` を追加する想定
