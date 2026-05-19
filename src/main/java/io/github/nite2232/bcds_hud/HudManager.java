package io.github.nite2232.bcds_hud;

import io.github.nite2232.bcds_hud.api.HudSlot;
import io.github.nite2232.bcds_hud.api.HudTextProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.stream.Collectors;

/**
 * プレイヤーのHUDを管理するクラス。
 *
 * <p>Display エンティティを使って画面上にテキストを表示します。
 * HUDには2種類あります：</p>
 * <ul>
 *   <li><b>Personal HUD</b>: 特定のプレイヤーに追従するHUD。そのプレイヤー自身の情報表示に適しています。</li>
 *   <li><b>Shared HUD</b>: 固定座標またはエンティティに追従するHUD。ボスのHPやロビーの看板など、ゲーム全体の情報表示に適しています。</li>
 * </ul>
 *
 * <h2>基本的な使い方</h2>
 * <pre>{@code
 * // 1. HudManagerを作成（onEnableで）
 * HudManager hudManager = BCDs_HUD.getPlugin().getHudManager();
 *
 * // 2. プレイヤー参加時にHUDを表示
 * hudManager.showPersonalHud(player);
 * hudManager.registerPersonalProvider(player, HudSlot.topLeft(), myProvider);
 *
 * // 3. プレイヤー退出時に必ず呼ぶ
 * hudManager.hidePersonalHud(player);
 *
 * // 4. プラグイン終了時に全HUDを削除
 * hudManager.hideAll();
 * }</pre>
 */
public class HudManager {

    private final Plugin plugin;

    // ── 共有HUD ──

    // HudSlot -> providers
    private final Map<HudSlot, List<HudTextProvider>> sharedProviders = new LinkedHashMap<>();
    // HudSlot -> viewers
    private final Map<HudSlot, Set<UUID>> sharedViewers = new LinkedHashMap<>();
    // HudSlot -> TextDisplay
    private final Map<HudSlot, TextDisplay> sharedDisplays = new LinkedHashMap<>();
    // HudSlot -> Interaction
    private final Map<HudSlot, Interaction> sharedInteractions = new LinkedHashMap<>();
    // HudSlot -> アンカー（Location or Entity）
    private final Map<HudSlot, Object> sharedAnchors = new LinkedHashMap<>();

    // ── 個人HUD（プレイヤー自身に追従するHUD） ──

    // UUID(owner) -> Set<UUID>(viewers)
    private final Map<UUID, Map<HudSlot, Set<UUID>>> personalHudViewers = new HashMap<>();
    // UUID -> (HudSlot -> providers)
    private final Map<UUID, Map<HudSlot, List<HudTextProvider>>> personalProviders = new HashMap<>();
    // UUID -> (HudSlot -> TextDisplay)
    private final Map<UUID, Map<HudSlot, TextDisplay>> personalDisplays = new HashMap<>();
    // UUID -> Interaction
    private final Map<UUID, Interaction> personalInteractions = new HashMap<>();
    // UUID -> (HudSlot -> 幅キャッシュ)
    private final Map<UUID, Map<HudSlot, Float>> personalMaxLineWidths = new HashMap<>();

