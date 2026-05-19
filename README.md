# BCD's HUD

Minecraft 1.21.x (Paper) 向けのHUD表示プラグインです。  
TextDisplayエンティティを使って、プレイヤーの画面上に自由な位置・デザインでテキストを表示できます。

---

## 特徴

- プレイヤーに追従する **Personal HUD** と、座標・エンティティに固定する **Shared HUD** の2種類
- 位置・角度・サイズ・透明度・背景色をリアルタイムに変更可能
- プリセット9箇所 + 完全カスタム位置に対応
- 期限付き・条件付きでの登録と自動削除
- 複数プレイヤーへのHUD共有・観戦対応

---

## 導入方法

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.github.nite2232</groupId>
        <artifactId>BCDs-HUD</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### plugin.yml への依存追加

```yaml
depend:
  - BCDs_HUD
```

---

## 基本概念

BCD's HUD には2つの重要な概念があります。

### HudSlot（スロット）

**どこに・どんな見た目で表示するか**を定義するオブジェクトです。  
位置・サイズ・回転・透明度・背景色などをまとめて持ちます。

```java
// プリセットを使う
HudSlot slot = HudSlot.topLeft();

// 完全カスタム
HudSlot slot = HudSlot.custom()
        .offset(5f, 7f, 10f)   // 右5, 上7, 距離10
        .scale(2.0f)
        .rotation(0f, 0f, 15f) // yaw, pitch, roll
        .opacity(200)
        .backgroundTransparent();
```

スロットのパラメータは後から変更でき、次の `updateAll()` で即座に反映されます。

```java
slot.opacity = 128;    // 半透明に
slot.scale   = 2.0f;   // 拡大
slot.offsetUp += 0.1f; // 毎tickじわじわ上昇
```

### HudTextProvider（プロバイダ）

**何を表示するか**を定義するインターフェースです。  
`getHudText()` が毎 tick 呼ばれ、返した Component がそのままHUDに表示されます。

```java
public class HpProvider implements HudTextProvider {

    private final Player player;

    public HpProvider(Player player) {
        this.player = player;
    }

    @Override
    public Component getHudText() {
        double hp = player.getHealth() / 2.0;
        return Component.text("❤ " + String.format("%.1f", hp), NamedTextColor.RED);
    }

    @Override
    public int getPriority() {
        return HudPriority.NORMAL; // 同じスロットに複数登録したときの表示順
    }
}
```

固定テキストなら `SimpleHudProvider` を使うとクラスを作らずに済みます。

```java
HudTextProvider provider = SimpleHudProvider.of(
    Component.text("緊急事態！", NamedTextColor.RED)
);

// ラムダも使える
HudTextProvider provider = SimpleHudProvider.of(
    () -> Component.text("時刻: " + LocalTime.now())
);
```

### スロットとプロバイダの関係

- HudSlot  = 「どこに・どんな見た目で」
- Provider = 「何を表示するか」
- 1つのスロットに複数のプロバイダを登録できます。
- priority の小さい順に上から表示されます。

---

## セットアップ

```java
public class MyPlugin extends JavaPlugin {

    private HudManager hudManager;

    @Override
    public void onEnable() {
        hudManager = new HudManager(this);

        // 毎tick更新（必須）
        Bukkit.getScheduler().runTaskTimer(this, hudManager::updateAll, 0L, 1L);
    }

    @Override
    public void onDisable() {
        hudManager.hideAll(); // 必須
    }
}
```

---

## Personal HUD

プレイヤーに追従するHUDです。そのプレイヤー自身の情報表示に使います。

### 基本的な表示

```java
// プロバイダを登録してから showPersonalHud を呼ぶ
hudManager.registerPersonalProvider(player, HudSlot.topLeft(), new HpProvider(player));
hudManager.showPersonalHud(player);

// showPersonalHud後でも登録できる（次のupdateAllで自動表示）
hudManager.registerPersonalProvider(player, HudSlot.bottomRight(), new MapProvider(player));
```

### プレイヤー退出時

```java
@EventHandler
public void onQuit(PlayerQuitEvent e) {
    hudManager.hidePersonalHud(e.getPlayer());
}
```

### 死亡・リスポーン時（一時的な非表示）

```java
// 死亡時: エンティティだけ消す（プロバイダは残る）
hudManager.hidePersonalHudEntities(player);

// リスポーン時: そのまま再表示
hudManager.showPersonalHud(player);
```

### 名前付きスロット（別の場所から同じスロットに追加）

```java
// 複数の場所から "ability_hud" に追加できる
hudManager.registerPersonalProviderByName(
    player, "ability_hud", HudSlot.topLeft(), abilityA
);
hudManager.registerPersonalProviderByName(
    player, "ability_hud", HudSlot.topLeft(), abilityB
);
// → 同じ topLeft スロットに両方追加される
```

### プロバイダの削除

```java
// インスタンスで削除
hudManager.unregisterPersonalProvider(player, slot, myProvider);

// クラスで一括削除
hudManager.unregisterPersonalProvidersByClass(player, slot, AbilityProvider.class);

// スロットごと削除
hudManager.clearPersonalProviders(player, slot);
```

---

## Shared HUD

座標またはエンティティに固定されるHUDです。  
ボスのHP・ロビーの看板・チームスコアなど、ゲーム全体の情報表示に使います。

### 固定座標に表示（全員に見える）

