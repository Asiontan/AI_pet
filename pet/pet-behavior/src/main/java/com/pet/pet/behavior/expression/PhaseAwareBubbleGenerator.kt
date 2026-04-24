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
        val prefix = when {
            text.startsWith("你") -> listOf("我想轻轻提醒你，", "小声说一下，", "嗯……").random()
            text.startsWith("今天") -> listOf("想跟你说一声，", "顺便提一嘴，", "话说，").random()
            text.startsWith("好") -> listOf("那个……", "我想了想，").random()
            text.startsWith("又") || text.startsWith("连") -> listOf("不是催你啦，", "别紧张，").random()
            else -> listOf("如果你愿意的话，", "不打扰的话，", "方便的话，").random()
        }
        return "$prefix$text"
    }

    private fun naturalize(text: String): String {
        return text
            .replace("我会待在这里", "我就在这儿")
            .replace("不着急说话", "先不用急着说话")
            .replace("安静陪着你", "陪你待一会儿")
            .replace("我先陪着你", "我就在旁边")
            .replace("不多打扰", "不烦你")
            .replace("记得休息", "该歇会儿了")
            .replace("先休息一下吧", "歇歇吧")
    }

    private fun warm(text: String): String {
        return when {
            "陪着你" in text -> text.replace("陪着你", "一直陪着你")
            "我会" in text -> text.replaceFirst("我会", "我还是会")
            "待在" in text -> text.replace("待在你身边", "守在你身边")
            "没关系" in text -> text.replace("没关系", "真的没关系")
            "不用" in text -> text.replaceFirst("不用", "真的不用")
            else -> "$text 我在。"
        }
    }

    private fun tacit(text: String): String {
        return text
            .replace("如果现在不想说话也没关系，", "")
            .replace("有需要的时候你再叫我", "你需要时叫我就好")
            .replace("我会先", "我先")
            .replace("你今天看起来有点累，", "")
            .replace("好像有一阵子没认真陪你了，", "")
            .replace("你不用一个人扛着，", "")
            .replace("不急着让你回应我", "你不用回应")
            .replace("我知道你需要空间，", "")
    }
}
