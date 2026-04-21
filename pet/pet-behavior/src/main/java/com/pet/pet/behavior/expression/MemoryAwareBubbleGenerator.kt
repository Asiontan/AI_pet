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
    fun generate(
        userState: UserState,
        petMindState: PetMindState,
        relationshipState: RelationshipState,
        policy: CompanionPolicy
    ): String? {
        return when (policy.mode) {
            CompanionMode.GENTLE_CARE -> gentleCareText(userState, relationshipState)
            CompanionMode.PROACTIVE_HELP -> proactiveHelpText(userState, relationshipState)
            CompanionMode.PLAYFUL_INTERACTION -> playfulText(userState, petMindState, relationshipState)
            CompanionMode.EMOTIONAL_SUPPORT -> emotionalSupportText(userState, relationshipState)
            CompanionMode.QUIET_COMPANION,
            CompanionMode.DO_NOT_DISTURB -> null
        }
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

        return when {
            familiarity >= 7 && chatCount >= 3 && userState.mood == UserMood.STRESSED ->
                "${hourHint}你这几天好像一直没怎么放松，我先安静陪着你。"
            streak >= 3 && userState.mood == UserMood.SAD ->
                "${hourHint}这几天你都会来见我，如果现在不想说话也没关系，我会待在这里。"
            ignoredCare >= 2 ->
                "${hourHint}前几次你好像都在忙，我这次就轻一点陪着你，不多打扰。"
            neglect >= 35 ->
                "${hourHint}好像有一阵子没认真陪你了，今天我先不打扰，只陪着你。"
            else ->
                "${hourHint}你今天看起来有点累，我先陪着你，不着急说话。"
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
        return when {
            userState.lifeContext == LifeContext.LATE_NIGHT && lateNightCount >= 2 ->
                "你最近总是很晚才来，我会陪你，但今天也早点休息吧。"
            userState.lifeContext == LifeContext.LATE_NIGHT ->
                "已经很晚了，今天先休息一下吧，我明天还会在。"
            onlineMinutes >= 180 && familiarity >= 5 ->
                "你今天已经忙了挺久，休息两分钟也没关系，我帮你记着进度。"
            else ->
                "工作久了记得休息一下，也别忘了喝水。"
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
        return when {
            relationshipState.intimacyLevel >= 70 && dailyInteractions >= 5 ->
                "你今天状态不错，我们已经互动好几次啦，要不要再陪我一下？"
            relationshipState.familiarityDays >= 5 && userState.mood == UserMood.HAPPY ->
                "今天的你看起来特别有精神，我也想跟着开心一点。"
            clicks >= 3 ->
                "你今天已经点我好几次啦，我有点得意了。"
            else ->
                "今天心情好像不错，我也有精神啦。"
        }
    }

    private fun emotionalSupportText(
        userState: UserState,
        relationshipState: RelationshipState
    ): String? {
        val trust = preferences.getPetTrust()
        val conversations = preferences.getMeaningfulConversationCount()
        val lastHour = preferences.getLastInteractionHour()
        return when {
            relationshipState.intimacyLevel >= 60 && trust >= 60 && conversations >= 2 ->
                "这段时间我会更安静一点陪着你，有需要的时候你再叫我。"
            userState.lifeContext == LifeContext.LATE_NIGHT && lastHour in 23..23 || lastHour in 0..5 ->
                "夜深了，我会把声音放轻一点，陪你慢慢缓下来。"
            preferences.getPassiveDayCount() >= 2 ->
                "这两天你好像一直很忙，我就先安静待在你身边。"
            else ->
                "我会先待在你身边，不急着让你回应我。"
        }
    }

    private fun timeHint(context: LifeContext): String = when (context) {
        LifeContext.MORNING -> "早上好，"
        LifeContext.LATE_NIGHT -> "这么晚了，"
        LifeContext.WORKING, LifeContext.STUDYING -> "先缓一缓，"
        else -> ""
    }
}
