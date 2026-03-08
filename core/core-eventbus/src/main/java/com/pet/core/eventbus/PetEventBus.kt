package com.pet.core.eventbus

import com.pet.core.common.logger.PetLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 轻量级事件总线，用于模块间通信
 * 支持同步和异步事件分发
 */
object PetEventBus {
    private val subscribers = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<Subscriber>>()
    private val stickyEvents = ConcurrentHashMap<Class<*>, Any>()

    /**
     * 订阅事件
     */
    fun <T> subscribe(eventType: Class<T>, subscriber: (T) -> Unit): Subscription {
        val subscription = Subscription(eventType)
        subscribers.getOrPut(eventType) { CopyOnWriteArrayList() }
            .add(
                Subscriber(subscription) { anyEvent ->
                    @Suppress("UNCHECKED_CAST")
                    subscriber(anyEvent as T)
                }
            )
        return subscription
    }

    /**
     * 订阅粘性事件（可以接收已发送的事件）
     */
    fun <T> subscribeSticky(eventType: Class<T>, subscriber: (T) -> Unit): Subscription {
        val subscription = subscribe(eventType, subscriber)
        // 如果有已发送的粘性事件，立即触发
        stickyEvents[eventType]?.let { event ->
            try {
                @Suppress("UNCHECKED_CAST")
                subscriber(event as T)
            } catch (e: Exception) {
                PetLogger.e("PetEventBus", "Error handling sticky event", e)
            }
        }
        return subscription
    }

    /**
     * 发布事件（同步）
     */
    fun post(event: Any) {
        val eventType = event::class.java
        subscribers[eventType]?.forEach { subscriber ->
            try {
                subscriber.handler(event)
            } catch (e: Exception) {
                PetLogger.e("PetEventBus", "Error handling event", e)
            }
        }
    }

    /**
     * 发布粘性事件
     */
    fun postSticky(event: Any) {
        val eventType = event::class.java
        stickyEvents[eventType] = event
        post(event)
    }

    /**
     * 取消订阅
     */
    fun unsubscribe(subscription: Subscription) {
        subscribers[subscription.eventType]?.removeIf { it.subscription == subscription }
    }

    /**
     * 清除粘性事件
     */
    fun <T> removeStickyEvent(eventType: Class<T>) {
        stickyEvents.remove(eventType)
    }

    /**
     * 清除所有订阅
     */
    fun clear() {
        subscribers.clear()
        stickyEvents.clear()
    }

    private data class Subscriber(
        val subscription: Subscription,
        val handler: (Any) -> Unit
    )
}

/**
 * 订阅对象，用于取消订阅
 */
data class Subscription(
    val eventType: Class<*>
)

