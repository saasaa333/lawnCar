package com.saasaa.lawncar.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers icon-pack APKs installed on the device by querying the conventional set of
 * intent-filter actions that pack developers register with.
 */
public class IconPackManager {

    public static class PackInfo {
        public final String packageName;
        public final CharSequence label;

        public PackInfo(String packageName, CharSequence label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    /** Actions that icon packs declare in their manifest so launchers can find them. */
    private static final String[] PACK_ACTIONS = {
            "org.adw.launcher.THEMES",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME"
    };

    public static List<PackInfo> available(Context context) {
        PackageManager pm = context.getPackageManager();
        Set<String> seen = new HashSet<>();
        List<PackInfo> out = new ArrayList<>();

        for (String action : PACK_ACTIONS) {
            Intent i = new Intent(action);
            for (ResolveInfo ri : pm.queryIntentActivities(i, PackageManager.GET_META_DATA)) {
                String pkg = ri.activityInfo.packageName;
                if (seen.add(pkg)) {
                    out.add(new PackInfo(pkg, ri.loadLabel(pm)));
                }
            }
        }
        return out;
    }
}
