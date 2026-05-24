package com.saasaa.lawncar;

import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.ImageView;

import java.io.IOException;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.DynamicColors;
import com.saasaa.lawncar.adapters.AppAdapter;
import com.saasaa.lawncar.data.GroupRepository;
import com.saasaa.lawncar.data.IconPack;
import com.saasaa.lawncar.data.IconPackManager;
import com.saasaa.lawncar.model.AppGroup;
import com.saasaa.lawncar.model.AppInfo;
import com.saasaa.lawncar.ui.LauncherViewModel;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView appList;
    private LinearLayout groupsContainer;
    private LinearLayout bottomPanel;
    private LinearLayout alphaIndex;
    private LinearLayout favoritesDock;

    private AppAdapter adapter;
    private LauncherViewModel viewModel;

    private int rippleBackground;
    private boolean firstAppsLoad = true;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            viewModel.loadApps();
        }
    };

    // ─────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_main);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        viewModel = new ViewModelProvider(this).get(LauncherViewModel.class);

        appList = findViewById(R.id.appList);
        groupsContainer = findViewById(R.id.groupsContainer);
        bottomPanel = findViewById(R.id.bottomPanel);
        alphaIndex = findViewById(R.id.alphaIndex);
        favoritesDock = findViewById(R.id.favoritesDock);

        // Translucent panel — alpha on the background drawable only, so text/icons stay opaque
        if (bottomPanel.getBackground() != null) {
            bottomPanel.getBackground().mutate().setAlpha(205); // ~80%
        }

        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        rippleBackground = tv.resourceId;

        appList.setAlpha(0f);

        setupAlphaIndex();
        setupSystemBars();

        findViewById(R.id.btnAddGroup).setOnClickListener(v -> showCreateFolderDialog());
        findViewById(R.id.btnSearch).setOnClickListener(v -> showSearchDialog());
        findViewById(R.id.btnSettings).setOnClickListener(this::showSettingsMenu);

        adapter = new AppAdapter(viewModel.appsSnapshot(), new AppAdapter.OnAppActionListener() {
            @Override public void onAppClick(AppInfo app) { launchApp(app.packageName); }
            @Override public void onAppLongClick(AppInfo app, View v) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showOptionsPopup(app, v);
            }
        });

        appList.setLayoutManager(new LinearLayoutManager(this));
        appList.setAdapter(adapter);

        groupsContainer.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showCreateFolderDialog();
            return true;
        });

        viewModel.getApps().observe(this, list -> {
            adapter.notifyDataSetChanged();
            if (firstAppsLoad && !list.isEmpty()) {
                firstAppsLoad = false;
                appList.animate().alpha(1f).setDuration(420).start();
            }
        });
        viewModel.getGroups().observe(this, list -> renderGroups());
        viewModel.getDock().observe(this, list -> renderDock());
        viewModel.getIconPack().observe(this, pack -> renderDock());
    }

    private void setupSystemBars() {
        ViewCompat.setOnApplyWindowInsetsListener(bottomPanel, (v, insets) -> {
            Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            int extraPaddingPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    navBarInsets.bottom + extraPaddingPx);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(appList, (v, insets) -> {
            Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), statusBarInsets.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightNavigationBars(true);
    }

    // ─────────────────────────────────────────────
    // Alpha index
    // ─────────────────────────────────────────────

    private void setupAlphaIndex() {
        int textColor = com.google.android.material.color.MaterialColors.getColor(
                alphaIndex, com.google.android.material.R.attr.colorOnSurfaceVariant);

        for (char c = 'a'; c <= 'z'; c++) {
            TextView letter = new TextView(this);
            letter.setText(String.valueOf(c));
            letter.setTextSize(7);
            letter.setGravity(Gravity.CENTER);
            letter.setTextColor(textColor);
            letter.setAlpha(0.4f);
            letter.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            alphaIndex.addView(letter);
        }

        alphaIndex.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE) {
                int index = (int) (event.getY() / v.getHeight() * 26);
                index = Math.max(0, Math.min(25, index));
                scrollToLetter((char) ('A' + index));
            }
            return true;
        });
    }

    private void scrollToLetter(char letter) {
        LinearLayoutManager lm = (LinearLayoutManager) appList.getLayoutManager();
        if (lm == null) return;
        List<AppInfo> apps = viewModel.appsSnapshot();
        for (int i = 0; i < apps.size(); i++) {
            String label = apps.get(i).label;
            if (!label.isEmpty() && Character.toUpperCase(label.charAt(0)) >= letter) {
                lm.scrollToPositionWithOffset(i, 0);
                return;
            }
        }
    }

    // ─────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);

        viewModel.reloadGroupsFromDisk();
        viewModel.loadApps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(packageReceiver);
    }

    // ─────────────────────────────────────────────
    // Folder UI
    // ─────────────────────────────────────────────

    private void renderGroups() {
        groupsContainer.removeAllViews();
        // Index 0 is FAVORITES — it renders as the dock, not as a folder
        List<AppGroup> all = viewModel.groupsSnapshot();
        for (int i = 1; i < all.size(); i++) {
            AppGroup g = all.get(i);
            View content = createContent(g);
            groupsContainer.addView(createHeader(g, content));
            groupsContainer.addView(content);
        }
    }

    // ─────────────────────────────────────────────
    // Dock
    // ─────────────────────────────────────────────

    private void renderDock() {
        favoritesDock.removeAllViews();
        List<AppInfo> dock = viewModel.getDock().getValue();
        if (dock == null || dock.isEmpty()) return;

        IconPack pack = viewModel.getIconPack().getValue();
        int slot = dp(56);
        int pad  = dp(8);
        int gap  = dp(8);

        for (int i = 0; i < dock.size(); i++) {
            AppInfo app = dock.get(i);
            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(slot, slot);
            if (i > 0) lp.setMarginStart(gap);
            icon.setLayoutParams(lp);
            icon.setPadding(pad, pad, pad, pad);
            icon.setBackgroundResource(rippleBackground);
            icon.setContentDescription(app.label);
            icon.setImageDrawable(iconFor(app, pack));

            icon.setOnClickListener(v -> launchApp(app.packageName));
            icon.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showOptionsPopup(app, v);
                return true;
            });
            favoritesDock.addView(icon);
        }
    }

    private Drawable iconFor(AppInfo app, IconPack pack) {
        PackageManager pm = getPackageManager();
        Drawable fallback = null;
        try { fallback = pm.getApplicationIcon(app.packageName); }
        catch (PackageManager.NameNotFoundException ignored) {}

        if (pack == null) return fallback;
        Intent launch = pm.getLaunchIntentForPackage(app.packageName);
        if (launch == null || launch.getComponent() == null) return fallback;
        return pack.getIcon(launch.getComponent(), fallback);
    }

    private View createHeader(AppGroup group, View content) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(10), dp(6), dp(10), dp(6));

        int labelColor = com.google.android.material.color.MaterialColors.getColor(
                header, com.google.android.material.R.attr.colorOnSurfaceVariant);

        boolean empty = group.packages.isEmpty();

        TextView title = new TextView(this);
        title.setText(group.name);
        title.setTextColor(labelColor);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        if (empty) title.setAlpha(0.55f);

        TextView overflow = new TextView(this);
        overflow.setText("⋮");
        overflow.setTextSize(16);
        overflow.setPadding(dp(10), 0, 0, 0);
        overflow.setTextColor(labelColor);
        overflow.setAlpha(0.55f);
        overflow.setOnClickListener(v -> showFolderOptions(group));

        header.addView(title);

        final TextView arrow;
        if (!empty) {
            TextView count = new TextView(this);
            count.setText(String.valueOf(group.packages.size()));
            count.setTextSize(11);
            count.setTextColor(labelColor);
            count.setAlpha(0.55f);
            count.setPadding(dp(6), 0, dp(8), 0);
            count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

            arrow = new TextView(this);
            arrow.setText(group.expanded ? "▼" : "▶");
            arrow.setTextColor(labelColor);
            arrow.setAlpha(0.55f);

            header.addView(count);
            header.addView(arrow);
        } else {
            arrow = null;
        }

        header.addView(overflow);

        header.setOnClickListener(v -> {
            if (group.packages.isEmpty()) return; // nothing to expand
            viewModel.toggleExpanded(group);
            if (arrow != null) arrow.setText(group.expanded ? "▼" : "▶");
            animateExpand(content, group.expanded);
        });

        header.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (group.name.equals(GroupRepository.FAVORITES)) {
                showFolderOptions(group);
            } else {
                header.startDragAndDrop(null, new View.DragShadowBuilder(header), group, 0);
            }
            return true;
        });

        header.setOnDragListener((v, e) -> {
            switch (e.getAction()) {
                case DragEvent.ACTION_DROP:
                    Object state = e.getLocalState();
                    if (state instanceof AppGroup) {
                        viewModel.reorderFolders((AppGroup) state, group);
                    } else if (state instanceof AppInfo) {
                        viewModel.moveAppToGroup((AppInfo) state, group.name);
                    }
                    break;
                case DragEvent.ACTION_DRAG_ENTERED:
                    v.setAlpha(0.6f);
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setAlpha(1f);
                    break;
            }
            return true;
        });

        return header;
    }

    private View createContent(AppGroup group) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setVisibility(group.expanded ? View.VISIBLE : View.GONE);

        for (AppInfo app : viewModel.appsSnapshot()) {
            if (!group.packages.contains(app.packageName)) continue;

            TextView row = new TextView(this);
            row.setText(app.label);
            row.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                    row, com.google.android.material.R.attr.colorOnSurface));
            row.setTextSize(15);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setBackgroundResource(rippleBackground);
            row.setClickable(true);
            row.setFocusable(true);

            row.setOnClickListener(v -> launchApp(app.packageName));
            row.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showOptionsPopup(app, v);
                return true;
            });

            container.addView(row);
        }
        return container;
    }

    /**
     * Smoothly grows or shrinks the folder content view's height without touching anything
     * else in the layout. Cancels any in-flight animation on the same view so rapid taps
     * never fight each other.
     */
    private void animateExpand(View content, boolean expand) {
        Object existing = content.getTag();
        if (existing instanceof ValueAnimator) ((ValueAnimator) existing).cancel();

        ValueAnimator anim;
        if (expand) {
            int parentWidth = ((View) content.getParent()).getWidth();
            content.measure(
                    View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int target = content.getMeasuredHeight();

            content.getLayoutParams().height = 0;
            content.setVisibility(View.VISIBLE);
            content.requestLayout();

            anim = ValueAnimator.ofInt(0, target);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    content.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    content.requestLayout();
                    content.setTag(null);
                }
            });
        } else {
            int start = content.getHeight();
            anim = ValueAnimator.ofInt(start, 0);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    content.setVisibility(View.GONE);
                    content.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    content.setTag(null);
                }
            });
        }

        anim.addUpdateListener(va -> {
            content.getLayoutParams().height = (int) va.getAnimatedValue();
            content.requestLayout();
        });
        anim.setDuration(260);
        content.setTag(anim);
        anim.start();
    }

    // ─────────────────────────────────────────────
    // Menus / dialogs
    // ─────────────────────────────────────────────

    private void showOptionsPopup(AppInfo app, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        AppGroup current = viewModel.groupContaining(app);

        if (app.isFavorite) menu.getMenu().add("Remove from Dock");
        else                 menu.getMenu().add("Add to Dock");

        if (current != null && !current.name.equals(GroupRepository.FAVORITES)) {
            menu.getMenu().add("Remove from Folder");
        }

        menu.getMenu().add("App Shortcuts");
        menu.getMenu().add("Move to Folder");
        menu.getMenu().add("Uninstall");

        menu.setOnMenuItemClickListener(i -> {
            switch (i.getTitle().toString()) {
                case "Add to Dock":
                    viewModel.moveAppToGroup(app, GroupRepository.FAVORITES);
                    break;
                case "Remove from Dock":
                case "Remove from Folder":
                    viewModel.removeFromFolder(app);
                    break;
                case "App Shortcuts":
                    showAppShortcuts(app, anchor);
                    break;
                case "Move to Folder":
                    showMoveToFolderMenu(app, anchor);
                    break;
                case "Uninstall":
                    uninstallApp(app.packageName);
                    break;
            }
            return true;
        });
        menu.show();
    }

    private void showAppShortcuts(AppInfo app, View anchor) {
        LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery();
        q.setPackage(app.packageName);
        q.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);

        List<ShortcutInfo> shortcuts;
        try {
            shortcuts = launcherApps.getShortcuts(q, Process.myUserHandle());
        } catch (SecurityException e) {
            Toast.makeText(this, "Cannot read shortcuts", Toast.LENGTH_SHORT).show();
            return;
        }

        if (shortcuts == null || shortcuts.isEmpty()) {
            Toast.makeText(this, "No shortcuts available", Toast.LENGTH_SHORT).show();
            return;
        }

        PopupMenu menu = new PopupMenu(this, anchor);
        for (ShortcutInfo sc : shortcuts) menu.getMenu().add(sc.getShortLabel());

        menu.setOnMenuItemClickListener(item -> {
            for (ShortcutInfo sc : shortcuts) {
                if (item.getTitle().equals(sc.getShortLabel())) {
                    try { launcherApps.startShortcut(sc, null, null); }
                    catch (SecurityException ignored) {}
                    return true;
                }
            }
            return false;
        });
        menu.show();
    }

    private void showMoveToFolderMenu(AppInfo app, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        List<AppGroup> all = viewModel.groupsSnapshot();
        // Skip index 0 (FAVORITES / dock — apps reach it via "Add to Dock")
        if (all.size() <= 1) {
            Toast.makeText(this, "No folders yet", Toast.LENGTH_SHORT).show();
            return;
        }
        for (int i = 1; i < all.size(); i++) menu.getMenu().add(all.get(i).name);

        menu.setOnMenuItemClickListener(i -> {
            viewModel.moveAppToGroup(app, i.getTitle().toString());
            return true;
        });
        menu.show();
    }

    private void showFolderOptions(AppGroup group) {
        PopupMenu menu = new PopupMenu(this, groupsContainer);
        menu.getMenu().add("Rename");
        if (!group.name.equals(GroupRepository.FAVORITES)) menu.getMenu().add("Delete");

        menu.setOnMenuItemClickListener(i -> {
            if (i.getTitle().equals("Rename")) showRenameFolderDialog(group);
            if (i.getTitle().equals("Delete")) viewModel.deleteFolder(group);
            return true;
        });
        menu.show();
    }

    private void showCreateFolderDialog() {
        if (viewModel.isAtMaxFolders()) {
            Toast.makeText(this, "Max 5 folders allowed", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_add_folder, null);
        EditText input = view.findViewById(R.id.folderNameInput);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_LawnCar_Dialog)
                .setTitle("Create Folder")
                .setView(view)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError("Folder name required");
                return;
            }
            if (viewModel.addFolder(name)) dialog.dismiss();
        });

        input.requestFocus();
    }

    private void showRenameFolderDialog(AppGroup group) {
        EditText input = new EditText(this);
        input.setText(group.name);
        input.setSelectAllOnFocus(true);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_LawnCar_Dialog)
                .setTitle("Rename Folder")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) viewModel.renameFolder(group, name);
                })
                .setNegativeButton("Cancel", null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
    }

    private void showSearchDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_search, null);
        EditText input = view.findViewById(R.id.searchInput);
        RecyclerView results = view.findViewById(R.id.searchResults);

        results.setLayoutManager(new LinearLayoutManager(this));

        List<AppInfo> filtered = new ArrayList<>(viewModel.appsSnapshot());

        AppAdapter searchAdapter = new AppAdapter(filtered, new AppAdapter.OnAppActionListener() {
            @Override public void onAppClick(AppInfo app)             { launchApp(app.packageName); }
            @Override public void onAppLongClick(AppInfo app, View v) { showOptionsPopup(app, v); }
        });
        results.setAdapter(searchAdapter);

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().toLowerCase().trim();
                filtered.clear();
                for (AppInfo app : viewModel.appsSnapshot()) {
                    if (app.label.toLowerCase().contains(q)) filtered.add(app);
                }
                searchAdapter.notifyDataSetChanged();
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_LawnCar_Dialog)
                .setView(view)
                .setNegativeButton("Close", null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
        input.requestFocus();
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private void launchApp(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) startActivity(i);
    }

    private void uninstallApp(String pkg) {
        startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
    }

    // ─────────────────────────────────────────────
    // Settings menu
    // ─────────────────────────────────────────────

    private void showSettingsMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Change wallpaper");
        menu.getMenu().add("Wallpaper & styles");
        menu.getMenu().add("All-black wallpaper");
        menu.getMenu().add("Icon pack");

        menu.setOnMenuItemClickListener(item -> {
            switch (item.getTitle().toString()) {
                case "Change wallpaper":      openWallpaperPicker(); break;
                case "Wallpaper & styles":    openWallpaperSettings(); break;
                case "All-black wallpaper":   setBlackWallpaper(); break;
                case "Icon pack":             showIconPackPicker(anchor); break;
            }
            return true;
        });
        menu.show();
    }

    private void showIconPackPicker(View anchor) {
        List<IconPackManager.PackInfo> packs = IconPackManager.available(this);
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 0, 0, "System default");
        for (int i = 0; i < packs.size(); i++) {
            menu.getMenu().add(0, i + 1, 0, packs.get(i).label);
        }

        if (packs.isEmpty()) {
            Toast.makeText(this, "No icon packs installed", Toast.LENGTH_SHORT).show();
        }

        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 0) viewModel.setIconPack(null);
            else         viewModel.setIconPack(packs.get(id - 1).packageName);
            return true;
        });
        menu.show();
    }

    private void openWallpaperPicker() {
        try {
            startActivity(Intent.createChooser(
                    new Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No wallpaper picker found", Toast.LENGTH_SHORT).show();
        }
    }

    private void openWallpaperSettings() {
        // Use ACTION_SET_WALLPAPER (which wallpaper apps export via intent-filter) combined
        // with setPackage() so it goes straight to the target app without a chooser.
        // This avoids SecurityException (component-targeting non-exported activities) and
        // avoids the chooser (createChooser).
        //
        // Priority on Android 12+: Google's Wallpaper & style app
        // Fallback: AOSP com.android.wallpaper, then generic chooser.
        List<String> candidates = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            candidates.add("com.google.android.apps.wallpaper");
        }
        candidates.add("com.android.wallpaper");

        for (String pkg : candidates) {
            Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
            intent.setPackage(pkg);
            if (getPackageManager().resolveActivity(intent, 0) != null) {
                startActivity(intent);
                return;
            }
        }
        openWallpaperPicker();
    }

    private void setBlackWallpaper() {
        Bitmap black = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        black.eraseColor(Color.BLACK);
        try {
            WallpaperManager wm = WallpaperManager.getInstance(this);
            wm.setBitmap(black, null, true, WallpaperManager.FLAG_SYSTEM);
            Toast.makeText(this, "Wallpaper set to black", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to set wallpaper", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
