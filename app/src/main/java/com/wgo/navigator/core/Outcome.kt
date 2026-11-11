package com.wgo.navigator.core

/**
 * Why a service call did not produce a value. Services never throw across layers: every failure is one of these,
 * returned inside [Outcome.Err] (or emitted as an event), and the UI maps each to a clear state.
 */
sealed interface WgoFailure {
    /** The runtime permission the service needs is not granted. */
    data object NoPermission : WgoFailure

    /** The device lacks the hardware/service, it is switched off, or the feature is not configured (no key). */
    data object Unavailable : WgoFailure

    data object Timeout : WgoFailure

    /** No network connection. */
    data object Offline : WgoFailure

    /** The request worked but found nothing (no place, no route, nothing understood). */
    data object NotFound : WgoFailure

    /** The remote service answered with an error; [code] is the HTTP status when there is one, else 0. */
    data class Server(val code: Int) : WgoFailure

    /** A local engine (the speech recognizer) could not be started, even after a silent retry. */
    data object NotStarted : WgoFailure

    /** The caller (or the user) cancelled. */
    data object Cancelled : WgoFailure
}

/** Result of a one-shot service call. */
sealed interface Outcome<out T> {
    data class Ok<out T>(val value: T) : Outcome<T>
    data class Err(val failure: WgoFailure) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Ok)?.value
