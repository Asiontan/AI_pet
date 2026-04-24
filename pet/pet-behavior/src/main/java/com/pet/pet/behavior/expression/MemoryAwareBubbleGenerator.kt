package com.pet.pet.behavior.expression

import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.CompanionMode
import com.pet.core.domain.model.CompanionPolicy
import com.pet.core.domain.model.LifeContext
import com.pet.core.domain.model.PetMindState
import com.pet.core.domain.model.RelationshipState
import com.pet.core.domain.model.UserMood
import com.pet.core.domain.model.UserState

class MemoryAwareBubbleGenerator(
    private val preferences: PetPreferences
) {
    private val entertainmentBubbleCooldownMs = 4 * 60_000L

    fun generate(
        userState: UserState,
        petMindState: PetMindState,
        relationshipState: RelationshipState,
        policy: CompanionPolicy
    ): String? {
        entertainmentInterrupt(userState)?.let { return it }
        return when (policy.mode) {
            CompanionMode.GENTLE_CARE -> gentleCareText(userState, relationshipState)
            CompanionMode.PROACTIVE_HELP -> proactiveHelpText(userState, relationshipState)
            CompanionMode.PLAYFUL_INTERACTION -> playfulText(userState, petMindState, relationshipState)
            CompanionMode.EMOTIONAL_SUPPORT -> emotionalSupportText(userState, relationshipState)
            CompanionMode.QUIET_COMPANION,
            CompanionMode.DO_NOT_DISTURB -> null
        }
    }

    /**
     * 刷视频/玩游戏时，偶尔插入一条轻量互动气泡。
     * 用本地时间戳做简单冷却，避免每 30 秒都弹。
     */
    private fun entertainmentInterrupt(userState: UserState): String? {
        if (userState.lifeContext != LifeContext.RESTING) return null
        val now = System.currentTimeMillis()
        val last = preferences.getLong("last_entertainment_bubble_ts", 0L)
        if (now - last < entertainmentBubbleCooldownMs) return null
        // 心情很糟/压力大时不打扰（让 gentle/emotional 逻辑接管）
        if (userState.mood in listOf(UserMood.SAD, UserMood.STRESSED)) return null

        val hourHint = timeHint(userState.lifeContext)
        val msg = listOf(
            "${hourHint}在刷视频呀？给我留个小角落嘛~",
            "${hourHint}这个看起来好有趣！要不要也分我一点快乐？",
            "${hourHint}你是不是又沉浸了？眨眨眼、放松一下肩膀~",
            "${hourHint}玩游戏的话我给你加油！别忘了喝水哦。",
            "${hourHint}打到哪一关啦？我在旁边当应援团！",
            "${hourHint}刷着刷着就过去好久了……要不要休息 1 分钟？"
        ).random()

        preferences.putLong("last_entertainment_bubble_ts", now)
        return msg
    }

    private fun gentleCareText(
        userState: UserState,
        relationshipState: RelationshipState
    ): String? {
        if (userState.mood !in listOf(UserMood.SAD, UserMood.STRESSED, UserMood.TIRED)) return null
        val hourHint = timeHint(userState.lifeContext)
        val familiarity = relationshipState.familiarityDays
        val neglect = preferences.getNeglectLevel()
        val chatCount = preferences.getTotalChatCount()
        val streak = preferences.getInteractionStreakDays()
        val ignoredCare = preferences.getIgnoredCareCount()
        val bond = preferences.getBondLevel()

        return when {
            familiarity >= 7 && chatCount >= 3 && userState.mood == UserMood.STRESSED ->
                listOf(
                    "${hourHint}你这几天好像一直没怎么放松，我先安静陪着你。",
                    "${hourHint}最近压力好大吧？不用跟我说什么，我在就好。",
                    "${hourHint}感觉你撑了好久了，今天就让自己歇一歇吧。"
                ).random()
            streak >= 3 && userState.mood == UserMood.SAD ->
                listOf(
                    "${hourHint}这几天你都会来见我，如果现在不想说话也没关系，我会待在这里。",
                    "${hourHint}你连续来了好几天了，不开心的话就靠一会儿吧。",
                    "${hourHint}谢谢你每天都来看我，难过的时候不用硬撑着。"
                ).random()
            ignoredCare >= 2 ->
                listOf(
                    "${hourHint}前几次你好像都在忙，我这次就轻一点陪着你，不多打扰。",
                    "${hourHint}我知道你最近很忙，我就安安静静在这里。",
                    "${hourHint}之前打扰你了吧？这次我小声一点。"
                ).random()
            neglect >= 35 ->
                listOf(
                    "${hourHint}好像有一阵子没认真陪你了，今天我先不打扰，只陪着你。",
                    "${hourHint}好久没见了，我就不催你了，待在旁边就好。",
                    "${hourHint}虽然很久没来，但你来了我就很开心了。"
                ).random()
            bond >= 50 && userState.mood == UserMood.TIRED ->
                "${hourHint}辛苦了，你做得已经很好了，休息一下吧。"
            userState.mood == UserMood.SAD && familiarity >= 3 ->
                "${hourHint}难过的话可以跟我说说，说不出来也没关系。"
            else ->
                listOf(
                    "${hourHint}你今天看起来有点累，我先陪着你，不着急说话。",
                    "${hourHint}感觉你有点低落，我就安静待在这儿。",
                    "${hourHint}没事的，慢慢来，我又不会跑掉。"
                ).random()
        }
    }

    private fun proactiveHelpText(
        userState: UserState,
        relationshipState: RelationshipState
    ): String? {
        if (userState.mood != UserMood.TIRED) return null
        val onlineMinutes = preferences.getTotalOnlineMinutes()
        val familiarity = relationshipState.familiarityDays
        val lateNightCount = preferences.getLateNightInteractionCount()
        val bond = preferences.getBondLevel()
        return when {
            userState.lifeContext == LifeContext.LATE_NIGHT && lateNightCount >= 2 ->
                listOf(
                    "你最近总是很晚才来，我会陪你，但今天也早点休息吧。",
                    "又是深夜呀……我不催你，但眼睛会受不了的。",
                    "连续几个晚上了，今天试试早点睡好不好？"
                ).random()
            userState.lifeContext == LifeContext.LATE_NIGHT ->
                listOf(
                    "已经很晚了，今天先休息一下吧，我明天还会在。",
                    "夜深了，该放下手机啦，明天见~",
                    "月亮都出来很久了哦，让眼睛休息一下吧。"
                ).random()
            onlineMinutes >= 180 && familiarity >= 5 ->
                listOf(
                    "你今天已经忙了挺久，休息两分钟也没关系，我帮你记着进度。",
                    "三个小时了哦！站起来走走，我等你回来。",
                    "盯屏幕太久了，看看窗外，远处的绿色对眼睛好~"
                ).random()
            bond >= 40 && onlineMinutes >= 120 ->
                listOf(
                    "你已经很努力了，喝口水缓一缓吧。",
                    "两个小时了呢，揉揉肩膀再继续吧。"
                ).random()
            else ->
                listOf(
                    "工作久了记得休息一下，也别忘了喝水。",
                    "别一直低头看手机哦，脖子会酸的~",
                    "偶尔眨眨眼、伸伸腰，对身体好~"
                ).random()
        }
    }

    private fun playfulText(
        userState: UserState,
        petMindState: PetMindState,
        relationshipState: RelationshipState
    ): String? {
        if (petMindState.desireToInteract <= 65) return null
        val clicks = preferences.getTodayClickCount()
        val dailyInteractions = preferences.getDailyInteractionCount()
        val bond = preferences.getBondLevel()
        return when {
            relationshipState.intimacyLevel >= 70 && dailyInteractions >= 5 ->
                listOf(
                    "你今天状态不错，我们已经互动好几次啦，要不要再陪我一下？",
                    "今天玩得好开心！还想再多待一会儿~",
                    "我觉得今天是我们的专属快乐日！再来一次嘛~"
                ).random()
            relationshipState.familiarityDays >= 5 && userState.mood == UserMood.HAPPY ->
                listOf(
                    "今天的你看起来特别有精神，我也想跟着开心一点。",
                    "好喜欢你开心的样子！我尾巴都要摇起来了~",
                    "嘻嘻，看到你笑我也忍不住了。"
                ).random()
            clicks >= 3 ->
                listOf(
                    "你今天已经点我好几次啦，我有点得意了。",
                    "又戳我！再戳我就要翻肚皮了哦！",
                    "哇，被你点了这么多次，我是不是特别受欢迎？"
                ).random()
            bond >= 60 ->
                listOf(
                    "嘿，我们的默契越来越好了呢！",
                    "你在的时候，我总是特别有活力~"
                ).random()
            dailyInteractions >= 2 ->
                listOf(
                    "今天已经聊了好几次了，感觉我们越来越亲了！",
                    "哈哈，你今天好有空呀，我好开心~"
                ).random()
            else ->
                listOf(
                    "今天心情好像不错，我也有精神啦。",
                    "嘻嘻，想找你玩~你有空吗？",
                    "我想到一件有趣的事——就是跟你说话！",
                    "无聊的时候戳戳我呀，我随时在的~"
                ).random()
        }
    }

    private fun emotionalSupportText(
        userState: UserState,
        relationshipState: RelationshipState
    ): String? {
        val trust = preferences.getPetTrust()
        val conversations = preferences.getMeaningfulConversationCount()
        val lastHour = preferences.getLastInteractionHour()
        val bond = preferences.getBondLevel()
        return when {
            relationshipState.intimacyLevel >= 60 && trust >= 60 && conversations >= 2 ->
                listOf(
                    "这段时间我会更安静一点陪着你，有需要的时候你再叫我。",
                    "我知道你需要空间，我会在这里等你准备好。",
                    "你不用假装没事，在我面前可以做真实的自己。"
                ).random()
            userState.lifeContext == LifeContext.LATE_NIGHT && (lastHour in 23..23 || lastHour in 0..5) ->
                listOf(
                    "夜深了，我会把声音放轻一点，陪你慢慢缓下来。",
                    "夜晚总是容易多想……我陪你，不用怕。",
                    "深夜的情绪总是更浓，但天亮了一切都会好起来的。"
                ).random()
            preferences.getPassiveDayCount() >= 2 ->
                listOf(
                    "这两天你好像一直很忙，我就先安静待在你身边。",
                    "连着几天没怎么互动了，但我一直在哦。",
                    "你忙你的，我等你有空了再来找我就好。"
                ).random()
            bond >= 50 && userState.mood == UserMood.SAD ->
                listOf(
                    "想哭就哭吧，我不会告诉别人的。",
                    "不开心的时候可以靠着我，我虽然小但很稳的。"
                ).random()
            trust >= 40 ->
                listOf(
                    "你不用一个人扛着，有我在呢。",
                    "就算什么都不说，我也想陪着你。"
                ).random()
            else ->
                listOf(
                    "我会先待在你身边，不急着让你回应我。",
                    "没关系的，慢慢来，我不着急。",
                    "你可以什么都不做，我就静静待着就好。",
                    "发呆也好，叹气也好，我都陪你。"
                ).random()
        }
    }

    private fun timeHint(context: LifeContext): String = when (context) {
        LifeContext.MORNING -> listOf("早上好，", "新的一天，", "早呀，").random()
        LifeContext.LATE_NIGHT -> listOf("这么晚了，", "夜深了，", "都这个点了，").random()
        LifeContext.WORKING, LifeContext.STUDYING -> listOf("先缓一缓，", "忙了一阵了，", "暂停一下，").random()
        else -> ""
    }
}
