package io.github.nite2232.bcds_hud.api;

import net.kyori.adventure.text.Component;

/**
 * HUDに表示するテキストを提供するインターフェース。
 * 各プラグインでこのインターフェースを実装し、HudManagerに登録してください。
 */
public interface HudTextProvider {

    /**
     * HUDに表示するテキストを返します。
     * このメソッドはtickごとに呼び出されます。
     *
     * @return 表示するComponent（改行を含めても構いません）
     */
    Component getHudText();

    /**
     * 表示優先度を返します。数値が小さいほど上に表示されます。
     * {@link HudPriority}の定数を使うか、任意のintを返してください。
     *
     * @return 優先度
     */
    int getPriority();
}