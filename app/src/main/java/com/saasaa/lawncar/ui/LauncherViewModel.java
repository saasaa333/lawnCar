package com.saasaa.lawncar.ui;

import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.saasaa.lawncar.data.GroupRepository;
import com.saasaa.lawncar.data.IconPack;
import com.saasaa.lawncar.model.AppGroup;
import com.saasaa.lawncar.model.AppInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LauncherViewModel extends AndroidViewModel {

    public static final int MAX_GROUPS = 5;
    public static final int DOCK_SIZE  = 5;

    private final GroupRepository repo;

    private final List<AppInfo> apps = new ArrayList<>();
    private final List<AppGroup> groups = new ArrayList<>();

    private final MutableLiveData<List<AppInfo>> appsLive = new MutableLiveData<>(apps);
    private final MutableLiveData<List<AppGroup>> groupsLive = new MutableLiveData<>(groups);
    private final MutableLiveData<List<AppInfo>> dockLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<IconPack> iconPackLive = new MutableLiveData<>(null);

    public LauncherViewModel(@NonNull Application app) {
        super(app);
        repo = new GroupRepository(app);
        groups.addAll(repo.load());
        loadApps();
        loadIconPackFromPrefs();
    }

    // ─────────────────────────────────────────────
    // Observables
    // ─────────────────────────────────────────────

    public LiveData<List<AppInfo>> getApps()    { return appsLive; }
    public LiveData<List<AppGroup>> getGroups() { return groupsLive; }
    public LiveData<List<AppInfo>> getDock()    { return dockLive; }
    public LiveData<IconPack> getIconPack()     { return iconPackLive; }

    public List<AppInfo> appsSnapshot()    { return apps; }
    public List<AppGroup> groupsSnapshot() { return groups; }

    // ─────────────────────────────────────────────
    // Loading
    // ─────────────────────────────────────────────

    public void loadApps() {
        apps.clear();
        PackageManager pm = getApplication().getPackageManager();

        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);

        AppGroup favorites = favorites();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getApplication().getPackageName())) continue;

            AppInfo app = new AppInfo(ri.loadLabel(pm).toString(), pkg);
            app.isFavorite = favorites.packages.contains(pkg);
            apps.add(app);
        }

        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        applyFolderColors();
        appsLive.setValue(apps);
        recomputeDock();
    }

    private void applyFolderColors() {
        for (AppInfo app : apps) {
            AppGroup g = groupContaining(app);
            app.folderColor = (g != null) ? AppGroup.colorFor(g.name) : 0;
        }
    }

    public void reloadGroupsFromDisk() {
        groups.clear();
        groups.addAll(repo.load());
        AppGroup favorites = favorites();
        for (AppInfo app : apps) app.isFavorite = favorites.packages.contains(app.packageName);
        applyFolderColors();
        groupsLive.setValue(groups);
        appsLive.setValue(apps);
        recomputeDock();
    }

    // ─────────────────────────────────────────────
    // Icon pack
    // ─────────────────────────────────────────────

    public String getIconPackPackage() {
        return repo.getIconPack();
    }

    /** Set or clear the active icon pack. Loading the pack happens on a background thread. */
    public void setIconPack(String packageName) {
        repo.setIconPack(packageName);
        if (packageName == null) {
            iconPackLive.setValue(null);
            return;
        }
        new Thread(() -> {
            try {
                IconPack pack = new IconPack(getApplication(), packageName);
                iconPackLive.postValue(pack);
            } catch (Exception e) {
                iconPackLive.postValue(null);
            }
        }, "icon-pack-load").start();
    }

    private void loadIconPackFromPrefs() {
        String pkg = repo.getIconPack();
        if (pkg != null) setIconPack(pkg);
    }

    // ─────────────────────────────────────────────
    // Dock
    // ─────────────────────────────────────────────

    private void recomputeDock() {
        AppGroup fav = favorites();
        List<AppInfo> dock = new ArrayList<>();

        if (!fav.packages.isEmpty()) {
            // Use favorites in the order the user added them
            for (String pkg : fav.packages) {
                AppInfo app = findApp(pkg);
                if (app != null) dock.add(app);
            }
        } else {
            // Resolve 5 common system defaults: Phone, Messages, Camera, Browser, Gallery
            PackageManager pm = getApplication().getPackageManager();
            Intent[] defaults = {
                    new Intent(Intent.ACTION_DIAL),
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
                    new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
            };
            Set<String> seen = new HashSet<>();
            for (Intent intent : defaults) {
                ResolveInfo ri = pm.resolveActivity(intent, 0);
                if (ri == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (!seen.add(pkg)) continue;
                AppInfo app = findApp(pkg);
                if (app != null) dock.add(app);
                if (dock.size() >= DOCK_SIZE) break;
            }
        }
        dockLive.setValue(dock);
    }

    private AppInfo findApp(String pkg) {
        for (AppInfo a : apps) if (a.packageName.equals(pkg)) return a;
        return null;
    }

    // ─────────────────────────────────────────────
    // Mutations
    // ─────────────────────────────────────────────

    public void moveAppToGroup(AppInfo app, String groupName) {
        for (AppGroup g : groups) g.packages.remove(app.packageName);
        AppGroup target = byName(groupName);
        target.packages.add(app.packageName);
        app.isFavorite = groupName.equals(GroupRepository.FAVORITES);
        persist();
    }

    public void removeFromFolder(AppInfo app) {
        for (AppGroup g : groups) g.packages.remove(app.packageName);
        app.isFavorite = false;
        persist();
    }

    public boolean addFolder(String name) {
        if (groups.size() >= MAX_GROUPS) return false;
        groups.add(new AppGroup(name));
        persist();
        return true;
    }

    public void renameFolder(AppGroup group, String newName) {
        group.name = newName;
        persist();
    }

    public void deleteFolder(AppGroup group) {
        groups.remove(group);
        persist();
    }

    public boolean reorderFolders(AppGroup dragged, AppGroup target) {
        int from = groups.indexOf(dragged);
        int to   = groups.indexOf(target);
        if (from == -1 || to == -1 || from == to) return false;
        if (dragged.name.equals(GroupRepository.FAVORITES)) return false;
        if (to == 0) return false;

        groups.remove(from);
        groups.add(to, dragged);
        persist();
        return true;
    }

    public void toggleExpanded(AppGroup group) {
        // Flip the flag only — the Activity animates the affected content view's height
        // directly. Firing groupsLive here would force a full rebuild and ruin the animation.
        group.expanded = !group.expanded;
    }

    // ─────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────

    public AppGroup favorites() {
        return groups.get(0);
    }

    public AppGroup groupContaining(AppInfo app) {
        for (AppGroup g : groups)
            if (g.packages.contains(app.packageName)) return g;
        return null;
    }

    public boolean isAtMaxFolders() {
        return groups.size() >= MAX_GROUPS;
    }

    // ─────────────────────────────────────────────

    private AppGroup byName(String name) {
        for (AppGroup g : groups)
            if (g.name.equals(name)) return g;
        return favorites();
    }

    private void persist() {
        repo.save(groups);
        applyFolderColors();
        groupsLive.setValue(groups);
        appsLive.setValue(apps);
        recomputeDock();
    }
}
