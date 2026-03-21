package com.pet.algorithm.sentiment

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.pet.core.common.logger.PetLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于用户行为的情绪推断
 * 通过 UsageStatsManager 分析用户实际使用模式推断情绪状态
 * 情绪值：0-10（0=非常消极，10=非常积极）
 *
 * 注意：需要用户在「设置 → 有权查看使用情况的应用」中手动授权。
 * 未授权时降级为基于最近交互频率的简单打分。
 */
class BehaviorEmotionAnalyzer(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    // 社交类应用包名前缀
    private val socialAppPrefixes = listOf(
        "com.tencent.mm",       // 微信
        "com.tencent.mobileqq", // QQ
        "com.sina.weibo",       // 微博
        "com.zhihu",            // 知乎
        "com.douban",           // 豆瓣
        "com.twitter",
        "com.instagram",
        "com.facebook"
    )

    // 娱乐/视频类应用包名前缀
    private val entertainmentPrefixes = listOf(
        "com.ss.android.ugc",   // 抖音
        "tv.danmaku.bili",       // B站
        "com.youku",
        "com.iqiyi",
        "com.netflix",
        "com.youtube"
    )

    /**
     * 通过 PackageManager 获取应用的显示名称
     * - 优先从系统 PackageManager 读取
     * - 获取失败时查内置常见应用映射表
     * - 最终兜底直接返回包名
     */
    fun getAppName(packageName: String): String {
        // 先尝试从 PackageManager 获取
        try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val appInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
            } else {
                pm.getApplicationInfo(packageName, 0)
            }
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.isNotBlank() && label != packageName) return label
        } catch (_: Exception) {}

        // PackageManager 失败，查内置映射表
        return knownAppNames[packageName]
            ?: knownAppNames.entries.firstOrNull { packageName.startsWith(it.key) }?.value
            ?: packageName
    }

    /** 常见应用包名 → 名称映射表（用于 PackageManager 获取失败时兜底） */
    private val knownAppNames = mapOf(
        // 腾讯系
        "com.tencent.mm"              to "微信",
        "com.tencent.mobileqq"        to "QQ",
        "com.tencent.tim"             to "TIM",
        "com.tencent.qqlite"          to "QQ轻量版",
        "com.tencent.video"           to "腾讯视频",
        "com.tencent.qqmusic"         to "QQ音乐",
        // 字节跳动
        "com.ss.android.ugc.aweme"    to "抖音",
        "com.ss.android.article.news" to "今日头条",
        "com.zhiliaoapp.musically"    to "TikTok",
        // B站
        "tv.danmaku.bili"             to "哔哩哔哩",
        // 阿里系
        "com.taobao.taobao"           to "淘宝",
        "com.alibaba.android.rimet"   to "钉钉",
        "com.youku.phone"             to "优酷",
        "com.alipay.android.phone.moneydetail" to "支付宝",
        // 百度
        "com.baidu.searchbox"         to "百度",
        "com.iqiyi.video"             to "爱奇艺",
        // 网易
        "com.netease.cloudmusic"      to "网易云音乐",
        "com.netease.newsreader.activity" to "网易新闻",
        // 微博
        "com.sina.weibo"              to "微博",
        // 知乎
        "com.zhihu.android"           to "知乎",
        // 小红书
        "com.xingin.xhs"              to "小红书",
        // 系统应用
        "com.android.launcher"        to "桌面",
        "com.android.settings"        to "设置",
        "com.android.camera"          to "相机",
        "com.android.phone"           to "电话",
        "com.android.contacts"        to "联系人",
        "com.android.mms"             to "短信",
        "com.android.chrome"          to "Chrome",
        // 国内厂商桌面
        "com.miui.home"               to "桌面(MIUI)",
        "com.huawei.android.launcher" to "桌面(华为)",
        "com.oppo.launcher"           to "桌面(OPPO)",
        "com.vivo.launcher"           to "桌面(vivo)",
        "com.bbk.launcher2"           to "桌面(vivo)",
        "com.samsung.android.app.launcher" to "桌面(三星)",
        // 国际应用
        "com.google.android.youtube"  to "YouTube",
        "com.netflix.mediaclient"     to "Netflix",
        "com.instagram.android"       to "Instagram",
        "com.twitter.android"         to "Twitter/X",
        "com.facebook.katana"         to "Facebook",
        "com.spotify.music"           to "Spotify"
    )

    /**
     * 检查 PACKAGE_USAGE_STATS 是否已被用户授权
     * （该权限即使在 Manifest 中声明也必须用户手动开启）
     */
    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            PetLogger.e("BehaviorEmotionAnalyzer", "hasUsageStatsPermission check failed", e)
            false
        }
    }

    /**
     * 分析用户行为特征，推断情绪，返回情绪值 0-10
     *
     * - 有 UsageStats 权限：基于 App 切换、使用时长、社交/娱乐占比综合打分
     * - 无权限：降级为固定中性值 5，并打印警告日志提示用户授权
     */
    /**
     * 获取用户当前（最近）正在使用的应用包名。
     * 通过查询过去 3 秒内 lastTimeUsed 最新的记录来推断。
     * 需要 PACKAGE_USAGE_STATS 权限；未授权或查询失败时返回 null。
     */
    fun getCurrentForegroundApp(): String? {
        if (!hasUsageStatsPermission()) return null
        return try {
            val endTime = System.currentTimeMillis()
            // 查询窗口扩大到10秒，避免系统未及时刷新导致3秒窗口内无数据
            val startTime = endTime - 10_000L
            var stats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, startTime, endTime
            )
            // 若10秒内仍无数据，退一步查过去1分钟，取最近使用的应用
            if (stats.isNullOrEmpty()) {
                stats = usageStatsManager?.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST,
                    endTime - 60_000L,
                    endTime
                )
            }
            if (stats.isNullOrEmpty()) return null
            val current = stats
                .filter { it.lastTimeUsed > 0 && it.packageName != context.packageName }
                .maxByOrNull { it.lastTimeUsed }
                ?.packageName
            val appName = current?.let { getAppName(it) } ?: "null"
            PetLogger.d("BehaviorEmotionAnalyzer", "Current foreground app: $current ($appName)")
            current
        } catch (e: Exception) {
            PetLogger.e("BehaviorEmotionAnalyzer", "getCurrentForegroundApp failed", e)
            null
        }
    }

    suspend fun analyzeBehaviorEmotion(): Int = withContext(Dispatchers.IO) {
        if (!hasUsageStatsPermission()) {
            PetLogger.w("BehaviorEmotionAnalyzer",
                "PACKAGE_USAGE_STATS not granted. Please enable in Settings > Usage access. Returning neutral emotion=5")
            return@withContext 5
        }

        try {
            val endTime = System.currentTimeMillis()
            // 采集过去1小时的数据（测试时可改短，但不建议低于10分钟，否则数据太少）
            val startTime = endTime - 60 * 60 * 1000L

            val stats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, startTime, endTime
            ) ?: emptyList()

            if (stats.isEmpty()) {
                PetLogger.w("BehaviorEmotionAnalyzer", "UsageStats returned empty list, returning neutral")
                return@withContext 5
            }

            // 特征1：时间窗口内有使用记录的 App 数量（近似 App 切换次数）
            val appSwitchCount = stats.count { it.lastTimeUsed >= startTime }

            // 特征2：总屏幕使用时长（分钟）
            val totalUsageMs = stats.sumOf { it.totalTimeInForeground }
            val totalUsageMin = totalUsageMs / 60_000L

            // 特征3：社交应用使用时长占比
            val socialUsageMs = stats
                .filter { stat -> socialAppPrefixes.any { stat.packageName.startsWith(it) } }
                .sumOf { it.totalTimeInForeground }
            val socialRatio = if (totalUsageMs > 0) socialUsageMs.toFloat() / totalUsageMs else 0f

            // 特征4：娱乐/视频类应用使用时长占比
            val entertainmentUsageMs = stats
                .filter { stat -> entertainmentPrefixes.any { stat.packageName.startsWith(it) } }
                .sumOf { it.totalTimeInForeground }
            val entertainmentRatio =
                if (totalUsageMs > 0) entertainmentUsageMs.toFloat() / totalUsageMs else 0f

            // 综合评分（起点 5 = 中性）
            var emotionScore = 5

            // App 切换频率影响
            when {
                appSwitchCount > 20 -> emotionScore -= 2  // 频繁切换，可能烦躁
                appSwitchCount > 15 -> emotionScore -= 1
                appSwitchCount < 5  -> emotionScore += 1  // 专注，状态可能较好
            }

            // 使用时长影响
            when {
                totalUsageMin > 50 -> emotionScore -= 1  // 长时间使用，可能疲惫
                totalUsageMin in 20..40 -> emotionScore += 1  // 适度使用
                totalUsageMin < 5 -> emotionScore -= 1   // 几乎不用，可能忙碌/烦躁
            }

            // 社交活跃度影响
            when {
                socialRatio > 0.5f -> emotionScore += 2
                socialRatio > 0.3f -> emotionScore += 1
            }

            // 娱乐放松影响
            if (entertainmentRatio > 0.4f) emotionScore += 1

            val finalScore = emotionScore.coerceIn(0, 10)
            PetLogger.d("BehaviorEmotionAnalyzer",
                "Emotion=$finalScore apps=$appSwitchCount usage=${totalUsageMin}min " +
                "social=${"%.2f".format(socialRatio)} " +
                "entertainment=${"%.2f".format(entertainmentRatio)}")
            return@withContext finalScore
        } catch (e: Exception) {
            PetLogger.e("BehaviorEmotionAnalyzer", "Failed to analyze behavior emotion", e)
            return@withContext 5
        }
    }
}
