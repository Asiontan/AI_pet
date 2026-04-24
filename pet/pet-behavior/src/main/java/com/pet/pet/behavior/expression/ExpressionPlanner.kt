package com.pet.pet.behavior.expression

import com.pet.core.data.model.ModelManager
import com.pet.core.domain.model.CompanionMode
import com.pet.core.domain.model.CompanionPolicy
import com.pet.core.domain.model.ExpressionPlan
import com.pet.core.domain.model.PetMindState
import com.pet.core.domain.model.PetMood
import com.pet.core.domain.model.RelationshipState
import com.pet.core.domain.model.UserState

class ExpressionPlanner(
    private val bubbleGenerator: PhaseAwareBubbleGenerator,
    private val microInterventionPlanner: MicroInterventionPlanner,
    private val nonverbalInterventionPlanner: NonverbalInterventionPlanner,
    private val interventionSequencer: InterventionSequencer
) {
    fun buildPlan(
        modelInfo: ModelManager.ModelInfo?,
        userState: UserState,
        petMindState: PetMindState,
        relationshipState: RelationshipState,
        policy: CompanionPolicy
    ): ExpressionPlan {
        val expressions = modelInfo?.expressions.orEmpty()
        val motions = modelInfo?.motions.orEmpty()

        val expression = findExpression(expressions, policy.mode, petMindState.mood)
        val motion = findMotion(motions, policy.mode, petMindState.mood)
        val bubbleText = bubbleGenerator.generate(userState, petMindState, relationshipState, policy)
        val intervention = microInterventionPlanner.plan(
            userState = userState,
            petMindState = petMindState,
            relationshipState = relationshipState,
            policy = policy,
            bubbleText = bubbleText
        )
        val nonverbal = nonverbalInterventionPlanner.plan(
            userState = userState,
            petMindState = petMindState,
            relationshipState = relationshipState,
            policy = policy,
            triggerBubble = intervention.shouldTriggerBubble
        )
        val sequence = interventionSequencer.plan(
            userState = userState,
            petMindState = petMindState,
            policy = policy,
            triggerBubble = intervention.shouldTriggerBubble,
            triggerNonverbalOnly = nonverbal.triggerNonverbalOnly
        )

        return ExpressionPlan(
            expressionName = expression,
            motionName = motion,
            bubbleText = bubbleText,
            triggerBubble = intervention.shouldTriggerBubble,
            clearExpression = expression == null && petMindState.mood == PetMood.CALM,
            bubbleDelayMs = intervention.bubbleDelayMs,
            interventionLevel = intervention.interventionLevel,
            triggerNonverbalOnly = nonverbal.triggerNonverbalOnly,
            motionDelayMs = nonverbal.motionDelayMs,
            expressionFirst = sequence.expressionFirst,
            sequenceGapMs = sequence.sequenceGapMs
        )
    }

    private fun findExpression(
        expressions: List<String>,
        mode: CompanionMode,
        petMood: PetMood
    ): String? {
        if (expressions.isEmpty()) return null
        val keywords = when (mode) {
            CompanionMode.GENTLE_CARE, CompanionMode.EMOTIONAL_SUPPORT -> listOf("sad", "calm", "soft", "worry", "comfort", "gentle", "tender", "难过", "平静", "温柔", "安慰", "轻柔")
            CompanionMode.PLAYFUL_INTERACTION -> listOf("happy", "smile", "joy", "laugh", "cheer", "wink", "play", "开心", "笑", "欢乐", "眨眼", "玩")
            CompanionMode.PROACTIVE_HELP -> listOf("calm", "normal", "alert", "remind", "nod", "平静", "自然", "提醒", "点头")
            CompanionMode.DO_NOT_DISTURB, CompanionMode.QUIET_COMPANION -> listOf("calm", "idle", "normal", "quiet", "still", "rest", "平静", "待机", "安静", "休息")
        }
        return firstMatching(expressions, keywords)
            ?: firstMatching(expressions, moodKeywords(petMood))
            ?: expressions.firstOrNull()
    }

    private fun findMotion(
        motions: List<String>,
        mode: CompanionMode,
        petMood: PetMood
    ): String? {
        if (motions.isEmpty()) return null
        val keywords = when (mode) {
            CompanionMode.GENTLE_CARE, CompanionMode.EMOTIONAL_SUPPORT -> listOf("idle", "relax", "calm", "breath", "sway", "comfort", "待机", "放松", "呼吸", "轻摇", "安抚")
            CompanionMode.PLAYFUL_INTERACTION -> listOf("happy", "wave", "dance", "bounce", "spin", "jump", "clap", "开心", "挥手", "跳", "转圈", "拍手", "蹦")
            CompanionMode.PROACTIVE_HELP -> listOf("wave", "point", "idle", "nod", "tap", "提醒", "指", "点头", "敲")
            CompanionMode.DO_NOT_DISTURB, CompanionMode.QUIET_COMPANION -> listOf("idle", "sleep", "calm", "rest", "doze", "待机", "睡", "休息", "打盹")
        }
        return firstMatching(motions, keywords)
            ?: firstMatching(motions, moodKeywords(petMood))
            ?: motions.firstOrNull()
    }

    private fun moodKeywords(petMood: PetMood): List<String> = when (petMood) {
        PetMood.HAPPY, PetMood.EXCITED -> listOf("happy", "smile", "wave", "laugh", "cheer", "dance", "joy", "开心", "笑", "欢乐", "雀跃")
        PetMood.WORRIED, PetMood.SAD -> listOf("sad", "calm", "worry", "tear", "sigh", "难过", "平静", "担忧", "叹气")
        PetMood.SLEEPY -> listOf("sleep", "idle", "doze", "yawn", "rest", "睡", "待机", "打哈欠", "休息")
        PetMood.LONELY, PetMood.SHY, PetMood.CALM -> listOf("calm", "idle", "normal", "gentle", "quiet", "平静", "待机", "温和", "安静")
    }

    private fun firstMatching(files: List<String>, keywords: List<String>): String? {
        val lower = files.map { it to it.lowercase() }
        keywords.forEach { keyword ->
            lower.firstOrNull { it.second.contains(keyword.lowercase()) }?.let { return it.first }
        }
        return null
    }
}
