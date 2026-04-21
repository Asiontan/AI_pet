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
            CompanionMode.GENTLE_CARE, CompanionMode.EMOTIONAL_SUPPORT -> listOf("sad", "calm", "soft", "worry", "难过", "平静")
            CompanionMode.PLAYFUL_INTERACTION -> listOf("happy", "smile", "joy", "开心", "笑")
            CompanionMode.PROACTIVE_HELP -> listOf("calm", "normal", "平静", "自然")
            CompanionMode.DO_NOT_DISTURB, CompanionMode.QUIET_COMPANION -> listOf("calm", "idle", "normal", "平静", "待机")
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
            CompanionMode.GENTLE_CARE, CompanionMode.EMOTIONAL_SUPPORT -> listOf("idle", "relax", "calm", "breath", "待机", "放松")
            CompanionMode.PLAYFUL_INTERACTION -> listOf("happy", "wave", "dance", "开心", "挥手", "跳")
            CompanionMode.PROACTIVE_HELP -> listOf("wave", "point", "idle", "提醒", "指")
            CompanionMode.DO_NOT_DISTURB, CompanionMode.QUIET_COMPANION -> listOf("idle", "sleep", "calm", "待机", "睡")
        }
        return firstMatching(motions, keywords)
            ?: firstMatching(motions, moodKeywords(petMood))
            ?: motions.firstOrNull()
    }

    private fun moodKeywords(petMood: PetMood): List<String> = when (petMood) {
        PetMood.HAPPY, PetMood.EXCITED -> listOf("happy", "smile", "wave", "开心", "笑")
        PetMood.WORRIED, PetMood.SAD -> listOf("sad", "calm", "难过", "平静")
        PetMood.SLEEPY -> listOf("sleep", "idle", "睡", "待机")
        PetMood.LONELY, PetMood.SHY, PetMood.CALM -> listOf("calm", "idle", "normal", "平静", "待机")
    }

    private fun firstMatching(files: List<String>, keywords: List<String>): String? {
        val lower = files.map { it to it.lowercase() }
        keywords.forEach { keyword ->
            lower.firstOrNull { it.second.contains(keyword.lowercase()) }?.let { return it.first }
        }
        return null
    }
}
