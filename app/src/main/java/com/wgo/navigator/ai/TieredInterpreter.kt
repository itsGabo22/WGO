package com.wgo.navigator.ai

import android.content.Context
import com.wgo.navigator.BuildConfig
import com.wgo.navigator.Language
import com.wgo.navigator.core.IntentInterpreter
import com.wgo.navigator.core.IntentKind
import com.wgo.navigator.core.Interpretation
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.net.HttpClient
import com.wgo.navigator.net.isOnline

/** Which tier produced the last answer (debug metric). */
enum class InterpreterTier { Local, Remote }

/**
 * Local rules first; the remote interpreter only when the local confidence is below
 * [Interpretation.ACCEPT_CONFIDENCE], a remote interpreter is configured (an API key exists) and the network is up,
 * at most one call per utterance. Any remote failure falls back to the local result, so the app works with no key.
 */
class TieredInterpreter(
    private val remote: LlmInterpreter?,
    private val online: () -> Boolean,
    private val budget: AiCallBudget? = null
) : IntentInterpreter {
    @Volatile
    var lastTier: InterpreterTier = InterpreterTier.Local
        private set

    /** True when the last utterance needed the remote tier and it failed (informational only). */
    @Volatile
    var lastRemoteFailed: Boolean = false
        private set

    override suspend fun interpret(transcript: String, language: Language): Interpretation {
        val local = LocalIntentParser.parse(transcript, language)
        lastTier = InterpreterTier.Local
        lastRemoteFailed = false
        if (local.confidence >= Interpretation.ACCEPT_CONFIDENCE || remote == null || !online()) {
            WgoMetrics.event("interpret tier=Local kind=${local.kind} conf=${local.confidence}")
            return local
        }
        if (budget != null && !budget.tryAcquire()) {
            lastRemoteFailed = true
            return local
        }
        return when (val r = remote.interpret(transcript, language)) {
            is LlmInterpreter.Result.Ok -> merge(local, r.value)
            LlmInterpreter.Result.RateLimited -> {
                budget?.onRateLimited()
                lastRemoteFailed = true
                WgoMetrics.event("interpret tier=Local reason=rate_limited")
                local
            }
            else -> {
                lastRemoteFailed = true
                WgoMetrics.event("interpret tier=Local reason=remote_failed")
                local
            }
        }
    }

    private fun merge(local: Interpretation, remote: Interpretation): Interpretation {
        if (remote.kind == IntentKind.Unknown && local.kind != IntentKind.Unknown) {
            WgoMetrics.event("interpret tier=Local reason=remote_unknown")
            return local
        }
        lastTier = InterpreterTier.Remote
        WgoMetrics.event("interpret tier=Remote kind=${remote.kind} conf=${remote.confidence}")
        return remote.copy(mode = remote.mode ?: local.mode)
    }

    companion object {
        /** The app's interpreter: the remote tier exists only when local.properties provided an API key. */
        fun create(context: Context, userAgent: String): TieredInterpreter {
            val app = context.applicationContext
            val key = BuildConfig.LLM_API_KEY
            val remote = if (key.isBlank()) null else LlmInterpreter(
                apiKey = key,
                baseUrl = BuildConfig.LLM_BASE_URL.ifBlank { LlmInterpreter.DEFAULT_BASE_URL },
                model = BuildConfig.LLM_MODEL.ifBlank { LlmInterpreter.DEFAULT_MODEL },
                http = HttpClient(userAgent)
            )
            WgoMetrics.event("interpreter remote=${remote != null}")
            return TieredInterpreter(remote, { isOnline(app) }, AiCallBudget(DataStoreAiCounterStore(app)))
        }
    }
}