```java
HudSlot slot = HudSlot.topCenter().name("lobby_info");
hudManager.registerSharedProvider(slot, new LobbyInfoProvider(), lobbyLocation);
// → 次の updateAll() で自動表示
```

### エンティティに追従（全員に見える）

```java
HudSlot slot = HudSlot.topCenter().name("boss_hp");
hudManager.registerSharedProvider(slot, new BossHpProvider(boss), bossEntity);
```

### 特定プレイヤーだけに見せる

```java
hudManager.registerSharedProvider(
    slot, provider, location, Set.of(playerA, playerB)
);

// 後から追加・削除
hudManager.addSharedViewer(slot, playerC);
hudManager.removeSharedViewer(slot, playerA);
```

### 一時的な非表示と再表示

```java
// エンティティだけ消す（プロバイダ・viewer設定は残る）
hudManager.hideSharedSlotEntities(slot);

// 再表示（anchorは保持されているので引数不要）
hudManager.showSharedSlot(slot);

// 完全削除
hudManager.hideSharedSlot(slot);
```

---

## 他プレイヤーへの共有（Personal HUD）

```java
// playerA のHUD全スロットを playerB にも見せる（観戦など）
hudManager.addPersonalHudViewer(playerA, playerB);

// 特定スロットだけ見せる
hudManager.addPersonalHudViewer(playerA, hpSlot, playerB);

// 非表示に戻す
hudManager.removePersonalHudViewer(playerA, playerB);
```

---

## 時限式・条件式

### 時限式（tick指定）

```java
// 「緊急事態！」を5秒だけ追加表示（既存の表示は消えない）
hudManager.registerPersonalTextTemporary(
    player, slot,
    SimpleHudProvider.of(Component.text("緊急事態！", NamedTextColor.RED)),
    100L
);

// スロットごと5秒後に削除
hudManager.registerPersonalSlotTemporary(
    player,
    HudSlot.topCenter().name("alert"),
    SimpleHudProvider.of(Component.text("警告！", NamedTextColor.YELLOW)),
    100L
);

// Shared: 3秒だけ全員に表示して消す
hudManager.registerSharedSlotTemporary(
    HudSlot.middleCenter().name("game_start"),
    SimpleHudProvider.of(Component.text("ゲーム開始！", NamedTextColor.GOLD)),
    someLocation,
    60L
);
```

### 条件式（条件がfalseになったら削除）

```java
// 範囲内にいる間だけ表示
hudManager.registerPersonalTextWhile(
    player, slot, provider,
    () -> player.getLocation().distanceSquared(origin) <= range * range
);

// アビリティが有効な間だけ表示
hudManager.registerPersonalSlotWhile(
    player, slot, provider,
    () -> ability.isActive()
);

// Shared: エンティティが生きている間だけ表示
hudManager.registerSharedSlotWhile(
    slot, provider, bossEntity,
    () -> !bossEntity.isDead()
);
```

---

## スロットのリアルタイム変更

スロットはフィールドとして保持しておき、どこからでも変更できます。

```java
private final HudSlot slot = HudSlot.topLeft();

// 任意のタイミングで変更（updateAllで自動反映）
slot.opacity = 128;                                      // 半透明
slot.scale   = 2.0f;                                     // 拡大
slot.backgroundColor = Color.fromARGB(128, 0, 0, 0);    // 半透明の黒背景
slot.backgroundColor = null;                              // デフォルト背景に戻す
slot.offsetUp += 0.1f;                                   // じわじわ上昇
slot.rotationRoll += 5f;                                 // 毎tickくるくる
```

---

## HudSlot プリセット一覧

- TOP_LEFT
- TOP_CENTER
- TOP_RIGHT
- MIDDLE_LEFT
- MIDDLE_CENTER
- MIDDLE_RIGHT
- BOTTOM_LEFT
- BOTTOM_CENTER
- BOTTOM_RIGHT

```java
HudSlot.topLeft()
HudSlot.topCenter()
HudSlot.topRight()
HudSlot.middleLeft()
HudSlot.middleCenter()
HudSlot.middleRight()
HudSlot.bottomLeft()
HudSlot.bottomCenter()
HudSlot.bottomRight()
HudSlot.custom() // 完全カスタム
```

---

## HudSlot 設定一覧

| フィールド | 説明 | デフォルト |
|---|---|---|
| `offsetRight` | 右方向オフセット（負で左） | プリセット依存 |
| `offsetUp` | 上方向オフセット（負で下） | プリセット依存 |
| `offsetForward` | 奥行き（距離） | `10f` |
| `scale` | 表示スケール | `1.5f` |
| `rotationYaw` | Y軸回転（度数） | `0f` |
| `rotationPitch` | X軸回転（度数） | `0f` |
| `rotationRoll` | Z軸回転（度数） | `0f` |
| `opacity` | 不透明度（0〜255） | `255` |
| `lineWidth` | テキストの折り返し幅（px） | `10000` |
| `seeThrough` | 壁越し表示 | `false` |
| `verticalGrowth` | 縦方向の伸び（UP/DOWN/CENTER） | プリセット依存 |
| `horizontalGrowth` | 横方向の伸び（LEFT/RIGHT/CENTER） | プリセット依存 |
| `textAlignment` | 文字揃え（LEFT/CENTER/RIGHT） | `LEFT` |
| `backgroundColor` | 背景色（nullでデフォルト） | `null` |

---

## API 要件

- Java 21+
- Paper 1.21.x