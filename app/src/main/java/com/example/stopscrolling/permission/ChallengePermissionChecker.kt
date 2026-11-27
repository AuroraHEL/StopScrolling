package com.example.stopscrolling.permission

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.stopscrolling.challenge.ChallengeType

/** Reads the permission state of the challenges (US-04). */
interface ChallengePermissionChecker {

    /** The permission to ask for [type] on this phone, or null if none is needed. */
    fun permissionFor(type: ChallengeType): String?

    /** True if [type] can be used: no permission is needed, or it is granted. */
    fun isGranted(type: ChallengeType): Boolean
}

/** Real implementation, based on the Android permission system. */
class AndroidChallengePermissionChecker(
    context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT
) : ChallengePermissionChecker {

    private val appContext = context.applicationContext

    override fun permissionFor(type: ChallengeType): String? =
        ChallengePermissions.permissionToRequest(type, sdkInt)

    override fun isGranted(type: ChallengeType): Boolean {
        val permission = permissionFor(type) ?: return true

        return ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
