package ando.toolkit

import ando.toolkit.NotchUtils.isNotchOfficialSupport
import ando.toolkit.OSUtils.isEssentialPhone
import ando.toolkit.OSUtils.isFlyme
import ando.toolkit.OSUtils.isFlymeLowerThan
import ando.toolkit.OSUtils.isMIUI
import ando.toolkit.OSUtils.isMIUIV5
import ando.toolkit.OSUtils.isMIUIV6
import ando.toolkit.OSUtils.isMIUIV7
import ando.toolkit.OSUtils.isMIUIV8
import ando.toolkit.OSUtils.isMIUIV9
import ando.toolkit.OSUtils.isMeizu
import ando.toolkit.OSUtils.isZTKC2016
import ando.toolkit.OSUtils.isZUKZ1
import ando.toolkit.ext.DeviceUtils
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Build.VERSION_CODES.HONEYCOMB
import android.os.Build.VERSION_CODES.KITKAT
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.IntDef
import androidx.core.view.ViewCompat
import java.lang.reflect.Field

/**
 * https://github.com/Tencent/QMUI_Android/blob/master/qmui/src/main/java/com/qmuiteam/qmui/util/QMUIStatusBarHelper.java
 *
 * 注: 需要混淆
 */
object StatusBarUtils {

    private const val STATUSBAR_TYPE_DEFAULT = 0
    private const val STATUSBAR_TYPE_MIUI = 1
    private const val STATUSBAR_TYPE_FLYME = 2
    private const val STATUSBAR_TYPE_ANDROID6 = 3 // Android 6.0
    private const val STATUS_BAR_DEFAULT_HEIGHT_DP = 25 // 大部分状态栏都是25dp

    // 在某些机子上存在不同的density值，所以增加两个虚拟值
    var sVirtualDensity = -1f
    var sVirtualDensityDpi = -1f
    private var sStatusBarHeight = -1

    @StatusBarType
    private var mStatusBarType = STATUSBAR_TYPE_DEFAULT
    private var sTransparentValue: Int? = null

    fun translucent(activity: Activity) {
        translucent(activity.window)
    }

    fun translucent(window: Window) {
        translucent(window, 0x40000000)
    }

    private fun supportTranslucent(): Boolean {
        // Essential Phone 在 Android 8 之前沉浸式做得不全，系统不从状态栏顶部开始布局却会下发 WindowInsets
        return !(isEssentialPhone() && Build.VERSION.SDK_INT < 26)
    }

    /**
     * 沉浸式状态栏。
     * 支持 4.4 以上版本的 MIUI 和 Flyme，以及 5.0 以上版本的其他 Android。
     *
     * @param activity 需要被设置沉浸式状态栏的 Activity。
     */
    fun translucent(activity: Activity, @ColorInt colorOn5x: Int) {
        val window = activity.window
        translucent(window, colorOn5x)
    }

    @TargetApi(19)
    fun translucent(window: Window, @ColorInt colorOn5x: Int) {
        if (!supportTranslucent()) {
            // 版本小于4.4，绝对不考虑沉浸式
            return
        }
        if (isNotchOfficialSupport()) {
            handleDisplayCutoutMode(window)
        }

        // 小米和魅族4.4 以上版本支持沉浸式
        // 小米 Android 6.0 ，开发版 7.7.13 及以后版本设置黑色字体又需要 clear FLAG_TRANSLUCENT_STATUS, 因此还原为官方模式
        if (isFlymeLowerThan(8) || isMIUI() && Build.VERSION.SDK_INT < VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            )
            return
        }
        @Suppress("DEPRECATION")
        var systemUiVisibility = window.decorView.systemUiVisibility
        @Suppress("DEPRECATION")
        systemUiVisibility =
            systemUiVisibility or (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = systemUiVisibility
        if (Build.VERSION.SDK_INT >= VERSION_CODES.M && supportTransclentStatusBar6()) {
            // android 6以后可以改状态栏字体颜色，因此可以自行设置为透明
            // ZUK Z1是个另类，自家应用可以实现字体颜色变色，但没开放接口
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
        } else {
            // android 5不能修改状态栏字体颜色，因此直接用FLAG_TRANSLUCENT_STATUS，nexus表现为半透明
            // 魅族和小米的表现如何？
            // update: 部分手机运用FLAG_TRANSLUCENT_STATUS时背景不是半透明而是没有背景了。。。。。
//          window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

            // 采取setStatusBarColor的方式，部分机型不支持，那就纯黑了，保证状态栏图标可见
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = colorOn5x
        }
    }

