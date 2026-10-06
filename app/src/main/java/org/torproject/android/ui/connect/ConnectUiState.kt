package org.torproject.android.ui.connect

sealed class ConnectUiState {
    /**
     * NoInternet can mean two things, the device doesn't have *any* reliable WiFi/Cellular/USB signal
     * TODO If the device has a VALID WIFI CONNECTION, but the device is using another VPN that is
     * blocking Orbot from connecting to the web, NoInternet DOES NOT REGISTER, even though Orbot
     * is effectively offline...
     */
    object NoInternet : ConnectUiState()
    object Off : ConnectUiState()
    data class Starting(val bootstrapPercent: Int?) : ConnectUiState()
    object On : ConnectUiState()
    object Stopping : ConnectUiState()
}
