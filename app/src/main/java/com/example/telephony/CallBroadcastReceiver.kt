package com.example.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.example.AlamerApplication

/**
 * BroadcastReceiver for phone state changes.
 * Catches incoming calls on the Android device and invokes CallManager.
 */
class CallBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            if (stateStr == TelephonyManager.EXTRA_STATE_RINGING) {
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    ?: intent.getStringExtra("incoming_number")
                    ?: "غير متوفر"
                val app = context.applicationContext as? AlamerApplication
                app?.callManager?.onIncomingCallRinging(incomingNumber)
            }
        }
    }
}
