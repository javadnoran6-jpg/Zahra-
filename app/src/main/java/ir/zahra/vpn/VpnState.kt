package ir.zahra.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

enum class Status { IDLE, CONNECTING, CONNECTED, ERROR }

data class UiState(
    val status: Status = Status.IDLE,
    val message: String = "",
    val profileName: String = "",
    val connectedAt: Long = 0L,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val rxSpeed: Long = 0L,
    val txSpeed: Long = 0L,
)

/** Process-wide state shared between the VpnService and the UI. */
object VpnState {
    val state = MutableStateFlow(UiState())
    val logs = MutableStateFlow<List<String>>(emptyList())

    fun log(line: String) {
        if (line.isBlank()) return
        logs.update { (it + line).takeLast(400) }
    }
}
