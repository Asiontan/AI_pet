#!/bin/bash

# 创建核心模块文件
cat > app/src/main/java/com/pet/desktop/PetApplication.kt << 'ENDAPP'
package com.pet.desktop

import android.app.Application
import com.pet.core.common.logger.PetLogger

class PetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PetLogger.setDebugMode(true)
        PetLogger.d("PetApplication", "Pet started")
    }
}
ENDAPP

cat > core/core-domain/src/main/java/com/pet/core/domain/model/PetState.kt << 'ENDSTATE'
package com.pet.core.domain.model

data class PetState(
    val id: String = "default",
    val position: PetPosition = PetPosition(),
    val behaviorState: BehaviorState = BehaviorState.IDLE,
    val animationState: AnimationState = AnimationState.IDLE,
    val lastUpdateTimestamp: Long = System.currentTimeMillis()
)
ENDSTATE

cat > core/core-domain/src/main/java/com/pet/core/domain/model/AnimationState.kt << 'ENDANIM'
package com.pet.core.domain.model

enum class AnimationState {
    IDLE, WALK, CLICK, DRAG, SLEEP, WAKE_UP, LONG_PRESS, CUSTOM
}
ENDANIM

cat > core/core-domain/src/main/java/com/pet/core/domain/model/PetPosition.kt << 'ENDPOS'
package com.pet.core.domain.model

data class PetPosition(
    val x: Int = 0,
    val y: Int = 0,
    val isEdgeSnapped: Boolean = false
)
ENDPOS

cat > core/core-domain/src/main/java/com/pet/core/domain/model/BehaviorState.kt << 'ENDBEH'
package com.pet.core.domain.model

enum class BehaviorState {
    IDLE, WALK, DRAG, CLICK, LONG_PRESS, SLEEP, WAKE_UP

    fun getAnimationState(): AnimationState {
        return when (this) {
            IDLE -> AnimationState.IDLE
            WALK -> AnimationState.WALK
            DRAG -> AnimationState.DRAG
            CLICK -> AnimationState.CLICK
            LONG_PRESS -> AnimationState.LONG_PRESS
            SLEEP -> AnimationState.SLEEP
            WAKE_UP -> AnimationState.WAKE_UP
        }
    }
}
ENDBEH

echo "Core files created successfully!"