    /**
     * HudManagerを作成します。
     *
     * @param plugin このHudManagerを所有するプラグイン。エンティティの表示制御に使用します。
     */
    HudManager(Plugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================
    // プロバイダ管理（Personal）
    // =========================================================

    /**
     * 複数のPersonalプロバイダを一括登録します。
     *
     * <p>すでにHUDが表示中の場合、次の {@link #updateAll()} で反映されます。
     * HUDを表示する前に登録しておくことを推奨します。</p>
     *
     * @param player 対象プレイヤー
     * @param slot   登録先スロット
     * @param list   登録するプロバイダのリスト
     */
    public void registerPersonalProviders(Player player, HudSlot slot, List<HudTextProvider> list) {
        personalProviders.computeIfAbsent(player.getUniqueId(), k -> new LinkedHashMap<>())
                .computeIfAbsent(slot, k -> new ArrayList<>())
                .addAll(list);
    }

    /**
     * Personalプロバイダを1件登録します。
     *
     * <p>すでにHUDが表示中の場合、次の {@link #updateAll()} で反映されます。</p>
     *
     * @param player   対象プレイヤー
     * @param slot     登録先スロット
     * @param provider 登録するプロバイダ
     */
    public void registerPersonalProvider(Player player, HudSlot slot, HudTextProvider provider) {
        registerPersonalProviders(player, slot, Collections.singletonList(provider));
    }

    /**
     * 指定スロットのPersonalプロバイダをすべて削除します。
     *
     * @param player 対象プレイヤー
     * @param slot   削除対象スロット
     */
    public void clearPersonalProviders(Player player, HudSlot slot) {
        Map<HudSlot, List<HudTextProvider>> map = personalProviders.get(player.getUniqueId());
        if (map != null) map.remove(slot);
    }

    /**
     * 特定のPersonalプロバイダインスタンスを削除します。
     *
     * @param player   対象プレイヤー
     * @param slot     対象スロット
     * @param provider 削除するプロバイダ
     */
    public void unregisterPersonalProvider(Player player, HudSlot slot, HudTextProvider provider) {
        getPersonalProviderList(player, slot).ifPresent(list -> list.remove(provider));
    }

    /**
     * 指定クラスに一致するPersonalプロバイダをすべて削除します。
     *
     * <p>アビリティ終了時など、クラス単位でまとめて削除したい場合に便利です。</p>
     *
     * <pre>{@code
     * hudManager.unregisterPersonalProvidersByClass(player, slot, MyAbilityProvider.class);
     * }</pre>
     *
     * @param player 対象プレイヤー
     * @param slot   対象スロット
     * @param clazz  削除するプロバイダのクラス
     */
    public void unregisterPersonalProvidersByClass(Player player, HudSlot slot,
                                           Class<? extends HudTextProvider> clazz) {
        getPersonalProviderList(player, slot).ifPresent(list -> list.removeIf(clazz::isInstance));
    }

    private Optional<List<HudTextProvider>> getPersonalProviderList(Player player, HudSlot slot) {
        Map<HudSlot, List<HudTextProvider>> map = personalProviders.get(player.getUniqueId());
        if (map == null) return Optional.empty();
        return Optional.ofNullable(map.get(slot));
    }

    /**
     * 指定プレイヤーの登録済みPersonalスロットから名前で検索します。
     *
     * @param player 対象プレイヤー
     * @param name   検索する名前（{@link HudSlot#name(String)} で設定した値）
     * @return 見つかったHudSlot。見つからなければ {@link Optional#empty()}
     */
    public Optional<HudSlot> findPersonalSlotByName(Player player, String name) {
        Map<HudSlot, List<HudTextProvider>> map = personalProviders.get(player.getUniqueId());
        if (map == null) return Optional.empty();

        return map.keySet().stream()
                .filter(slot -> slot.getName().map(name::equals).orElse(false))
                .findFirst();
    }

    /**
     * 名前でPersonalスロットを検索し、見つかればそこへ追加、なければ新規スロットを作成して登録します。
     *
     * <p>複数の場所から同じスロットにプロバイダを追加したい場合に便利です。</p>
     *
     * <pre>{@code
     * // "ability_hud" という名前のスロットがあればそこに追加、なければ topLeft に新規作成
     * hudManager.registerPersonalProviderByName(player, "ability_hud", HudSlot.topLeft(), provider);
     * }</pre>
     *
     * @param player   対象プレイヤー
     * @param name     スロット名
     * @param fallback 見つからなかった場合に使う新規スロット
     * @param provider 登録するプロバイダ
     */
    public void registerPersonalProviderByName(Player player, String name,
                                       HudSlot fallback, HudTextProvider provider) {
        HudSlot slot = findPersonalSlotByName(player, name).orElse(fallback.name(name));
        registerPersonalProvider(player, slot, provider);
    }

    // =========================================================
    // プロバイダ管理（Shared）
    // =========================================================

    /**
     * Sharedプロバイダを登録します。
     * 次のupdateAll()で自動的に表示されます。
     *
     * @param slot     登録先スロット
     * @param provider 登録するプロバイダ
     * @param anchor   表示するアンカー（Location）
     */
    public void registerSharedProvider(HudSlot slot, HudTextProvider provider, Location anchor) {
        sharedProviders.computeIfAbsent(slot, k -> new ArrayList<>()).add(provider);
        sharedAnchors.put(slot, anchor.clone());
    }

    /**
     * Sharedプロバイダを登録します。
     * 次のupdateAll()で自動的に表示されます。
     *
     * @param slot     登録先スロット
     * @param provider 登録するプロバイダ
     * @param anchor   表示するアンカー（Entity）
     */
    public void registerSharedProvider(HudSlot slot, HudTextProvider provider, Entity anchor) {
        if (anchor instanceof Player) {
            throw new IllegalArgumentException(
                    "Playerをanchorにすることはできません。" +
                            "プレイヤーに追従させる場合はregisterPersonalProvider()を使用してください。"
            );
        }
        sharedProviders.computeIfAbsent(slot, k -> new ArrayList<>()).add(provider);
        sharedAnchors.put(slot, anchor);
    }

    /**
     * Sharedプロバイダを登録します。指定したプレイヤーのみ見えます。
     * 次のupdateAll()で自動的に表示されます。
     */
    public void registerSharedProvider(HudSlot slot, HudTextProvider provider,
                                       Location anchor, Set<Player> viewers) {
        Set<UUID> uuids = viewers.stream()
                .map(Player::getUniqueId)
                .collect(Collectors.toCollection(HashSet::new));
        sharedViewers.put(slot, uuids);
        registerSharedProvider(slot, provider, anchor);
    }

    /**
     * Sharedプロバイダを登録します。指定したプレイヤーのみ見えます。
     * 次のupdateAll()で自動的に表示されます。
     */
    public void registerSharedProvider(HudSlot slot, HudTextProvider provider,
                                       Entity anchor, Set<Player> viewers) {
        Set<UUID> uuids = viewers.stream()
                .map(Player::getUniqueId)
                .collect(Collectors.toCollection(HashSet::new));
        sharedViewers.put(slot, uuids);
        registerSharedProvider(slot, provider, anchor);
    }

    /**
     * 複数のSharedプロバイダを一括登録します。
     */
    public void registerSharedProviders(HudSlot slot, List<HudTextProvider> list, Location anchor) {
        sharedProviders.computeIfAbsent(slot, k -> new ArrayList<>()).addAll(list);
        sharedAnchors.put(slot, anchor.clone());
    }

    /**
     * 複数のSharedプロバイダを一括登録します。
     */
    public void registerSharedProviders(HudSlot slot, List<HudTextProvider> list, Entity anchor) {
        if (anchor instanceof Player) {
            throw new IllegalArgumentException(
                    "Playerをanchorにすることはできません。"
            );
        }
        sharedProviders.computeIfAbsent(slot, k -> new ArrayList<>()).addAll(list);
        sharedAnchors.put(slot, anchor);
    }

    /**
     * 指定Sharedスロットのプロバイダをすべて削除します。
     *
     * @param slot 削除対象スロット
     */
    public void clearSharedProviders(HudSlot slot) {
        sharedProviders.remove(slot);
    }

    /**
     * 特定のSharedプロバイダインスタンスを削除します。
     *
     * @param slot     対象スロット
     * @param provider 削除するプロバイダ
     */
    public void unregisterSharedProvider(HudSlot slot, HudTextProvider provider) {
        List<HudTextProvider> list = sharedProviders.get(slot);
        if (list != null) list.remove(provider);
    }

    /**
     * 指定クラスに一致するSharedプロバイダをすべて削除します。
     *
     * @param slot  対象スロット
     * @param clazz 削除するプロバイダのクラス
     */
    public void unregisterSharedProvidersByClass(HudSlot slot,
                                                 Class<? extends HudTextProvider> clazz) {
        List<HudTextProvider> list = sharedProviders.get(slot);
        if (list != null) list.removeIf(clazz::isInstance);
    }

    /**
     * 登録済みSharedスロットから名前で検索します。
     *
     * @param name 検索する名前（{@link HudSlot#name(String)} で設定した値）
     * @return 見つかったHudSlot。見つからなければ {@link Optional#empty()}
     */
    public Optional<HudSlot> findSharedSlotByName(String name) {
        return sharedProviders.keySet().stream()
                .filter(slot -> slot.getName().map(name::equals).orElse(false))
                .findFirst();
    }

    /**
     * 名前でSharedスロットを検索し、見つかればそこへ追加、なければ新規スロットを作成して登録します。
     *
     * @param name     スロット名
     * @param fallback 見つからなかった場合に使う新規スロット
     * @param provider 登録するプロバイダ
     * @param anchor   表示するアンカー（Location）
     */
    public void registerSharedProviderByName(String name, HudSlot fallback,
                                             HudTextProvider provider, Location anchor) {
        HudSlot slot = findSharedSlotByName(name).orElse(fallback.name(name));
        registerSharedProvider(slot, provider, anchor);
    }

    /**
     * 名前でSharedスロットを検索し、見つかればそこへ追加、なければ新規スロットを作成して登録します。
     *
     * @param name     スロット名
     * @param fallback 見つからなかった場合に使う新規スロット
     * @param provider 登録するプロバイダ
     * @param anchor   表示するアンカー（Entity）
     */
    public void registerSharedProviderByName(String name, HudSlot fallback,
                                             HudTextProvider provider, Entity anchor) {
        HudSlot slot = findSharedSlotByName(name).orElse(fallback.name(name));
        registerSharedProvider(slot, provider, anchor);
    }


    // =========================================================
    // HUD表示制御（Personal）
    // =========================================================

    /**
     * プレイヤーにPersonal HUDを表示します。
     *
     * <p>このメソッドを呼ぶ前にプロバイダを登録しておくことを推奨します。
     * すでに表示中の場合は何もしません。</p>
     *
     * <p>プレイヤーが退出する際は必ず {@link #hidePersonalHud(Player)} を呼んでください。</p>
     *
     * @param player 対象プレイヤー
     */
    public void showPersonalHud(Player player) {
        UUID uuid = player.getUniqueId();
        if (personalInteractions.containsKey(uuid)) return; // 二重表示防止

        World world = player.getWorld();
        Location baseLoc = player.getLocation();

        // Interaction（HUDのルートアンカー）を生成
        Interaction inter = world.spawn(baseLoc, Interaction.class);
        inter.setInteractionHeight(0F);
        inter.setInteractionWidth(0F);
        inter.setResponsive(false);
        player.addPassenger(inter);
        personalInteractions.put(uuid, inter);

        // 表示方向の基底ベクトル計算
        float yaw = 0;
        float pitch = 28f;
        Vector3f forward = yawPitchToVector(yaw, pitch).mul(10f);
        Vector3f right = computeRightVector(yaw);
        Vector3f up = new Vector3f(0, 1, 0);

        // 登録済みスロット分のTextDisplayを生成
        Map<HudSlot, List<HudTextProvider>> posMap =
                personalProviders.getOrDefault(uuid, new LinkedHashMap<>());

        Map<HudSlot, TextDisplay> displayMap = new LinkedHashMap<>();

        for (Map.Entry<HudSlot, List<HudTextProvider>> entry : posMap.entrySet()) {
            HudSlot slot = entry.getKey();
            List<HudTextProvider> provs = sortedProviders(entry.getValue());
            Component text = buildText(provs);

            TextDisplay display = spawnDisplay(world, baseLoc, player, slot, text);
            inter.addPassenger(display);
            displayMap.put(slot, display);
        }

        personalDisplays.put(uuid, displayMap);
    }

    /**
     * プレイヤーのPersonal HUDのエンティティのみ削除します。
     * プロバイダとviewer設定は保持されるため、showPersonalHud()で再表示できます。
     * 死亡・リスポーン時などの一時的な非表示に使用してください。
     *
     * @param player 対象プレイヤー
     */
    public void hidePersonalHudEntities(Player player) {
        UUID uuid = player.getUniqueId();

        Map<HudSlot, TextDisplay> displayMap = personalDisplays.remove(uuid);
        if (displayMap != null) displayMap.values().forEach(Entity::remove);

        Interaction inter = personalInteractions.remove(uuid);
        if (inter != null) inter.remove();

        personalMaxLineWidths.remove(uuid);
        // personalProviders と personalHudViewers は残す
    }

    /**
     * プレイヤーのPersonal HUDを完全に削除します。
     * プロバイダ・viewer設定も含めてすべて削除します。
     * PlayerQuitEvent時に呼んでください。
     *
     * @param player 対象プレイヤー
     */
    public void hidePersonalHud(Player player) {
        hidePersonalHudEntities(player);
        personalProviders.remove(player.getUniqueId());
        personalHudViewers.remove(player.getUniqueId());
    }

    // =========================================================
    // HUD表示制御（Shared）
    // =========================================================

    /**
     * hideSharedSlotEntities()後に再表示します。
     * 初回表示はregisterSharedProvider()を使ってください。
     */
    public void showSharedSlot(HudSlot slot) {
        Object anchor = sharedAnchors.get(slot);
        if (anchor == null) {
            throw new IllegalStateException(
                    "アンカーが設定されていません。registerSharedProvider()を先に呼んでください。"
            );
        }
        if (anchor instanceof Entity entity) {
            spawnSharedEntities(entity.getLocation(), entity, slot);
        } else {
            spawnSharedEntities((Location) anchor, null, slot);
        }
    }

    /**
     * Shared HUDのエンティティのみ削除します。
     * プロバイダとviewer設定は保持されるため、showSharedSlot()で再表示できます。
     * 一時的な非表示に使用してください。
     *
     * @param slot 対象スロット
     */
    public void hideSharedSlotEntities(HudSlot slot) {
        TextDisplay display = sharedDisplays.remove(slot);
        if (display != null) display.remove();
        Interaction inter = sharedInteractions.remove(slot);
        if (inter != null) inter.remove();
        sharedAnchors.remove(slot);
        // sharedProviders と sharedViewers は残す
    }

    /**
     * Shared HUDを完全に削除します。
     * プロバイダ・viewer設定も含めてすべて削除します。
     *
     * @param slot 対象スロット
     */
    public void hideSharedSlot(HudSlot slot) {
        hideSharedSlotEntities(slot);
        sharedProviders.remove(slot);
        sharedViewers.remove(slot);
    }


    /**
     * 登録されている全Shared HUDを削除します。
     */
    public void hideAllShared() {
        new HashSet<>(sharedDisplays.keySet()).forEach(this::hideSharedSlot);
    }

    /**
     * 全Personal HUDと全Shared HUDを削除します。
     *
     * <p><b>onDisableで必ず呼んでください。</b></p>
     */
    public void hideAll() {
        new HashSet<>(personalInteractions.keySet()).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) hidePersonalHud(player);
        });

        hideAllShared();
    }

    // =========================================================
    // 閲覧者管理（Personal）
    // =========================================================

    /**
     * ownerの全Personal HUDスロットをviewerにも表示します。
     *
     * <p>{@link #showPersonalHud(Player)} を呼んだ後に使用してください。</p>
     *
     * <pre>{@code
     * // 観戦者にプレイヤーのHUDを全部見せる
     * hudManager.addPersonalHudViewer(player, spectator);
     * }</pre>
     *
     * @param owner  HUDの持ち主
     * @param viewer 閲覧を許可するプレイヤー
     */
    public void addPersonalHudViewer(Player owner, Player viewer) {
        UUID ownerUuid  = owner.getUniqueId();
        UUID viewerUuid = viewer.getUniqueId();

        Map<HudSlot, TextDisplay> displayMap = personalDisplays.get(ownerUuid);
        if (displayMap == null) return;

        // 全スロットにviewerを追加
        for (HudSlot slot : displayMap.keySet()) {
            personalHudViewers
                    .computeIfAbsent(ownerUuid, k -> new HashMap<>())
                    .computeIfAbsent(slot, k -> new HashSet<>())
                    .add(viewerUuid);
        }

        // 全Displayを見せる
        displayMap.values().forEach(display -> viewer.showEntity(plugin, display));

        // Interactionも見せる
        Interaction inter = personalInteractions.get(ownerUuid);
        if (inter != null) viewer.showEntity(plugin, inter);
    }

    /**
     * ownerの特定Personal HUDスロットをviewerにも表示します。
     *
     * <p>{@link #showPersonalHud(Player)} の前後どちらで呼んでも機能します。</p>
     *
     * <pre>{@code
     * // HPスロットだけ他のプレイヤーに見せる
     * hudManager.addPersonalHudViewer(player, hpSlot, other);
     * }</pre>
     *
     * @param owner  HUDの持ち主
     * @param slot   表示するスロット
     * @param viewer 閲覧を許可するプレイヤー
     */
    public void addPersonalHudViewer(Player owner, HudSlot slot, Player viewer) {
        UUID ownerUuid  = owner.getUniqueId();
        UUID viewerUuid = viewer.getUniqueId();

        personalHudViewers
                .computeIfAbsent(ownerUuid, k -> new HashMap<>())
                .computeIfAbsent(slot, k -> new HashSet<>())
                .add(viewerUuid);

        // すでに表示中なら即座に見せる
        Map<HudSlot, TextDisplay> displayMap = personalDisplays.get(ownerUuid);
        if (displayMap == null) return;

        TextDisplay display = displayMap.get(slot);
        if (display != null) viewer.showEntity(plugin, display);
    }

    /**
     * viewerからownerの全Personal HUDスロットを非表示にします。
     *
     * @param owner  HUDの持ち主
     * @param viewer 閲覧を取り消すプレイヤー
     */
    public void removePersonalHudViewer(Player owner, Player viewer) {
        UUID ownerUuid  = owner.getUniqueId();
        UUID viewerUuid = viewer.getUniqueId();

        Map<HudSlot, Set<UUID>> slotViewerMap = personalHudViewers.get(ownerUuid);
        if (slotViewerMap != null) {
            slotViewerMap.values().forEach(viewers -> viewers.remove(viewerUuid));
        }

        Map<HudSlot, TextDisplay> displayMap = personalDisplays.get(ownerUuid);
        if (displayMap != null) {
            displayMap.values().forEach(display -> viewer.hideEntity(plugin, display));
        }

        Interaction inter = personalInteractions.get(ownerUuid);
        if (inter != null) viewer.hideEntity(plugin, inter);
    }

    /**
     * viewerからownerの特定Personal HUDスロットを非表示にします。
     *
     * @param owner  HUDの持ち主
     * @param slot   非表示にするスロット
     * @param viewer 閲覧を取り消すプレイヤー
     */
    public void removePersonalHudViewer(Player owner, HudSlot slot, Player viewer) {
        UUID ownerUuid  = owner.getUniqueId();
        UUID viewerUuid = viewer.getUniqueId();

        Map<HudSlot, Set<UUID>> slotViewerMap = personalHudViewers.get(ownerUuid);
        if (slotViewerMap == null) return;

        Set<UUID> viewers = slotViewerMap.get(slot);
        if (viewers != null) viewers.remove(viewerUuid);

        Map<HudSlot, TextDisplay> displayMap = personalDisplays.get(ownerUuid);
        if (displayMap == null) return;

        TextDisplay display = displayMap.get(slot);
        if (display != null) viewer.hideEntity(plugin, display);
    }

    // =========================================================
    // 閲覧者管理（Shared）
    // =========================================================

    /**
     * Shared HUDの閲覧者を追加します。
     *
     * @param slot   対象スロット
     * @param viewer 閲覧を許可するプレイヤー
     */
    public void addSharedViewer(HudSlot slot, Player viewer) {
        sharedViewers.computeIfAbsent(slot, k -> new HashSet<>())
                .add(viewer.getUniqueId());
        TextDisplay display = sharedDisplays.get(slot);
        Interaction inter   = sharedInteractions.get(slot);
        if (display != null) viewer.showEntity(plugin, display);
        if (inter   != null) viewer.showEntity(plugin, inter);
    }

    /**
     * Shared HUDの閲覧者を削除します。
     *
     * @param slot   対象スロット
     * @param viewer 閲覧を取り消すプレイヤー
     */
    public void removeSharedViewer(HudSlot slot, Player viewer) {
        Set<UUID> viewers = sharedViewers.get(slot);
        if (viewers != null) viewers.remove(viewer.getUniqueId());
        TextDisplay display = sharedDisplays.get(slot);
        Interaction inter   = sharedInteractions.get(slot);
        if (display != null) viewer.hideEntity(plugin, display);
        if (inter   != null) viewer.hideEntity(plugin, inter);
    }

    // =========================================================
    // 時限タイプ
    // =========================================================

    /**
     * Personalプロバイダを登録し、指定tick後にそのプロバイダだけ削除します。
     * 他のプロバイダは消えず、追加したプロバイダだけ時間で消えます。
     * すでにshowPersonalHud済みのスロットに一時的なテキストを追加したい際に利用します。
     *
     * <pre>{@code
     * // 「緊急事態！」を5秒だけ表示。他のプロバイダは影響なし。
     * hudManager.registerPersonalProviderTemporary(
     *     player, slot,
     *     SimpleHudProvider.of(Component.text("緊急事態！", NamedTextColor.RED)),
     *     100L
     * );
     * }</pre>
     */
    public void registerPersonalTextTemporary(Player player, HudSlot slot,
                                                  HudTextProvider provider, long ticks) {
        registerPersonalProvider(player, slot, provider);
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                unregisterPersonalProvider(player, slot, provider), ticks);
    }

    /**
     * Sharedプロバイダを登録し、指定tick後にそのプロバイダだけ削除します。
     * 他のプロバイダは消えず、追加したプロバイダだけ時間で消えます。
     * すでにregisterSharedProvider済みのスロットに一時的なテキストを追加したい際に利用します。
     *
     * @param slot     対象スロット（anchorはすでに登録済みであること）
     * @param provider 登録するプロバイダ
     * @param ticks    表示するtick数
     */
    public void registerSharedTextTemporary(HudSlot slot,
                                            HudTextProvider provider, long ticks) {
        // anchorはすでに登録済みのものを使うため、providerだけ追加
        sharedProviders.computeIfAbsent(slot, k -> new ArrayList<>()).add(provider);
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                unregisterSharedProvider(slot, provider), ticks);
    }

    /**
     * Personalプロバイダを登録し、指定tick後にスロットごと削除します。
     * スロットに他のプロバイダがない場合はDisplayも非表示になります。
     * スロットを新しく作って表示して、時間で丸ごと消したい際に利用します。
     *
     * @param player   対象プレイヤー
     * @param slot     登録先スロット
     * @param provider 登録するプロバイダ
     * @param ticks    表示するtick数
     */
    public void registerPersonalSlotTemporary(Player player, HudSlot slot,
                                              HudTextProvider provider, long ticks) {
        registerPersonalProvider(player, slot, provider);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // プロバイダを削除
            clearPersonalProviders(player, slot);

            // Displayエンティティも削除
            UUID uuid = player.getUniqueId();
            Map<HudSlot, TextDisplay> displayMap = personalDisplays.get(uuid);
            if (displayMap != null) {
                TextDisplay display = displayMap.remove(slot);
                if (display != null) display.remove();
            }
        }, ticks);
    }

    /**
     * Sharedスロットをまるごと登録し、指定tick後に完全削除します。
     * register・hide をまとめて行います。
     * スロットを新しく作って表示して、時間で丸ごと消したい際に利用します。
     *
     * <pre>{@code
     * // 「ゲーム開始！」を3秒だけ全員に表示して消す
     * hudManager.registerSharedSlotTemporary(
     *     slot,
     *     SimpleHudProvider.of(Component.text("ゲーム開始！", NamedTextColor.GOLD)),
     *     location,
     *     60L
     * );
     * }</pre>
     *
     * @param slot     登録先スロット
     * @param provider 登録するプロバイダ
     * @param anchor   表示するアンカー（Location）
     * @param ticks    表示するtick数
     */
    public void registerSharedSlotTemporary(HudSlot slot, HudTextProvider provider,
                                            Location anchor, long ticks) {
        registerSharedProvider(slot, provider, anchor);
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                hideSharedSlot(slot), ticks);
    }

    /**
     * Sharedスロットをまるごと登録し、指定tick後に完全削除します。
     *
     * @param slot     登録先スロット
     * @param provider 登録するプロバイダ
     * @param anchor   表示するアンカー（Entity）
     * @param ticks    表示するtick数
     */
    public void registerSharedSlotTemporary(HudSlot slot, HudTextProvider provider,
                                            Entity anchor, long ticks) {
        registerSharedProvider(slot, provider, anchor);
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                hideSharedSlot(slot), ticks);
    }

    // =========================================================
    // 条件タイプ
    // =========================================================

    /**
     * Personalプロバイダを登録し、条件がfalseになったら自動削除します。
     *
     * <pre>{@code
     * hudManager.registerPersonalTextWhile(
     *     player, slot, provider,
     *     () -> distSq <= effectiveRange * effectiveRange
     * );
     * }</pre>
     *
     * @param player    対象プレイヤー
     * @param slot      登録先スロット
     * @param provider  登録するプロバイダ
     * @param condition 継続条件。falseになった時点でプロバイダを削除します
     */
    public void registerPersonalTextWhile(Player player, HudSlot slot,
                                          HudTextProvider provider,
                                          java.util.function.BooleanSupplier condition) {
        registerPersonalProvider(player, slot, provider);
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (!condition.getAsBoolean()) {
                    unregisterPersonalProvider(player, slot, provider);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Personalスロットを登録し、条件がfalseになったらスロットごと削除します。
     *
     * @param player    対象プレイヤー
     * @param slot      登録先スロット
     * @param provider  登録するプロバイダ
     * @param condition 継続条件。falseになった時点でスロットごと削除します
     */
    public void registerPersonalSlotWhile(Player player, HudSlot slot,
                                          HudTextProvider provider,
                                          java.util.function.BooleanSupplier condition) {
        registerPersonalProvider(player, slot, provider);
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (!condition.getAsBoolean()) {
                    clearPersonalProviders(player, slot);
                    UUID uuid = player.getUniqueId();
                    Map<HudSlot, TextDisplay> displayMap = personalDisplays.get(uuid);
                    if (displayMap != null) {
                        TextDisplay display = displayMap.remove(slot);
                        if (display != null) display.remove();
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Sharedプロバイダを登録し、条件がfalseになったら自動削除します。
     *
     * @param slot      登録先スロット（anchorはすでに登録済みであること）
     * @param provider  登録するプロバイダ
     * @param condition 継続条件。falseになった時点でプロバイダを削除します
     */
    public void registerSharedTextWhile(HudSlot slot,
                                        HudTextProvider provider,
                                        java.util.function.BooleanSupplier condition) {
        sharedProviders.computeIfAbsent(slot, k -> new ArrayList<>()).add(provider);
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!condition.getAsBoolean()) {
                    unregisterSharedProvider(slot, provider);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Sharedスロットを登録し、条件がfalseになったらスロットごと完全削除します。
     *
     * @param slot      登録先スロット
     * @param provider  登録するプロバイダ
     * @param anchor    表示するアンカー（Location）
     * @param condition 継続条件。falseになった時点でスロットごと削除します
     */
    public void registerSharedSlotWhile(HudSlot slot, HudTextProvider provider,
                                        Location anchor,
                                        java.util.function.BooleanSupplier condition) {
        registerSharedProvider(slot, provider, anchor);
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!condition.getAsBoolean()) {
                    hideSharedSlot(slot);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Sharedスロットを登録し、条件がfalseになったらスロットごと完全削除します。
     *
     * @param slot      登録先スロット
     * @param provider  登録するプロバイダ
     * @param anchor    表示するアンカー（Entity）
     * @param condition 継続条件。falseになった時点でスロットごと削除します
     */
    public void registerSharedSlotWhile(HudSlot slot, HudTextProvider provider,
                                        Entity anchor,
                                        java.util.function.BooleanSupplier condition) {
        registerSharedProvider(slot, provider, anchor);
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!condition.getAsBoolean()) {
                    hideSharedSlot(slot);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // =========================================================
    // 存在確認
    // =========================================================

    /**
     * 指定プレイヤー・スロットに指定プロバイダが登録されているか確認します。
     *
     * @param player   対象プレイヤー
     * @param slot     対象スロット
     * @param provider 確認するプロバイダ
     * @return 登録されている場合 {@code true}
     */
    public boolean isPersonalProviderRegistered(Player player, HudSlot slot, HudTextProvider provider) {
        return getPersonalProviderList(player, slot)
                .map(list -> list.contains(provider))
                .orElse(false);
    }

    /**
     * 指定プレイヤー・スロットに指定クラスのプロバイダが登録されているか確認します。
     *
     * @param player 対象プレイヤー
     * @param slot   対象スロット
     * @param clazz  確認するプロバイダのクラス
     * @return 登録されている場合 {@code true}
     */
    public boolean isPersonalProviderRegistered(Player player, HudSlot slot,
                                                Class<? extends HudTextProvider> clazz) {
        return getPersonalProviderList(player, slot)
                .map(list -> list.stream().anyMatch(clazz::isInstance))
                .orElse(false);
    }

    /**
     * 指定スロットに指定プロバイダが登録されているか確認します。
     *
     * @param slot     対象スロット
     * @param provider 確認するプロバイダ
     * @return 登録されている場合 {@code true}
     */
    public boolean isSharedProviderRegistered(HudSlot slot, HudTextProvider provider) {
        List<HudTextProvider> list = sharedProviders.get(slot);
        if (list == null) return false;
        return list.contains(provider);
    }

    /**
     * 指定スロットに指定クラスのプロバイダが登録されているか確認します。
     *
     * @param slot  対象スロット
     * @param clazz 確認するプロバイダのクラス
     * @return 登録されている場合 {@code true}
     */
    public boolean isSharedProviderRegistered(HudSlot slot,
                                              Class<? extends HudTextProvider> clazz) {
        List<HudTextProvider> list = sharedProviders.get(slot);
        if (list == null) return false;
        return list.stream().anyMatch(clazz::isInstance);
    }

    // =========================================================
    // 毎tick更新
    // =========================================================

    /**
     * 全プレイヤーのHUDテキストとスロット設定を更新します。
     *
     * <p>{@link HudSlot} のパラメータ（位置・回転・スケール・透明度・背景色）の変更も
     * このメソッドで反映されます。</p>
     *
     * <p>BukkitSchedulerで定期的に呼び出してください：</p>
     * <pre>{@code
     * Bukkit.getScheduler().runTaskTimer(plugin, hudManager::updateAll, 0L, 1L);
     * }</pre>
     */
    public void updateAll() {
        for (UUID uuid : new HashSet<>(personalProviders.keySet())) {
            Map<HudSlot, TextDisplay> displayMap = personalDisplays.get(uuid);
            if (displayMap == null) continue;

            Map<HudSlot, List<HudTextProvider>> posMap = personalProviders.get(uuid);
            if (posMap == null) continue;

            Interaction inter = personalInteractions.get(uuid);

            for (Map.Entry<HudSlot, List<HudTextProvider>> entry : posMap.entrySet()) {
                HudSlot slot = entry.getKey();
                TextDisplay display = displayMap.get(slot);

                // showHud後に追加されたスロットは動的に生成
                if (display == null) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || inter == null) continue;
                    display = spawnDisplay(player.getWorld(), player.getLocation(),
                            player, slot, Component.empty());
                    if (inter != null) inter.addPassenger(display);
                    displayMap.put(slot, display);
                }

                List<HudTextProvider> provs = sortedProviders(entry.getValue());
                Component text = buildText(provs);

                // テキスト幅・行数を計算
                String plain = PlainTextComponentSerializer.plainText().serialize(text);
                String[] lines = plain.split("\n", -1);
                int lineCount = lines.length;

                float totalTextWidth = (float) Arrays.stream(lines)
                        .mapToDouble(line -> estimateLineWidth(line) * 0.19)
                        .max()
                        .orElse(0.0);

                personalMaxLineWidths.computeIfAbsent(uuid, k -> new LinkedHashMap<>())
                        .put(slot, totalTextWidth);

                // 毎tickスロット設定を読み直してTransformationを組み立て
                Vector3f offset = computeOffset(slot);

                float lineHeight = 0.37f * slot.scale / 1.5f;
                // scaleが1.5f（デフォルト）からどれだけずれているかの補正
                float scaleOffset = (slot.scale - 1.5f) * 0.1f; // 係数はマジックナンバー
                float verticalOffset = switch (slot.verticalGrowth) {
                    case UP     -> 0f;
                    case DOWN   -> -(lineCount - 1) * lineHeight;
                    case CENTER -> -scaleOffset;
                };

                float horizontalOffset = switch (slot.horizontalGrowth) {
                    case LEFT   -> -totalTextWidth;
                    case RIGHT  ->  totalTextWidth;
                    case CENTER -> 0f;
                };

                Vector3f rightVec = computeRightVector(0f);
                Vector3f adjustedOffset = new Vector3f(offset);
                adjustedOffset.y += verticalOffset;
                adjustedOffset.add(new Vector3f(rightVec).mul(horizontalOffset));

                Quaternionf rotation = new Quaternionf()
                        .rotateY((float) Math.toRadians(slot.rotationYaw))
                        .rotateX((float) Math.toRadians(slot.rotationPitch))
                        .rotateZ((float) Math.toRadians(slot.rotationRoll));

                display.text(text);
                display.setAlignment(toTextAlignment(slot));
                display.setTransformation(new Transformation(
                        adjustedOffset,
                        new Quaternionf(),
                        new Vector3f(slot.scale),
                        rotation
                ));

                // 透明度を毎tick反映
                display.setTextOpacity((byte) slot.opacity);
                applyBackground(display, slot);
            }
        }

        // 共有スロットの更新
        for (Map.Entry<HudSlot, List<HudTextProvider>> slotEntry : new LinkedHashMap<>(sharedProviders).entrySet()) {
            HudSlot slot = slotEntry.getKey();

            // アンカーが設定されていなければスキップ
            Object anchor = sharedAnchors.get(slot);
            if (anchor == null) continue;

            // まだ表示されていなければ自動生成
            if (!sharedDisplays.containsKey(slot)) {
                if (anchor instanceof Entity entity) {
                    spawnSharedEntities(entity.getLocation(), entity, slot);
                } else {
                    spawnSharedEntities((Location) anchor, null, slot);
                }
            }

            TextDisplay display = sharedDisplays.get(slot);
            if (display == null) continue;

            List<HudTextProvider> provs = sortedProviders(
                    sharedProviders.getOrDefault(slot, List.of()));
            Component text = buildText(provs);

            String plain = PlainTextComponentSerializer.plainText().serialize(text);
            String[] lines = plain.split("\n", -1);
            int lineCount = lines.length;

            float totalTextWidth = (float) Arrays.stream(lines)
                    .mapToDouble(line -> estimateLineWidth(line) * 0.19)
                    .max()
                    .orElse(0.0);

            float lineHeight = 0.37f * slot.scale / 1.5f;
            float verticalOffset = switch (slot.verticalGrowth) {
                case UP     -> 0f;
                case DOWN   -> -(lineCount - 1) * lineHeight;
                case CENTER -> 0f;
            };

            float horizontalOffset = switch (slot.horizontalGrowth) {
                case LEFT   -> -totalTextWidth;
                case RIGHT  ->  totalTextWidth;
                case CENTER -> 0f;
            };

            Vector3f rightVec = computeRightVector(0f);
            Vector3f offset   = computeOffset(slot);
            Vector3f adjustedOffset = new Vector3f(offset);
            adjustedOffset.y += verticalOffset;
            adjustedOffset.add(new Vector3f(rightVec).mul(horizontalOffset));

            Quaternionf rotation = new Quaternionf()
                    .rotateY((float) Math.toRadians(slot.rotationYaw))
                    .rotateX((float) Math.toRadians(slot.rotationPitch));

            display.text(text);
            display.setAlignment(toTextAlignment(slot));
            display.setTransformation(new Transformation(
                    adjustedOffset,
                    new Quaternionf(),
                    new Vector3f(slot.scale),
                    rotation
            ));
            display.setTextOpacity((byte) slot.opacity);
            applyBackground(display, slot);
        }
    }

    // =========================================================
    // ユーティリティ（公開API）
    // =========================================================

    /**
     * 指定プレイヤー・スロットの最大テキスト幅（スケール済み）を返します。
     *
     * <p>{@link HudTextProvider#getHudText()} 内でヘッダーの幅を揃えるために使えます。
     * 実際の文字幅計算に使う場合は {@code ÷ 0.19} した値を使ってください。</p>
     *
     * <pre>{@code
     * float scaled = hudManager.getMaxLineWidthFor(uuid, slot);
     * float unscaled = scaled / 0.19f;
     * String header = hudManager.generateCenteredHeader("タイトル", unscaled);
     * }</pre>
     *
     * @param uuid プレイヤーのUUID
     * @param slot 対象スロット
     * @return 最大テキスト幅（スケール済み）。未計算の場合は {@code 0f}
     */
    public float getMaxLineWidthFor(UUID uuid, HudSlot slot) {
        return personalMaxLineWidths.getOrDefault(uuid, Collections.emptyMap())
                .getOrDefault(slot, 0f);
    }

    /**
     * ラベルを中央に配置した区切り文字列を生成します。
     *
     * <p>テキストの幅に合わせて両端に {@code =} を追加します。</p>
     *
     * <pre>{@code
     * // 例: "=====データ=====" のような文字列を生成
     * float scaled = hudManager.getMaxLineWidthFor(uuid, slot);
     * String header = hudManager.generateCenteredHeader("データ", scaled / 0.19f);
     * }</pre>
     *
     * @param label      中央に配置する文字列
     * @param totalWidth 目標とする合計幅（{@link #getMaxLineWidthFor} の値を {@code ÷ 0.19} した値を推奨）
     * @return 中央揃えされた区切り文字列
     */
    public String generateCenteredHeader(String label, double totalWidth) {
        double equalWidth  = estimateCharWidth('=');
        double labelWidth  = estimateLineWidth(label);
        double paddingWidth = Math.max(0, totalWidth - labelWidth);
        int equalsEachSide = (int) Math.floor(paddingWidth / 2 / equalWidth);
        double usedWidth   = equalsEachSide * 2 * equalWidth;
        if (paddingWidth - usedWidth >= equalWidth * 0.75) equalsEachSide++;
        return "=".repeat(equalsEachSide) + label + "=".repeat(equalsEachSide);
    }

    /**
     * 文字が全角かどうかを判定します。
     *
     * <p>ひらがな・カタカナ・漢字・全角英数字を全角として扱います。
     * テキスト幅の推定に内部で使用しています。</p>
     *
     * @param c 判定する文字
     * @return 全角の場合 {@code true}
     */
    public boolean isFullWidth(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    // =========================================================
    // 内部ヘルパー
    // =========================================================

    // owner指定あり（personal用）: owner以外に非表示
    private TextDisplay spawnDisplay(World world, Location loc,
                                     Player owner, HudSlot slot, Component text) {
        TextDisplay display = createDisplay(world, loc, slot, text);
        hideFromOthers(owner, display, slot);
        return display;
    }

    // owner指定なし（shared用）: 表示制御は呼び出し側が行う
    private TextDisplay spawnDisplay(World world, Location loc,
                                     HudSlot slot, Component text) {
        return createDisplay(world, loc, slot, text);
    }

    // 共通の生成処理
    private TextDisplay createDisplay(World world, Location loc,
                                      HudSlot slot, Component text) {
        TextDisplay display = world.spawn(loc, TextDisplay.class);
        display.text(text);
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setViewRange(1.0f);
        display.setShadowed(true);
        display.setLineWidth(slot.lineWidth);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setTextOpacity((byte) slot.opacity);
        display.setAlignment(toTextAlignment(slot));
        applyBackground(display, slot);

        Vector3f offset = computeOffset(slot);
        Quaternionf rotation = new Quaternionf()
                .rotateY((float) Math.toRadians(slot.rotationYaw))
                .rotateX((float) Math.toRadians(slot.rotationPitch))
                .rotateZ((float) Math.toRadians(slot.rotationRoll));
        display.setTransformation(new Transformation(
                offset,
                new Quaternionf(),
                new Vector3f(slot.scale),
                rotation
        ));
        return display;
    }

    private void spawnSharedEntities(Location loc, Entity anchor, HudSlot slot) {
        if (sharedInteractions.containsKey(slot)) return;

        World world = loc.getWorld();

        Interaction inter = world.spawn(loc, Interaction.class);
        inter.setInteractionHeight(0F);
        inter.setInteractionWidth(0F);
        inter.setResponsive(false);
        if (anchor != null) anchor.addPassenger(inter);
        sharedInteractions.put(slot, inter);
        sharedAnchors.put(slot, anchor != null ? anchor : loc.clone());

        Component text = buildText(sortedProviders(
                sharedProviders.getOrDefault(slot, List.of())));
        TextDisplay display = spawnDisplay(world, loc, slot, text);
        inter.addPassenger(display);
        sharedDisplays.put(slot, display);

        // viewer制限の適用
        Set<UUID> viewers = sharedViewers.get(slot);
        if (viewers != null) {
            // showSharedSlotFor の場合: 全員に非表示にしてviewerだけ見せる
            Bukkit.getOnlinePlayers().forEach(p -> {
                p.hideEntity(plugin, inter);
                p.hideEntity(plugin, display);
            });
            viewers.stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .forEach(p -> {
                        p.showEntity(plugin, inter);
                        p.showEntity(plugin, display);
                    });
        }
        // viewers が null の場合: 何もしない = 全員に見える（デフォルト）
    }

    /** 背景色をDisplayに適用する共通メソッド */
    private void applyBackground(TextDisplay display, HudSlot slot) {
        if (slot.backgroundColor == null) {
            // nullならデフォルト背景
            display.setDefaultBackground(true);
        } else {
            display.setDefaultBackground(false);
            display.setBackgroundColor(slot.backgroundColor);
        }
    }

    /**
     * HudSlotからワールド空間上のオフセットベクトルを計算します。
     * yaw=0固定（Interactionに乗っているためプレイヤーの向きは自動追従）
     */
    private Vector3f computeOffset(HudSlot slot) {
        float yaw = 0f;
        float pitch = 28f; // 画面の傾き補正（固定）

        Vector3f forward  = yawPitchToVector(yaw, pitch).mul(slot.offsetForward);
        Vector3f right    = computeRightVector(yaw);
        Vector3f up       = new Vector3f(0, 1, 0);

        return new Vector3f(forward)
                .add(new Vector3f(right).mul(slot.offsetRight))
                .add(new Vector3f(up).mul(slot.offsetUp));
    }

    private Component buildText(List<HudTextProvider> provs) {
        if (provs.isEmpty()) return Component.empty();
        Component result = Component.empty();
        for (int i = 0; i < provs.size(); i++) {
            result = result.append(provs.get(i).getHudText());
            if (i < provs.size() - 1) result = result.append(Component.newline());
        }
        return result;
    }

    private List<HudTextProvider> sortedProviders(List<HudTextProvider> provs) {
        return provs.stream()
                .sorted(Comparator.comparingInt(HudTextProvider::getPriority)
                        .thenComparing(p -> p.getClass().getName()))
                .toList();
    }

    private void hideFromOthers(Player owner, Entity entity, HudSlot slot) {
        UUID ownerUuid = owner.getUniqueId();
        Map<HudSlot, Set<UUID>> slotViewerMap =
                personalHudViewers.getOrDefault(ownerUuid, Map.of());
        Set<UUID> viewers = slotViewerMap.getOrDefault(slot, Set.of());

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(owner)) continue;
            if (viewers.contains(other.getUniqueId())) {
                other.showEntity(plugin, entity);
            } else {
                other.hideEntity(plugin, entity);
            }
        }
    }

    private Vector3f yawPitchToVector(float yaw, float pitch) {
        float yawRad   = (float) Math.toRadians(-yaw - 180);
        float pitchRad = (float) Math.toRadians(-pitch);
        return new Vector3f(
                (float) (Math.sin(yawRad) * Math.cos(pitchRad)),
                (float)  Math.sin(pitchRad),
                (float) (Math.cos(yawRad) * Math.cos(pitchRad))
        );
    }

    private Vector3f computeRightVector(float yaw) {
        float yawRad = (float) Math.toRadians(-yaw - 180);
        return new Vector3f(
                (float) Math.sin(yawRad - Math.PI / 2),
                0,
                (float) Math.cos(yawRad - Math.PI / 2)
        ).normalize();
    }

    private float estimateLineWidth(String line) {
        float width = 0f;
        for (char c : line.toCharArray()) width += estimateCharWidth(c);
        return width;
    }

    private float estimateCharWidth(char c) {
        if (isFullWidth(c)) return 1.0f;
        if (c == ' ')       return 0.25f;
        return 0.6f;
    }

    private TextDisplay.TextAlignment toTextAlignment(HudSlot slot) {
        return switch (slot.textAlignment) {
            case LEFT   -> TextDisplay.TextAlignment.LEFT;
            case CENTER -> TextDisplay.TextAlignment.CENTER;
            case RIGHT  -> TextDisplay.TextAlignment.RIGHT;
        };
    }
}