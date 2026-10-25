package com.reelshield.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * KontreDeviceAdminReceiver:
 * Implements Device Administrator receiver to provide high-level protection against
 * premature background task-killing and accidental service termination.
 *
 * Used as part of the redundant fallback defense layer alongside UsageStatsManager.
 */
class KontreDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "KontreDeviceAdmin"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, KontreDeviceAdminReceiver::class.java)
        }

        fun isDeviceAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                ?: return false
            return dpm.isAdminActive(getComponentName(context))
        }

        fun createAddDeviceAdminIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Enables Kontre Device Administrator protection to prevent OEM background killing " +
                            "and maintain continuous focus tracking."
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Administrator protection ENABLED.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "Device Administrator disable requested.")
        return "Disabling Device Administrator reduces focus protection and resilience against OEM background killers."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Administrator protection DISABLED.")
    }
}
