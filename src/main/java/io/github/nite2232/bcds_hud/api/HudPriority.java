package io.github.nite2232.bcds_hud.api;

/**
 * HUDプロバイダーの表示優先度を表す定数クラス。
 * {@link HudTextProvider#getPriority()}の戻り値として使用できます。
 *
 * <p>独自の優先度を使いたい場合は任意のint値を直接返しても構いません。</p>
 */
public final class HudPriority {
    public static final int HIGHEST = 0;
    public static final int HIGH    = 100;
    public static final int NORMAL  = 200;
    public static final int LOW     = 300;
    public static final int LOWEST  = 400;

    private HudPriority() {}
}