package com.wgo.navigator.core.permissions

import com.wgo.navigator.core.permissions.PermissionStatus.Denied
import com.wgo.navigator.core.permissions.PermissionStatus.Granted
import com.wgo.navigator.core.permissions.PermissionStatus.NotAsked
import com.wgo.navigator.core.permissions.PermissionStatus.PermanentlyDenied
import org.junit.Assert.assertEquals
import org.junit.Test

/** The API 30+ sequence: first ask -> denied (rationale on) -> asked again -> denied twice = don't ask again. */
class PermissionStatusTest {
    private fun s(granted: Boolean = false, asked: Boolean = true, rationale: Boolean = false, saw: Boolean = false) =
        permissionStatus(granted, asked, rationale, saw)

    @Test fun grantedWinsOverEverything() = assertEquals(Granted, s(granted = true, rationale = true, saw = true))

    @Test fun neverAskedShowsTheDialog() = assertEquals(NotAsked, s(asked = false))

    @Test fun firstDenialCanRetry() = assertEquals(Denied, s(rationale = true, saw = true))

    @Test fun deniedTwiceIsPermanent() = assertEquals(PermanentlyDenied, s(rationale = false, saw = true))

    /** A dialog dismissed with Back before any rationale was ever requested is still retryable. */
    @Test fun dismissedFirstDialogIsNotPermanent() = assertEquals(Denied, s(rationale = false, saw = false))
}
