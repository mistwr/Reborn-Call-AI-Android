package pt.reborn.callai.dialer

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

object DialerRoleHelper {
    const val REQUEST_ROLE_DIALER = 9101

    fun isDefaultDialer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java)
        return roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
            roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    fun requestDefaultDialer(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val roleManager = activity.getSystemService(RoleManager::class.java)
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) return
        if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) return
        val intent: Intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        activity.startActivityForResult(intent, REQUEST_ROLE_DIALER)
    }
}
