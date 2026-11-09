package com.wgo.navigator.ai

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wgo.navigator.metrics.WgoMetrics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Where the daily remote-call counter lives (DataStore in the app, memory in tests). */
interface AiCounterStore {
    /** The stored (local date "yyyy-MM-dd", count), or null when nothing was stored yet. */
    suspend fun load(): Pair<String, Int>?
    suspend fun save(day: String, count: Int)
}

/**
 * Free-tier guards for the remote interpreter: after HTTP 429 (or any rate-limit error) no remote call is made for
 * [cooldownMs]; and at most [dailyCap] calls per local calendar day (the counter is persisted and resets at local
 * midnight). When a guard says no, the caller uses the local parser only. Never loops or retries.
 */
class AiCallBudget(
    private val store: AiCounterStore,
    private val dailyCap: Int = DAILY_CAP,
    private val cooldownMs: Long = COOLDOWN_MS,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val today: () -> String = { LocalDate.now().toString() }
) {
    private val mutex = Mutex()

    @Volatile
    private var cooldownUntil = 0L

    /** Takes one call from today's budget; false while cooling down or when the daily cap is reached. */
    suspend fun tryAcquire(): Boolean = mutex.withLock {
        if (nowMs() < cooldownUntil) {
            WgoMetrics.inc(WgoMetrics.Counter.AI_COOLDOWN)
            WgoMetrics.event("ai_skip reason=cooldown")
            return@withLock false
        }
        val day = today()
        val stored = store.load()
        val used = if (stored?.first == day) stored.second else 0
        if (used >= dailyCap) {
            WgoMetrics.event("ai_skip reason=daily_cap used=$used")
            return@withLock false
        }
        store.save(day, used + 1)
        true
    }

    /** The provider said "too many requests": stop remote calls for the cool-down. */
    fun onRateLimited() {
        cooldownUntil = nowMs() + cooldownMs
        WgoMetrics.event("ai_cooldown_start ms=$cooldownMs")
    }

    fun coolingDown(): Boolean = nowMs() < cooldownUntil

    companion object {
        const val DAILY_CAP = 200
        const val COOLDOWN_MS = 60_000L
    }
}

private val Context.aiQuotaStore: DataStore<Preferences> by preferencesDataStore(name = "wgo_ai_quota")

/** The persisted counter (one tiny DataStore file, separate from the settings). */
class DataStoreAiCounterStore(context: Context) : AiCounterStore {
    private val store = context.applicationContext.aiQuotaStore

    override suspend fun load(): Pair<String, Int>? {
        val prefs = store.data.first()
        val day = prefs[DAY] ?: return null
        return day to (prefs[COUNT] ?: 0)
    }

    override suspend fun save(day: String, count: Int) {
        store.edit {
            it[DAY] = day
            it[COUNT] = count
        }
    }

    private companion object {
        val DAY = stringPreferencesKey("ai_day")
        val COUNT = intPreferencesKey("ai_count")
    }
}
