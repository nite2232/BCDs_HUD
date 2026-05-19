package io.github.nite2232.bcds_hud.api;

import org.bukkit.Color;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * HUDの1スロット（表示位置・見た目の設定）を表すクラス。
 *
 * <p>プリセットを使うか、{@link #custom()}で自由に設定できます。
 * フィールドはすべてvolatileで、updateAll実行中でも安全に変更できます。</p>
 *
 * <pre>{@code
 * // プリセット使用
 * HudSlot slot = HudSlot.topLeft();
 *
 * // 完全カスタム
 * HudSlot slot = HudSlot.custom()
 *     .offset(2f, 5f, 10f)
 *     .scale(1.2f)
 *     .opacity(200);
 * }</pre>
 */
public class HudSlot {

    // =====================================================
    // 表示設定（updateAll中に変更可能）
    // =====================================================

    private String name; // nullable

    /** プレイヤーからの右方向オフセット */
    public volatile float offsetRight;
    /** プレイヤーからの上方向オフセット */
    public volatile float offsetUp;
    /** プレイヤーからの前方向オフセット（奥行き・距離） */
    public volatile float offsetForward;

    /** 表示スケール（デフォルト: 1.5） */
    public volatile float scale;

    /** Y軸回転（度数）。LEFT/RIGHTの傾きに相当 */
    public volatile float rotationYaw;
    /** X軸回転（度数）。上下の傾きに相当 */
    public volatile float rotationPitch;
    /** Z軸回転（度数）。ロール（傾き）に相当 */
    public volatile float rotationRoll;

    /**
     * 不透明度（0=完全透明, 255=完全不透明）。
     * 255にするとエフェクトなしの完全表示になります。
     */
    public volatile int opacity;

    /**
     * 背景色（ARGB）。nullの場合はデフォルト背景を使用します。
     * {@link Color#fromARGB(int, int, int, int)} で作成してください。
     *
     * <pre>{@code
     * // 半透明の黒背景
     * slot.backgroundColor = Color.fromARGB(128, 0, 0, 0);
     *
     * // 完全透明（背景なし）
     * slot.backgroundColor = Color.fromARGB(0, 0, 0, 0);
     *
     * // デフォルト背景に戻す
     * slot.backgroundColor = null;
     * }</pre>
     */
    public volatile Color backgroundColor = null;

    /** テキストの縦方向の伸び方 */
    public volatile VerticalGrowth verticalGrowth;
    /** テキストの横方向の配置 */
    public volatile HorizontalGrowth horizontalGrowth;

    public volatile TextAlignment textAlignment;

    public volatile int lineWidth = 10000;

    /**
     * テキストが縦方向にどう伸びるか。
     * DOWNの場合はアンカー位置から下に向かってテキストが増えます。
     */
    public enum VerticalGrowth {
        /** アンカー位置から上向きにテキストが増える */
        UP,
        /** アンカー位置から下向きにテキストが増える */
        DOWN,
        /** アンカー位置を中心に上下に広がる */
        CENTER
    }

    /**
     * テキストの横方向の揃え方。
     */
    public enum HorizontalGrowth {
        /** アンカー位置から左向きにテキストが増える */
        LEFT,
        /** アンカー位置から右向きにテキストが増える */
        RIGHT,
        /** アンカー位置を中心に左右に広がる */
        CENTER
    }

    public enum TextAlignment {
        LEFT, CENTER, RIGHT
    }

    // =====================================================
    // コンストラクタ（privateにしてファクトリ経由で作成）
    // =====================================================

    private HudSlot() {
        // デフォルト値
        this.offsetRight   = 0f;
        this.offsetUp      = 0f;
        this.offsetForward = 10f;
        this.scale         = 1.5f;
        this.rotationYaw   = 0f;
        this.rotationPitch = 0f;
        this.rotationRoll  = 0f;
        this.opacity       = 255;
        this.lineWidth     = 380;
        this.verticalGrowth   = VerticalGrowth.DOWN;
        this.horizontalGrowth = HorizontalGrowth.RIGHT;
        this.textAlignment    = TextAlignment.LEFT;
    }

    // =====================================================
    // ビルダースタイルのセッター（メソッドチェーン用）
    // =====================================================

    /**
     * 名前を設定するセッター
     *
     * @param name
     * @return
     */
    public HudSlot name(String name) {
        this.name = name;
        return this;
    }

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    /**
     * 位置オフセットを設定します。
     *
     * @param right   右方向（負で左）
     * @param up      上方向（負で下）
     * @param forward 前方向（奥行き・距離）
     */
    public HudSlot offset(float right, float up, float forward) {
        this.offsetRight   = right;
        this.offsetUp      = up;
        this.offsetForward = forward;
        return this;
    }

    /** スケールを設定します。 */
    public HudSlot scale(float scale) {
        this.scale = scale;
        return this;
    }

    /**
     * 回転を設定します。
     *
     * @param yaw   Y軸回転（度数）。正で右傾き
     * @param pitch X軸回転（度数）。正で上傾き
     */
    public HudSlot rotation(float yaw, float pitch) {
        this.rotationYaw   = yaw;
        this.rotationPitch = pitch;
        return this;
    }

    public HudSlot rotation(float yaw, float pitch, float roll) {
        this.rotationYaw   = yaw;
        this.rotationPitch = pitch;
        this.rotationRoll  = roll;
        return this;
    }

    /**
     * 不透明度を設定します（0〜255）。
     * 255で完全不透明、0で完全透明です。
     */
    public HudSlot opacity(int opacity) {
        this.opacity = Math.max(0, Math.min(255, opacity));
        return this;
    }

    /**
     * 背景色をARGBで設定します。
     *
     * @param a アルファ（0=透明, 255=不透明）
     * @param r 赤
     * @param g 緑
     * @param b 青
     */
    public HudSlot backgroundColor(int a, int r, int g, int b) {
        this.backgroundColor = Color.fromARGB(a, r, g, b);
        return this;
    }

    /**
     * 背景色をColorで設定します。nullを渡すとデフォルト背景に戻ります。
     */
    public HudSlot backgroundColor(Color color) {
        this.backgroundColor = color;
        return this;
    }

    /** 背景を完全に透明にします。 */
    public HudSlot backgroundTransparent() {
        this.backgroundColor = Color.fromARGB(0, 0, 0, 0);
        return this;
    }

    /** 背景をデフォルト（Minecraftの薄黒）に戻します。 */
    public HudSlot backgroundDefault() {
        this.backgroundColor = null;
        return this;
    }

    /** テキストの縦方向の伸び方を設定します。 */
    public HudSlot verticalGrowth(VerticalGrowth growth) {
        this.verticalGrowth = growth;
        return this;
    }

    /** テキストの横方向の配置を設定します。 */
    public HudSlot horizontalGrowth(HorizontalGrowth growth) {
        this.horizontalGrowth = growth;
        return this;
    }

    public HudSlot textAlignment(TextAlignment alignment) {
        this.textAlignment = alignment;
        return this;
    }

    public HudSlot lineWidth(int lineWidth) {
        this.lineWidth = lineWidth;
        return this;
    }

    /**
     * 現在のオフセットに加算します。
     *
     * <pre>{@code
     * slot.addOffset(0f, -2f, 0f); // 上方向に-2（= 下に2下げる）
     * slot.addOffset(3f, 0f, 0f);  // 右に3追加
     * }</pre>
     *
     * @param right   右方向への加算量（負で左）
     * @param up      上方向への加算量（負で下）
     * @param forward 前方向への加算量
     */
    public HudSlot addOffset(float right, float up, float forward) {
        this.offsetRight   += right;
        this.offsetUp      += up;
        this.offsetForward += forward;
        return this;
    }

    /** 上方向のオフセットに加算します。負の値で下に移動します。 */
    public HudSlot addOffsetUp(float up) {
        this.offsetUp += up;
        return this;
    }

    /** 右方向のオフセットに加算します。負の値で左に移動します。 */
    public HudSlot addOffsetRight(float right) {
        this.offsetRight += right;
        return this;
    }

    /** 前方向のオフセットに加算します。 */
    public HudSlot addOffsetForward(float forward) {
        this.offsetForward += forward;
        return this;
    }

    /**
     * 現在の回転に加算します。
     *
     * <pre>{@code
     * slot.addRotation(0f, 0f, 5f); // ロールを5度追加
     * slot.addRotation(10f, 0f, 0f); // ヨーを10度追加
     * }</pre>
     *
     * @param yaw   Y軸回転への加算量（度数）
     * @param pitch X軸回転への加算量（度数）
     * @param roll  Z軸回転への加算量（度数）
     */
    public HudSlot addRotation(float yaw, float pitch, float roll) {
        this.rotationYaw   += yaw;
        this.rotationPitch += pitch;
        this.rotationRoll  += roll;
        return this;
    }

    /** Y軸回転（ヨー）に加算します。 */
    public HudSlot addRotationYaw(float yaw) {
        this.rotationYaw += yaw;
        return this;
    }

    /** X軸回転（ピッチ）に加算します。 */
    public HudSlot addRotationPitch(float pitch) {
        this.rotationPitch += pitch;
        return this;
    }

    /** Z軸回転（ロール）に加算します。 */
    public HudSlot addRotationRoll(float roll) {
        this.rotationRoll += roll;
        return this;
    }

    // =====================================================
    // ファクトリ
    // =====================================================

    /** 完全カスタムのスロットを作成します。デフォルト値から始まります。 */
    public static HudSlot custom() {
        return new HudSlot();
    }

    // --- プリセット ---
    // offsetForwardはyawPitchToVector(0, 28).mul(10f)の前方成分に相当するため

    public static HudSlot topLeft() {
        return new HudSlot()
                .offset(-10f, 9.5f, 10f)
                .rotation(6f, 0f)
                .verticalGrowth(VerticalGrowth.DOWN)
                .horizontalGrowth(HorizontalGrowth.RIGHT);
    }

    public static HudSlot topCenter() {
        return new HudSlot()
                .offset(0f, 9.5f, 10f)
                .rotation(0f, 0f)
                .verticalGrowth(VerticalGrowth.DOWN)
                .horizontalGrowth(HorizontalGrowth.CENTER);
    }

    public static HudSlot topRight() {
        return new HudSlot()
                .offset(10f, 9.5f, 10f)
                .rotation(-6f, 0f)
                .verticalGrowth(VerticalGrowth.DOWN)
                .horizontalGrowth(HorizontalGrowth.LEFT);
    }

    public static HudSlot middleLeft() {
        return new HudSlot()
                .offset(-10f, 4f, 10f)
                .rotation(6f, 0f)
                .verticalGrowth(VerticalGrowth.UP)
                .horizontalGrowth(HorizontalGrowth.RIGHT);
    }

    public static HudSlot middleCenter() {
        return new HudSlot()
                .offset(0f, 4f, 10f)
                .rotation(0f, 0f)
                .verticalGrowth(VerticalGrowth.CENTER)
                .horizontalGrowth(HorizontalGrowth.CENTER)
                .textAlignment(TextAlignment.CENTER);
    }

    public static HudSlot middleRight() {
        return new HudSlot()
                .offset(10f, 4f, 10f)
                .rotation(-6f, 0f)
                .verticalGrowth(VerticalGrowth.UP)
                .horizontalGrowth(HorizontalGrowth.LEFT);
    }

    public static HudSlot bottomLeft() {
        return new HudSlot()
                .offset(-10f, -0.5f, 10f)
                .rotation(6f, 0f)
                .verticalGrowth(VerticalGrowth.UP)
                .horizontalGrowth(HorizontalGrowth.RIGHT);
    }

    public static HudSlot bottomCenter() {
        return new HudSlot()
                .offset(0f, -0.5f, 10f)
                .rotation(0f, 0f)
                .verticalGrowth(VerticalGrowth.UP)
                .horizontalGrowth(HorizontalGrowth.CENTER);
    }

    public static HudSlot bottomRight() {
        return new HudSlot()
                .offset(10f, -0.5f, 10f)
                .rotation(-6f, 0f)
                .verticalGrowth(VerticalGrowth.UP)
                .horizontalGrowth(HorizontalGrowth.LEFT);
    }
}