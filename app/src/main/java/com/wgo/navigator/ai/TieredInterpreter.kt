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

    /** Remote calls the last utterance made (0 or 1): the place search keeps the total per utterance <= 2 (P8). */
    @Volatile
    var lastRemoteCalls: Int = 0
        private set

    override suspend fun interpret(transcript: String, language: Language): Interpretation {
        val local = LocalIntentParser.parse(transcript, language)
        lastTier = InterpreterTier.Local
        lastRemoteFailed = false
        lastRemoteCalls = 0
        if (local.confidence >= Interpretation.ACCEPT_CONFIDENCE || remote == null || !online()) {
            WgoMetrics.event("interpret tier=Local kind=${local.kind} conf=${local.confidence}")
            return local
        }
        if (budget != null && !budget.tryAcquire()) {
            lastRemoteFailed = true
            return local
        }
        lastRemoteCalls = 1
        return when (val r = remote.interpret(transcript, language)) {
            is LlmInterpreter.Result.Ok -> merge(local, grounded(r.value, transcript))
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

    /**
     * P10: the model's destination must be the user's own words; an invented or completed name is dropped (and an
     * ungrounded hint with it), so the local result stands.
     */
    private fun grounded(remote: Interpretation, transcript: String): Interpretation {
        val name = remote.placeQuery ?: return remote
        if (!LocalIntentParser.groundedIn(name, transcript)) {
            WgoMetrics.event("interpret remote_name_ignored=ungrounded")
            return remote.copy(kind = IntentKind.Unknown, placeQuery = null, placeHint = null)
        }
        val hint = remote.placeHint?.takeIf { LocalIntentParser.groundedIn(it, transcript) }
        return remote.copy(placeHint = hint)
    }

    private fun merge(local: Interpretation, remote: Interpretation): Interpretation {
        if (remote.kind == IntentKind.Unknown && local.kind != IntentKind.Unknown) {
            WgoMetrics.event("interpret tier=Local reason=remote_unknown")
            return local
        }
        lastTier = InterpreterTier.Remote
        WgoMetrics.event("interpret tier=Remote kind=${remote.kind} conf=${remote.confidence}")
        // The travel mode only comes from words the user actually said (the local rules catch every mode phrase);
        // live testing showed the remote model inventing one.
        return remote.copy(
            mode = local.mode, placeVague = local.placeVague, placeHeard = remote.placeQuery ?: local.placeHeard,
            placeNearest = local.placeNearest
        )
    }

    companion object {
        /**
         * The app's interpreter: the remote tier exists only when local.properties provided an API key. [budget] is
         * shared with the place chooser (one daily cap and one cool-down for every model call).
         */
        fun create(context: Context, userAgent: String, budget: AiCallBudget): TieredInterpreter {
            val app = context.applicationContext
            val key = BuildConfig.LLM_API_KEY
            val remote = if (key.isBlank()) null else LlmInterpreter(
                apiKey = key,
                baseUrl = BuildConfig.LLM_BASE_URL.ifBlank { LlmInterpreter.DEFAULT_BASE_URL },
                model = BuildConfig.LLM_MODEL.ifBlank { LlmInterpreter.DEFAULT_MODEL },
                http = HttpClient(userAgent)
            )
            WgoMetrics.event("interpreter remote=${remote != null}")
            return TieredInterpreter(remote, { isOnline(app) }, budget)
        }
    }
}
