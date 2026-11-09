package com.wgo.navigator.net

import com.wgo.navigator.BuildConfig

/** User-Agent strings. The contact comes from the git-ignored local.properties and is never logged. */
object WgoUserAgent {
    /** Placeholder when no contact is configured; a real contact is required before any public use of OSM services. */
    const val DEV = "WGO-dev/0.1"

    /** For OpenStreetMap services (Nominatim, OSM-DE routing): identifies the app and a contact, as their policies ask. */
    val identifying: String
        get() = BuildConfig.WGO_CONTACT.trim().let { contact ->
            if (contact.isEmpty()) DEV else "WGO/${BuildConfig.VERSION_NAME} ($contact)"
        }

    /** For other services (the remote interpreter): the app name only, no contact. */
    val plain: String get() = "WGO/${BuildConfig.VERSION_NAME}"
}
