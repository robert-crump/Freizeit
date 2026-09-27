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
