package com.saasaa.lawncar.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;

import androidx.core.content.res.ResourcesCompat;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads an installed icon pack and resolves per-component icons against its appfilter map.
 *
 * Icon packs follow the long-standing Apex/Nova/ADW convention:
 *   - Pack APK ships an `appfilter.xml` (in res/xml or assets) with <item component="..."
 *     drawable="..."/> entries
 *   - The `component` attribute uses the ComponentInfo{pkg/activity} textual format
 *   - The `drawable` attribute is a drawable resource name inside the pack's own resources
 */
public class IconPack {

    public final String packagePkg;
    private final Resources packRes;
    private final Map<String, String> componentToDrawable = new HashMap<>();

    public IconPack(Context context, String packagePkg) throws PackageManager.NameNotFoundException {
        this.packagePkg = packagePkg;
        this.packRes = context.getPackageManager().getResourcesForApplication(packagePkg);
        parseAppFilter(context);
    }

    /**
     * Returns the pack's icon for the given launcher component, or {@code fallback} if the pack
     * has no mapping for that component or fails to load its drawable.
     */
    public Drawable getIcon(ComponentName component, Drawable fallback) {
        if (component == null) return fallback;
        String key = "ComponentInfo{" + component.flattenToString() + "}";
        String drawableName = componentToDrawable.get(key);
        if (drawableName == null) return fallback;
        int id = packRes.getIdentifier(drawableName, "drawable", packagePkg);
        if (id == 0) return fallback;
        try {
            Drawable d = ResourcesCompat.getDrawable(packRes, id, null);
            return d != null ? d : fallback;
        } catch (Resources.NotFoundException e) {
            return fallback;
        }
    }

    private void parseAppFilter(Context context) {
        // Most packs ship the file as res/xml/appfilter.xml; some only ship it under assets/.
        XmlPullParser parser = null;
        InputStream asset = null;
        try {
            int xmlId = packRes.getIdentifier("appfilter", "xml", packagePkg);
            if (xmlId != 0) {
                parser = packRes.getXml(xmlId);
            } else {
                Context packCtx = context.createPackageContext(packagePkg, 0);
                asset = packCtx.getAssets().open("appfilter.xml");
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                parser = factory.newPullParser();
                parser.setInput(asset, "UTF-8");
            }
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "item".equals(parser.getName())) {
                    String component = parser.getAttributeValue(null, "component");
                    String drawable = parser.getAttributeValue(null, "drawable");
                    if (component != null && drawable != null) {
                        componentToDrawable.put(component, drawable);
                    }
                }
            }
        } catch (Exception ignored) {
            // Parsing best-effort — bad pack = empty map = fallback to system icons
        } finally {
            if (parser instanceof XmlResourceParser) ((XmlResourceParser) parser).close();
            if (asset != null) try { asset.close(); } catch (Exception ignored) {}
        }
    }
}
