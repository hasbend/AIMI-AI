package info.nightscout.interfaces.aps

import info.nightscout.database.entities.OfflineEvent
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.PumpEnactResult

interface Loop {

    fun isEnabled(): Boolean
    class LastRun {

        var request: APSResult? = null
        var constraintsProcessed: APSResult? = null
        var tbrSetByPump: PumpEnactResult? = null
        var smbSetByPump: PumpEnactResult? = null
        var source: String? = null
        var lastAPSRun = System.currentTimeMillis()
        var lastTBREnact: Long = 0
        var lastSMBEnact: Long = 0
        var lastTBRRequest: Long = 0
        var lastSMBRequest: Long = 0
        var lastOpenModeAccept: Long = 0
    }

    var lastRun: LastRun?
    var closedLoopEnabled: Constraint<Boolean>?
    val isSuspended: Boolean
    val isLGS: Boolean
    val isSuperBolus: Boolean
    val isDisconnected: Boolean
    var enabled: Boolean

    var lastBgTriggeredRun: Long

    fun invoke(initiator: String, allowNotification: Boolean, tempBasalFallback: Boolean = false)

    fun acceptChangeRequest()
    fun minutesToEndOfSuspend(): Int
    fun goToZeroTemp(durationInMinutes: Int, profile: Profile, reason: OfflineEvent.Reason)
    fun suspendLoop(durationInMinutes: Int)
    fun disableCarbSuggestions(durationMinutes: Int)
    fun buildAndStoreDeviceStatus()
}