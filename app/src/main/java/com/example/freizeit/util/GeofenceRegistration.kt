package com.example.freizeit.util

/**
 * Which geofences can still be trusted to be live in Play Services, given the id set persisted at
 * the last registration and the boot/install epoch it was persisted under (issue #58).
 *
 * Play Services drops every geofence on reboot (and on reinstall), but the persisted id set
 * survives both — so without this check, `GeofenceSyncManager.register` would keep diffing against
 * ids that no longer exist, conclude "nothing to add", and leave auto check-in silently dead until
 * the favorites happened to change. A mismatched or missing epoch (the latter also covering
 * installs from before the epoch existed) therefore means "assume nothing is registered".
 */
fun effectiveRegisteredIds(
    persistedIds: Set<String>,
    persistedEpoch: String?,
    currentEpoch: String
): Set<String> = if (persistedEpoch == currentEpoch) persistedIds else emptySet()

/** Epoch key combining the device's boot count and the app's last install/update time. */
fun registrationEpoch(bootCount: Int, lastUpdateTimeMillis: Long): String = "$bootCount:$lastUpdateTimeMillis"

/**
 * Whether a registration under [currentEpoch] is re-adding geofences after a reboot or
 * install/update, i.e. whether a delayed forced re-register should follow it (issue #61).
 *
 * After an install/update Play Services wipes the app's geofences asynchronously, possibly
 * *after* our own re-registration has already landed — the persisted set then claims they're live
 * and every later sync diffs to "nothing to do". A second, forced pass a couple of minutes later
 * beats that race whichever side wins it.
 *
 * Only a persisted registration from a *different* epoch counts. A missing epoch with nothing
 * persisted means nothing was registered to begin with (first enable, or right after toggling
 * auto check-in off/on or location going unavailable) — no race to beat there. A missing epoch
 * with ids persisted is a pre-#58 install, which is effectively an update.
 */
fun isEpochReset(
    persistedIds: Set<String>,
    persistedEpoch: String?,
    currentEpoch: String
): Boolean = if (persistedEpoch == null) persistedIds.isNotEmpty() else persistedEpoch != currentEpoch
