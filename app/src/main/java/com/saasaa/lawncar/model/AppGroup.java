package com.saasaa.lawncar.model;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

public class AppGroup {
    public String name;
    public final List<String> packages = new ArrayList<>();
    public boolean expanded = true;

    public AppGroup(String name) {
        this.name = name;
    }

    /**
     * Stable, name-derived dot color for this folder.
     * Same name → same hue across launches. Saturation/value kept gentle so the dot
     * reads as a tint rather than a sticker.
     */
    public static int colorFor(String name) {
        if (name == null || name.isEmpty()) return 0;
        int hash = Math.abs(name.hashCode());
        float hue = hash % 360;
        return Color.HSVToColor(new float[]{hue, 0.45f, 0.85f});
    }
}