    /**
     * 如果原本存在某一个flag， 就将它迁移到 out
     */
    fun retainSystemUiFlag(window: Window, out: Int, type: Int): Int {
        var realOut = out

        @Suppress("DEPRECATION")
        val now = window.decorView.systemUiVisibility
        if (now and type == type) {
            realOut = realOut or type
        }
        return realOut
    }

    @TargetApi(28)
    private fun handleDisplayCutoutMode(window: Window) {
        val decorView = window.decorView
        if (ViewCompat.isAttachedToWindow(decorView)) {
            realHandleDisplayCutoutMode(window, decorView)
        } else {
            decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    realHandleDisplayCutoutMode(window, v)
                }

                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
    }

    @TargetApi(28)
    private fun realHandleDisplayCutoutMode(window: Window, decorView: View) {
        if (decorView.rootWindowInsets != null &&
            decorView.rootWindowInsets.displayCutout != null
        ) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }
    }

    /**
     * 设置状态栏黑色字体图标，
     * 支持 4.4 以上版本 MIUI 和 Flyme，以及 6.0 以上版本的其他 Android
     *
     * @param activity 需要被处理的 Activity
     */
    fun setStatusBarLightMode(activity: Activity?): Boolean {
        if (activity == null) return false
        // 无语系列：ZTK C2016只能时间和电池图标变色。。。。
        if (isZTKC2016()) {
            return false
        }
        if (mStatusBarType != STATUSBAR_TYPE_DEFAULT) {
            return setStatusBarLightMode(activity, mStatusBarType)
        }
        if (isMIUICustomStatusBarLightModeImpl && MIUISetStatusBarLightMode(activity.window, true)) {
            mStatusBarType = STATUSBAR_TYPE_MIUI
            return true
        } else if (FlymeSetStatusBarLightMode(activity.window, true)) {
            mStatusBarType = STATUSBAR_TYPE_FLYME
            return true
        } else if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
            Android6SetStatusBarLightMode(activity.window, true)
            mStatusBarType = STATUSBAR_TYPE_ANDROID6
            return true
        }
        return false
    }

    /**
     * 已知系统类型时，设置状态栏黑色字体图标。
     * 支持 4.4 以上版本 MIUI 和 Flyme，以及 6.0 以上版本的其他 Android
     *
     * @param activity 需要被处理的 Activity
     * @param type     StatusBar 类型，对应不同的系统
     */
    private fun setStatusBarLightMode(activity: Activity, @StatusBarType type: Int): Boolean {
        return when (type) {
            STATUSBAR_TYPE_MIUI -> {
                MIUISetStatusBarLightMode(activity.window, true)
            }
            STATUSBAR_TYPE_FLYME -> {
                FlymeSetStatusBarLightMode(activity.window, true)
            }
            STATUSBAR_TYPE_ANDROID6 -> {
                Android6SetStatusBarLightMode(activity.window, true)
            }
            else -> false
        }
    }

    /**
     * 设置状态栏白色字体图标
     * 支持 4.4 以上版本 MIUI 和 Flyme，以及 6.0 以上版本的其他 Android
     */
    fun setStatusBarDarkMode(activity: Activity?): Boolean {
        if (activity == null) {
            return false
        }
        if (mStatusBarType == STATUSBAR_TYPE_DEFAULT) {
            // 默认状态，不需要处理
            return true
        }
        return when (mStatusBarType) {
            STATUSBAR_TYPE_MIUI -> {
                MIUISetStatusBarLightMode(activity.window, false)
            }
            STATUSBAR_TYPE_FLYME -> {
                FlymeSetStatusBarLightMode(activity.window, false)
            }
            STATUSBAR_TYPE_ANDROID6 -> {
                Android6SetStatusBarLightMode(activity.window, false)
            }
            else -> true
        }
    }

    /**
     * 设置状态栏字体图标为深色，Android 6
     *
     * @param window 需要设置的窗口
     * @param light  是否把状态栏字体及图标颜色设置为深色
     * @return boolean 成功执行返回true
     */
    @Suppress("DEPRECATION")
    @TargetApi(23)
    private fun Android6SetStatusBarLightMode(window: Window, light: Boolean): Boolean {
        val decorView = window.decorView
        var systemUi = decorView.systemUiVisibility
        systemUi = if (light) {
            systemUi or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            systemUi and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        decorView.systemUiVisibility = systemUi
        if (isMIUIV9()) {
            // MIUI 9 低于 6.0 版本依旧只能回退到以前的方案
            // https://github.com/Tencent/QMUI_Android/issues/160
            MIUISetStatusBarLightMode(window, light)
        }
        return true
    }

    /**
     * 设置状态栏字体图标为深色，需要 MIUIV6 以上
     *
     * @param window 需要设置的窗口
     * @param light  是否把状态栏字体及图标颜色设置为深色
     * @return boolean 成功执行返回 true
     */
    private fun MIUISetStatusBarLightMode(window: Window, light: Boolean): Boolean {
        var result = false
        val clazz: Class<*> = window.javaClass
        try {
            val darkModeFlag: Int

            @SuppressLint("PrivateApi")
            val layoutParams = Class.forName("android.view.MiuiWindowManager\$LayoutParams")
            val field = layoutParams.getField("EXTRA_FLAG_STATUS_BAR_DARK_MODE")
            darkModeFlag = field.getInt(layoutParams)

            val extraFlagField = clazz.getMethod(
                "setExtraFlags",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            if (light) {
                extraFlagField.invoke(window, darkModeFlag, darkModeFlag) //状态栏透明且黑色字体
            } else {
                extraFlagField.invoke(window, 0, darkModeFlag) //清除黑色字体
            }
            result = true
        } catch (ignored: Exception) {
        }
        return result
    }

    /**
     * 更改状态栏图标、文字颜色的方案是否是MIUI自家的， MIUI9 && Android 6 之后用回Android原生实现
     * 见小米开发文档说明：https://dev.mi.com/console/doc/detail?pId=1159
     */
    private val isMIUICustomStatusBarLightModeImpl: Boolean
        get() = if (isMIUIV9() && Build.VERSION.SDK_INT < VERSION_CODES.M) {
            true
        } else isMIUIV5() || isMIUIV6() ||
                isMIUIV7() || isMIUIV8()

    /**
     * 设置状态栏图标为深色和魅族特定的文字风格
     * 可以用来判断是否为 Flyme 用户
     *
     * @param window 需要设置的窗口
     * @param light  是否把状态栏字体及图标颜色设置为深色
     * @return boolean 成功执行返回true
     */
    private fun FlymeSetStatusBarLightMode(window: Window, light: Boolean): Boolean {
        var result = false
        Android6SetStatusBarLightMode(window, light)

        // flyme 在 6.2.0.0A 支持了 Android 官方的实现方案，旧的方案失效
        // 高版本调用这个出现不可预期的 Bug,官方文档也没有给出完整的高低版本兼容方案
        if (isFlymeLowerThan(7)) {
            try {
                val lp = window.attributes
                val darkFlag =
                    WindowManager.LayoutParams::class.java.getDeclaredField("MEIZU_FLAG_DARK_STATUS_BAR_ICON")
                val meizuFlags = WindowManager.LayoutParams::class.java.getDeclaredField("meizuFlags")
                darkFlag.isAccessible = true
                meizuFlags.isAccessible = true
                val bit = darkFlag.getInt(null)
                var value = meizuFlags.getInt(lp)
                value = if (light) {
                    value or bit
                } else {
                    value and bit.inv()
                }
                meizuFlags.setInt(lp, value)
                window.attributes = lp
                result = true
            } catch (ignored: Exception) {
            }
        } else if (isFlyme()) {
            result = true
        }
        return result
    }

    /**
     * 获取是否全屏
     *
     * @return 是否全屏
     */
    fun isFullScreen(activity: Activity): Boolean {
        var ret = false
        try {
            val attrs = activity.window.attributes
            @Suppress("DEPRECATION")
            ret = attrs.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ret
    }

    /**
     * API19之前透明状态栏：获取设置透明状态栏的system ui visibility的值，这是部分有提供接口的rom使用的
     * http://stackoverflow.com/questions/21865621/transparent-status-bar-before-4-4-kitkat
     */
    fun getStatusBarAPITransparentValue(context: Context): Int? {
        if (sTransparentValue != null) {
            return sTransparentValue
        }
        val systemSharedLibraryNames = context.packageManager.systemSharedLibraryNames
        var fieldName: String? = null
        if (systemSharedLibraryNames != null) {
            for (lib in systemSharedLibraryNames) {
                if ("touchwiz" == lib) {
                    fieldName = "SYSTEM_UI_FLAG_TRANSPARENT_BACKGROUND"
                } else if (lib.startsWith("com.sonyericsson.navigationbar")) {
                    fieldName = "SYSTEM_UI_FLAG_TRANSPARENT"
                }
            }
        }
        if (fieldName != null) {
            try {
                val field = View::class.java.getField(fieldName)
                val type = field.type
                if (type == Int::class.javaPrimitiveType) {
                    sTransparentValue = field.getInt(null)
                }
            } catch (ignored: Exception) {
            }
        }
        return sTransparentValue
    }

    /**
     * 检测 Android 6.0 是否可以启用 window.setStatusBarColor(Color.TRANSPARENT)。
     */
    fun supportTransclentStatusBar6(): Boolean {
        return !(isZUKZ1() || isZTKC2016())
    }

    /**
     * 获取状态栏的高度
     */
    fun getStatusbarHeight(context: Context): Int {
        if (sStatusBarHeight == -1) {
            initStatusBarHeight(context)
        }
        return sStatusBarHeight
    }

    @SuppressLint("PrivateApi")
    private fun initStatusBarHeight(context: Context) {
        val clazz: Class<*>
        var obj: Any? = null
        var field: Field? = null
        try {
            clazz = Class.forName("com.android.internal.R\$dimen")
            obj = clazz.newInstance()
            if (isMeizu()) {
                try {
                    field = clazz.getField("status_bar_height_large")
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
            if (field == null) {
                field = clazz.getField("status_bar_height")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        if (field != null && obj != null) {
            try {
                @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                val id = field[obj].toString().toInt()
                sStatusBarHeight = context.resources.getDimensionPixelSize(id)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        if (sStatusBarHeight <= 0) {
            sStatusBarHeight = if (sVirtualDensity == -1f) {
                (context.resources.displayMetrics.density * STATUS_BAR_DEFAULT_HEIGHT_DP + 0.5).toInt()
            } else {
                (STATUS_BAR_DEFAULT_HEIGHT_DP * sVirtualDensity + 0.5f).toInt()
            }
        }
    }

    fun setVirtualDensity(density: Float) {
        sVirtualDensity = density
    }

    fun setVirtualDensityDpi(densityDpi: Float) {
        sVirtualDensityDpi = densityDpi
    }

    @IntDef(STATUSBAR_TYPE_DEFAULT, STATUSBAR_TYPE_MIUI, STATUSBAR_TYPE_FLYME, STATUSBAR_TYPE_ANDROID6)
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    private annotation class StatusBarType

    /////////////////////////////////////////上面为 QMUI

    /**
     * 设置状态栏背景
     * v21
     * <item name="android:windowTranslucentStatus">false</item>
     * <item name="android:windowTranslucentNavigation">true</item>
     * <item name="android:statusBarColor">@android:color/transparent</item>
     */
    fun setStatusBarView(activity: Activity, @ColorRes statusBarColor: Int) {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            val decorView = activity.findViewById<View>(android.R.id.content) as FrameLayout
            val count = decorView.childCount
            if (count > 0) {
                val statusBarHeight = DeviceUtils.getStatusBarHeight()
                val layout = decorView.getChildAt(0)
                val layoutParams = layout.layoutParams as FrameLayout.LayoutParams
                layoutParams.topMargin = statusBarHeight
                val statusBarView: View
                if (count > 1) {
                    statusBarView = decorView.getChildAt(1) as View
                } else {
                    statusBarView = View(activity)
                    val viewParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, statusBarHeight
                    )
                    statusBarView.layoutParams = viewParams
                    decorView.addView(statusBarView)
                }
                statusBarView.setBackgroundResource(statusBarColor)
            }
        }
    }

    /**
     * 设置状态栏背景，一个Acitivty中只能调用一次，仅支持5.0以上
     * v21
     * <item name="android:windowTranslucentStatus">false</item>
     * <item name="android:windowTranslucentNavigation">true</item>
     * <item name="android:statusBarColor">@android:color/transparent</item>
     *
     * @param activity
     * @param drawable
     */
    fun setStatusBarDrawable(activity: Activity, drawable: Drawable?) {
        //7.0以上移除状态栏阴影
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                @SuppressLint("PrivateApi")
                val decorViewClazz = Class.forName("com.android.internal.policy.DecorView")
                val field = decorViewClazz.getDeclaredField("mSemiTransparentStatusBarColor")
                field.isAccessible = true
                field.setInt(activity.window.decorView, Color.TRANSPARENT) //改为透明
            } catch (e: Exception) {
            }
        }
        if (drawable == null) return
        if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            val rootView = activity.findViewById<View>(android.R.id.content) as FrameLayout
            val count = rootView.childCount
            if (count > 0) {
                val layout = rootView.getChildAt(0)
                val statusBarHeight = DeviceUtils.getStatusBarHeight()
                val layoutParams = layout.layoutParams as FrameLayout.LayoutParams
                layoutParams.topMargin = statusBarHeight
                val statusBarView: ImageView
                if (count > 1) {
                    statusBarView = rootView.getChildAt(1) as ImageView
                } else {
                    statusBarView = ImageView(activity)
                    val viewParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, statusBarHeight
                    )
                    statusBarView.scaleType = ImageView.ScaleType.FIT_XY
                    statusBarView.layoutParams = viewParams
                    rootView.addView(statusBarView)
                }
                statusBarView.setImageDrawable(drawable)
            }
        }
    }

    fun supportTransparentStatusBar(): Boolean {
        return (isMIUI()
                || isFlyme()
                || OSUtils.isOppo() && Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
    }

    /**
     * 设置状态栏颜色和透明度
     */
    fun setStatusBarColor(window: Window, @ColorInt color: Int, alpha: Int) {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = calculateStatusColor(color, alpha)
        }
    }

    /**
     * 计算状态栏颜色
     *
     * @param color color值
     * @param alpha alpha值
     * @return 最终的状态栏颜色
     */
    private fun calculateStatusColor(@ColorInt color: Int, alpha: Int): Int {
        if (alpha == 0) {
            return color
        }
        val a = 1 - alpha / 255f
        var red = color shr 16 and 0xff
        var green = color shr 8 and 0xff
        var blue = color and 0xff
        red = (red * a + 0.5).toInt()
        green = (green * a + 0.5).toInt()
        blue = (blue * a + 0.5).toInt()
        return 0xff shl 24 or (red shl 16) or (green shl 8) or blue
    }

    /**
     * 全屏
     *
     * @param activity 窗口
     */
    fun fullScreen(activity: Activity?) {
        if (activity == null) {
            return
        }
        fullScreen(activity.window)
    }

    /**
     * 全屏
     *
     * @param window 窗口
     */
    @SuppressLint("ObsoleteSdkInt")
    fun fullScreen(window: Window) {
        if (Build.VERSION.SDK_INT in (HONEYCOMB + 1) until KITKAT) { // lower api
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.GONE
        } else if (Build.VERSION.SDK_INT >= KITKAT) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }

    /**
     * 取消全屏
     *
     * @param activity           窗口
     * @param statusBarColor     状态栏的颜色
     * @param navigationBarColor 导航栏的颜色
     */
    fun cancelFullScreen(activity: Activity, @ColorInt statusBarColor: Int, @ColorInt navigationBarColor: Int) {
        cancelFullScreen(activity.window, statusBarColor, navigationBarColor)
    }

    /**
     * 取消全屏
     *
     * @param window             窗口
     * @param statusBarColor     状态栏的颜色
     * @param navigationBarColor 导航栏的颜色
     */
    fun cancelFullScreen(window: Window, @ColorInt statusBarColor: Int, @ColorInt navigationBarColor: Int) {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            @Suppress("DEPRECATION")
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                        or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )

            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            if (statusBarColor != -1) {
                window.statusBarColor = statusBarColor
            }
            if (navigationBarColor != -1) {
                window.navigationBarColor = navigationBarColor
            }
        }
    }

    /**
     * 取消全屏
     *
     * @param activity 窗口
     */
    fun cancelFullScreen(activity: Activity) {
        cancelFullScreen(activity.window)
    }

    /**
     * 取消全屏
     *
     * @param window 窗口
     */
    fun cancelFullScreen(window: Window) {
        cancelFullScreen(window, -1, -1)
    }

    /**
     * 设置底部导航条的颜色
     *
     * @param activity 窗口
     * @param color    颜色
     */
    @SuppressLint("ObsoleteSdkInt")
    fun setNavigationBarColor(activity: Activity, color: Int) {
        when {
            Build.VERSION.SDK_INT > VERSION_CODES.LOLLIPOP -> {
                //5.0以上可以直接设置 navigation颜色
                activity.window.navigationBarColor = color
            }
            Build.VERSION.SDK_INT >= KITKAT -> {
                @Suppress("DEPRECATION")
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                val decorView = activity.window.decorView as ViewGroup
                val navigationBar = View(activity)
                val params =
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, DeviceUtils.getNavBarHeight())
                params.gravity = Gravity.BOTTOM
                navigationBar.layoutParams = params
                navigationBar.setBackgroundColor(color)
                decorView.addView(navigationBar)
            }
            else -> {
                //4.4以下无法设置NavigationBar颜色
            }
        }
    }

    /**
     * 全屏下显示弹窗
     *
     * @param dialog 弹窗
     */
    fun showDialogInFullScreen(dialog: Dialog) {
        showWindowInFullScreen(dialog.window ?: return, object : OnWindowShowListener {
            override fun show(window: Window) {
                dialog.show()
            }
        })
    }

    /**
     * 全屏下显示窗口【包括dialog等】
     *
     * @param window 窗口
     */
    fun showWindowInFullScreen(window: Window, onWindowShowListener: OnWindowShowListener?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        onWindowShowListener?.show(window)
        fullScreen(window)
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    /**
     * 窗口显示接口
     */
    interface OnWindowShowListener {
        /**
         * 显示窗口
         *
         * @param window 窗口
         */
        fun show(window: Window)
    }

}