package com.saasaa.lawncar.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.saasaa.lawncar.model.AppGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class GroupRepository {

    public static final String FAVORITES = "FAVORITES";

    private static final String PREFS = "LawncarPrefs";
    private static final String KEY_GROUPS = "groups";
    private static final String KEY_ICON_PACK = "iconPack";

    private final SharedPreferences prefs;

    public GroupRepository(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<AppGroup> load() {
        List<AppGroup> groups = new ArrayList<>();
        String json = prefs.getString(KEY_GROUPS, null);

        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    AppGroup g = new AppGroup(o.getString("name"));
                    JSONArray pkgs = o.getJSONArray("packages");
                    for (int j = 0; j < pkgs.length(); j++) {
                        g.packages.add(pkgs.getString(j));
                    }
                    groups.add(g);
                }
            } catch (JSONException ignored) {}
        }

        // Favorites must always exist at index 0
        if (groups.isEmpty() || !groups.get(0).name.equals(FAVORITES)) {
            groups.add(0, new AppGroup(FAVORITES));
            save(groups);
        }
        return groups;
    }

    public String getIconPack() {
        return prefs.getString(KEY_ICON_PACK, null);
    }

    public void setIconPack(String packageName) {
        if (packageName == null) prefs.edit().remove(KEY_ICON_PACK).apply();
        else                     prefs.edit().putString(KEY_ICON_PACK, packageName).apply();
    }

    public void save(List<AppGroup> groups) {
        JSONArray arr = new JSONArray();
        try {
            for (AppGroup g : groups) {
                JSONObject o = new JSONObject();
                o.put("name", g.name);
                o.put("packages", new JSONArray(g.packages));
                arr.put(o);
            }
        } catch (JSONException ignored) {}
        prefs.edit().putString(KEY_GROUPS, arr.toString()).apply();
    }
}
