package com.pet.pet.behavior.expression

import com.pet.core.domain.model.CompanionPolicy
import com.pet.core.domain.model.PetMindState
import com.pet.core.domain.model.RelationshipState
import com.pet.core.domain.model.UserState
import com.pet.pet.behavior.relationship.RelationshipPhaseManager

class PhaseAwareBubbleGenerator(
    private val baseGenerator: MemoryAwareBubbleGenerator
) {
    fun generate(
        userState: UserState,
        petMindState: PetMindState,
        relationshipState: RelationshipState,
        policy: CompanionPolicy
    ): String? {
        val base = baseGenerator.generate(userState, petMindState, relationshipState, policy) ?: return null
        return when (phaseOf(relationshipState)) {
            RelationshipPhaseManager.Phase.ACQUAINTANCE -> soften(base)
            RelationshipPhaseManager.Phase.FAMILIAR -> naturalize(base)
            RelationshipPhaseManager.Phase.ATTACHED -> warm(base)
            RelationshipPhaseManager.Phase.TUNED -> tacit(base)
        }
    }

    private fun phaseOf(relationshipState: RelationshipState): RelationshipPhaseManager.Phase {
        return runCatching { RelationshipPhaseManager.Phase.valueOf(relationshipState.phase) }
            .getOrDefault(RelationshipPhaseManager.Phase.ACQUAINTANCE)
    }

    private fun soften(text: String): String {
        return when {
            text.startsWith("你") -> "我想轻轻提醒你，${text}"
            text.startsWith("今天") -> "想跟你说一声，${text}"
            else -> "如果你愿意的话，${text}"
        }
    }

    private fun naturalize(text: String): String {
        return text
            .replace("我会待在这里", "我就在这儿")
            .replace("不着急说话", "先不用急着说话")
    }

    private fun warm(text: String): String {
        return when {
            "陪着你" in text -> text.replace("陪着你", "一直陪着你")
            "我会" in text -> text.replaceFirst("我会", "我还是会")
            else -> "$text 我在。"
        }
    }

    private fun tacit(text: String): String {
        return text
            .replace("如果现在不想说话也没关系，", "")
            .replace("有需要的时候你再叫我", "你需要时叫我就好")
            .replace("我会先", "我先")
    }
}
