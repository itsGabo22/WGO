package com.wgo.navigator

import com.wgo.navigator.core.WgoFailure

/** The stage a Working surface shows while the voice-to-route pipeline runs. */
enum class WorkStage { Interpreting, Resolving, Routing }

/** Why the pipeline stopped; each maps to one message and one retry action. */
enum class PipelineError {
    MicPermission, LocationPermission, VoiceUnavailable, NotUnderstood, Offline, PlaceNotFound, RouteUnavailable,
    /** Informational: the remote interpreter failed AND the local rules found nothing. */
    AiUnavailable,
    /** Connected, but the server did not answer in time (read timeout). Retry repeats the failed step. */
    Timeout,
    /** The speech recognizer could not be started (busy or disconnected even after a silent retry). */
    MicStart;

    companion object {
        /**
         * A network failure of the geocoder or the router: a read timeout is [Timeout], no connection is [Offline],
         * anything else is [otherwise] (place not found / route unavailable).
         */
        fun forNetwork(failure: WgoFailure, otherwise: PipelineError): PipelineError = when (failure) {
            WgoFailure.Timeout -> Timeout
            WgoFailure.Offline -> Offline
            else -> otherwise
        }
    }
}
