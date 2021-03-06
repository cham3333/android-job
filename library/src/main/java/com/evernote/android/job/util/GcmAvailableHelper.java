package com.evernote.android.job.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.evernote.android.job.gcm.JobProxyGcm;
import com.evernote.android.job.gcm.PlatformGcmService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import net.vrallev.android.cat.CatLog;

import java.util.List;

/**
 * @author rwondratschek
 */
/*package*/ final class GcmAvailableHelper {

    private static final CatLog CAT = new JobCat("GcmAvailableHelper");

    private static final String ACTION_TASK_READY = "com.google.android.gms.gcm.ACTION_TASK_READY";
    private static final String GCM_PERMISSION = "com.google.android.gms.permission.BIND_NETWORK_TASK_SERVICE";

    private static final boolean GCM_IN_CLASSPATH;

    private static int gcmServiceAvailable = -1;
    private static boolean checkedServiceEnabled;

    static {
        boolean gcmInClasspath;
        try {
            Class.forName("com.google.android.gms.gcm.GcmNetworkManager");
            gcmInClasspath = true;
        } catch (Throwable t) {
            gcmInClasspath = false;
        }
        GCM_IN_CLASSPATH = gcmInClasspath;
    }

    public static boolean isGcmApiSupported(Context context) {
        try {
            if (!checkedServiceEnabled) {
                checkedServiceEnabled = true;
                setServiceEnabled(context, GCM_IN_CLASSPATH);
            }

            return GCM_IN_CLASSPATH
                    && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
                    && isGcmServiceRegistered(context) == ConnectionResult.SUCCESS;
        } catch (Throwable t) {
            // seeing sometimes a DeadObjectException, return false, we can't do anything in this case
            // still sometimes seeing a NoClassDefFoundError here
            CAT.w(t);
            return false;
        }
    }

    private static int isGcmServiceRegistered(Context context) {
        if (gcmServiceAvailable < 0) {
            synchronized (JobApi.class) {
                if (gcmServiceAvailable < 0) {
                    Intent intent = new Intent(context, PlatformGcmService.class);
                    List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentServices(intent, 0);
                    if (!hasPermission(resolveInfos)) {
                        gcmServiceAvailable = ConnectionResult.SERVICE_MISSING;
                        return gcmServiceAvailable;
                    }

                    intent = new Intent(ACTION_TASK_READY);
                    intent.setPackage(context.getPackageName());
                    resolveInfos = context.getPackageManager().queryIntentServices(intent, 0);
                    if (!hasPermission(resolveInfos)) {
                        gcmServiceAvailable = ConnectionResult.SERVICE_MISSING;
                        return gcmServiceAvailable;
                    }

                    gcmServiceAvailable = ConnectionResult.SUCCESS;
                }
            }
        }

        return gcmServiceAvailable;
    }

    private static boolean hasPermission(List<ResolveInfo> resolveInfos) {
        if (resolveInfos == null || resolveInfos.isEmpty()) {
            return false;
        }
        for (ResolveInfo info : resolveInfos) {
            if (info.serviceInfo != null && GCM_PERMISSION.equals(info.serviceInfo.permission) && info.serviceInfo.exported) {
                return true;
            }
        }
        return false;
    }

    private static void setServiceEnabled(Context context, boolean enabled) {
        try {
            PackageManager packageManager = context.getPackageManager();

            // use a string, the class object probably cannot be instantiated
            String className = JobProxyGcm.class.getPackage().getName() + ".PlatformGcmService";
            ComponentName component = new ComponentName(context, className);

            int componentEnabled = packageManager.getComponentEnabledSetting(component);
            switch (componentEnabled) {
                case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                    if (!enabled) {
                        packageManager.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                        CAT.i("GCM service disabled");
                    }
                    break;

                case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT: // default is disable
                case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                    if (enabled) {
                        packageManager.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                        CAT.i("GCM service enabled");
                    }
                    break;
            }

        } catch (Throwable t) {
            // just in case, don't let the app crash with each restart
            CAT.e(t);
        }
    }

    private GcmAvailableHelper() {
        // no op
    }
}
