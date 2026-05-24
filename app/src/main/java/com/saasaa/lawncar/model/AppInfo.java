package com.saasaa.lawncar.model;

public class AppInfo {
    public String label;
    public String packageName;
    public boolean isFavorite;
    /** ARGB color of the folder this app is in, or 0 if not in any folder. */
    public int folderColor;

    public AppInfo(String label, String packageName) {
        this.label = label;
        this.packageName = packageName;
        this.isFavorite = false;
        this.folderColor = 0;
    }
}
