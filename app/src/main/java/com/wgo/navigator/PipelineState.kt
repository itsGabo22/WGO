package com.wgo.navigator

/** The stage a Working surface shows while the voice-to-route pipeline runs. */
enum class WorkStage { Interpreting, Resolving, Routing }

/** Why the pipeline stopped; each maps to one message and one retry action. */
enum class PipelineError {
    MicPermission, LocationPermission, VoiceUnavailable, NotUnderstood, Offline, PlaceNotFound, RouteUnavailable,
    /** Informational: the remote interpreter failed AND the local rules found nothing. */
    AiUnavailable
}
