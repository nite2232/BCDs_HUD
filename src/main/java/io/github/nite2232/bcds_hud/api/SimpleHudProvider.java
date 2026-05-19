package io.github.nite2232.bcds_hud.api;

import net.kyori.adventure.text.Component;

/**
 * 固定テキストを表示するシンプルなプロバイダ。
 * クラスを別途作成せずにラムダやメソッド参照でも使えます。
 *
 * <pre>{@code
 * // 固定テキスト
 * HudTextProvider provider = SimpleHudProvider.of(
 *     Component.text("緊急事態！", NamedTextColor.RED)
 * );
 *
 * // ラムダで動的テキスト
 * HudTextProvider provider = SimpleHudProvider.of(
 *     () -> Component.text("時刻: " + LocalTime.now())
 * );
 * }</pre>
 */
public class SimpleHudProvider implements HudTextProvider {

    private final java.util.function.Supplier<Component> supplier;
    private final int priority;

    private SimpleHudProvider(java.util.function.Supplier<Component> supplier, int priority) {
        this.supplier = supplier;
        this.priority = priority;
    }

    /** 固定テキストのプロバイダを作成します。 */
    public static SimpleHudProvider of(Component text) {
        return new SimpleHudProvider(() -> text, HudPriority.NORMAL);
    }

    /** 固定テキストと優先度を指定してプロバイダを作成します。 */
    public static SimpleHudProvider of(Component text, int priority) {
        return new SimpleHudProvider(() -> text, priority);
    }

    /** ラムダで動的テキストを返すプロバイダを作成します。 */
    public static SimpleHudProvider of(java.util.function.Supplier<Component> supplier) {
        return new SimpleHudProvider(supplier, HudPriority.NORMAL);
    }

    /** ラムダと優先度を指定してプロバイダを作成します。 */
    public static SimpleHudProvider of(java.util.function.Supplier<Component> supplier, int priority) {
        return new SimpleHudProvider(supplier, priority);
    }

    @Override
    public Component getHudText() {
        return supplier.get();
    }

    @Override
    public int getPriority() {
        return priority;
    }
}