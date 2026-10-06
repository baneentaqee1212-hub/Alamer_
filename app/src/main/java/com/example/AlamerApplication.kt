package com.example

import android.app.Application
import com.example.data.AlamerRepository
import com.example.network.SignalRClient
import com.example.network.TaloolaHttpClient
import com.example.network.UdpDiscoveryClient
import com.example.security.SecureStorageManager
import com.example.telephony.AlamerCallAssistantService
import com.example.telephony.CallManager

class AlamerApplication : Application() {
    lateinit var secureStorage: SecureStorageManager
        private set
    lateinit var httpClient: TaloolaHttpClient
        private set
    lateinit var signalRClient: SignalRClient
        private set
    lateinit var udpDiscoveryClient: UdpDiscoveryClient
        private set
    lateinit var callManager: CallManager
        private set
    lateinit var repository: AlamerRepository
        private set

    override fun onCreate() {
        super.onCreate()

        secureStorage = SecureStorageManager(this)
        httpClient = TaloolaHttpClient()
        signalRClient = SignalRClient()
        udpDiscoveryClient = UdpDiscoveryClient(httpClient)
        callManager = CallManager(signalRClient)

        repository = AlamerRepository(
            context = this,
            secureStorage = secureStorage,
            httpClient = httpClient,
            signalRClient = signalRClient,
            udpDiscoveryClient = udpDiscoveryClient,
            callManager = callManager
        )

        // Initialize repository and auto-reconnect if trust exists
        repository.initialize()

        // Start background call assistant foreground service
        try {
            AlamerCallAssistantService.startService(this)
        } catch (e: Exception) {
            // Handled gracefully on Android 14+ background startup restrictions
        }
    }
}
