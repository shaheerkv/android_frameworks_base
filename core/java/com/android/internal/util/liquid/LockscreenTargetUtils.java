/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.liquid;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.TargetDrawable;

import java.io.File;

public final class LockscreenTargetUtils {
    private static final String TAG = "LockscreenTargetUtils";

    private LockscreenTargetUtils() {
    }

    public static int getMaxTargets(Context context) {
        if (!DeviceUtils.isPhone(context) || isEightTargets(context)) {
            return GlowPadView.MAX_TABLET_TARGETS;
        }

        return GlowPadView.MAX_PHONE_TARGETS;
    }

    public static int getTargetOffset(Context context) {
        boolean isLandscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        boolean isEightOrLarge = !DeviceUtils.isPhone(context) || isEightTargets(context);
        return isLandscape && !isEightOrLarge ? 2 : 0;
    }

    /**
     * Create a layered drawable
     * @param back - Background image to use when target is active
     * @param front - Front image to use for target
     * @param inset - Target inset padding
     * @param frontBlank - Whether the front image for active target should be blank
     * @return StateListDrawable
     */
    public static StateListDrawable getLayeredDrawable(Context context,
            Drawable back, Drawable front, int inset, boolean frontBlank) {

        PackageManager pm = context.getPackageManager();
        Resources keyguardResources = null;
        try {
            keyguardResources = pm.getResourcesForApplication("com.android.keyguard");
        } catch (Exception e) {
            e.printStackTrace();
        }

        final Resources res = context.getResources();
        InsetDrawable[] inactivelayer = new InsetDrawable[2];
        InsetDrawable[] activelayer = new InsetDrawable[2];

        inactivelayer[0] = new InsetDrawable(keyguardResources.getDrawable(
                    keyguardResources.getIdentifier(
                    "com.android.keyguard:drawable/ic_lockscreen_lock_pressed",
                    null, null)), 0, 0, 0, 0);
        inactivelayer[1] = new InsetDrawable(front, inset, inset, inset, inset);

        activelayer[0] = new InsetDrawable(back, 0, 0, 0, 0);
        activelayer[1] = new InsetDrawable(
                frontBlank ? res.getDrawable(android.R.color.transparent) : front,
                inset, inset, inset, inset);

        LayerDrawable inactiveLayerDrawable = new LayerDrawable(inactivelayer);
        inactiveLayerDrawable.setId(0, 0);
        inactiveLayerDrawable.setId(1, 1);

        LayerDrawable activeLayerDrawable = new LayerDrawable(activelayer);
        activeLayerDrawable.setId(0, 0);
        activeLayerDrawable.setId(1, 1);

        StateListDrawable states = new StateListDrawable();
        states.addState(TargetDrawable.STATE_INACTIVE, inactiveLayerDrawable);
        states.addState(TargetDrawable.STATE_ACTIVE, activeLayerDrawable);
        states.addState(TargetDrawable.STATE_FOCUSED, activeLayerDrawable);

        return states;
    }

    public static Drawable getDrawableFromFile(Context context, String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            return null;
        }

        if (fileName.startsWith("lockscreen_")) {
            return new BitmapDrawable(context.getResources(),
                    ImageHelper.getRoundedCornerBitmap(BitmapFactory.decodeFile(fileName)));
        } else {
            return new BitmapDrawable(context.getResources(), BitmapFactory.decodeFile(fileName));
        }
    }

    public static int getInsetForIconType(Context context, String type) {
        if (TextUtils.equals(type, GlowPadView.ICON_RESOURCE)) {
            return 0;
        }

        PackageManager pm = context.getPackageManager();
        Resources keyguardResources = null;
        try {
            keyguardResources = pm.getResourcesForApplication("com.android.keyguard");
        } catch (Exception e) {
            e.printStackTrace();
        }

        final Resources res = context.getResources();
        int inset = keyguardResources.getDimensionPixelSize(keyguardResources.getIdentifier(
                "com.android.keyguard:dimen/lockscreen_target_inset", null, null));

        if (TextUtils.equals(type, GlowPadView.ICON_FILE)) {
            inset += keyguardResources.getDimensionPixelSize(keyguardResources.getIdentifier(
                    "com.android.keyguard:dimen/lockscreen_target_icon_file_inset", null, null));
        }

        return inset;
    }

    public static Drawable getDrawableFromResources(Context context,
            String packageName, String identifier, boolean activated) {
        Resources res;

        if (packageName != null) {
            try {
                res = context.getPackageManager()
                        .getResourcesForApplication(packageName);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Could not fetch icons from package " + packageName);
                return null;
            }
        } else {
            res = context.getResources();
            packageName = "android";
        }

        if (activated) {
            identifier = identifier.replaceAll("_normal", "_activated");
        }

        try {
            int id = res.getIdentifier(identifier, "drawable", packageName);
            return res.getDrawable(id);
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "Could not resolve icon " + identifier + " in " + packageName, e);
        }

        return null;
    }

    public static Drawable getDrawableFromIntent(Context context, Intent intent) {
        final Resources res = context.getResources();
        final PackageManager pm = context.getPackageManager();
        ActivityInfo info = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);

        if (info == null) {
            return res.getDrawable(android.R.drawable.sym_def_app_icon);
        }

        Drawable icon = info.loadIcon(pm);
        return new BitmapDrawable(res, resizeIconTarget(context, icon));
    }

    private static Bitmap resizeIconTarget(Context context, Drawable icon) {
        Resources res = context.getResources();
        int size = (int) res.getDimension(android.R.dimen.app_icon_size);

        int width = size;
        int height = size;

        if (icon instanceof PaintDrawable) {
            PaintDrawable painter = (PaintDrawable) icon;
            painter.setIntrinsicWidth(width);
            painter.setIntrinsicHeight(height);
        } else if (icon instanceof BitmapDrawable) {
            // Ensure the bitmap has a density.
            BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
            Bitmap bitmap = bitmapDrawable.getBitmap();
            if (bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                bitmapDrawable.setTargetDensity(context.getResources().getDisplayMetrics());
            }
        }
        int sourceWidth = icon.getIntrinsicWidth();
        int sourceHeight = icon.getIntrinsicHeight();
        if (sourceWidth > 0 && sourceHeight > 0) {
            // There are intrinsic sizes.
            if (width < sourceWidth || height < sourceHeight) {
                // It's too big, scale it down.
                final float ratio = (float) sourceWidth / sourceHeight;
                if (sourceWidth > sourceHeight) {
                    height = (int) (width / ratio);
                } else if (sourceHeight > sourceWidth) {
                    width = (int) (height * ratio);
                }
            } else if (sourceWidth < width && sourceHeight < height) {
                // Don't scale up the icon
                width = sourceWidth;
                height = sourceHeight;
            }
        }

        final Bitmap bitmap = Bitmap.createBitmap(size, size,
                Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas();
        canvas.setBitmap(bitmap);

        final int left = (size - width) / 2;
        final int top = (size - height) / 2;

        Rect oldBounds = new Rect();
        oldBounds.set(icon.getBounds());
        icon.setBounds(left, top, left + width, top + height);
        icon.draw(canvas);
        icon.setBounds(oldBounds);
        canvas.setBitmap(null);

        return bitmap;
    }

    public static boolean isShortcuts(Context context) {
        final String apps = Settings.System.getStringForUser(context.getContentResolver(),
                Settings.System.LOCKSCREEN_SHORTCUTS, UserHandle.USER_CURRENT);
        if (apps == null || apps.isEmpty()) {
            return false;
        }
        return true;
    }

    public static boolean isEightTargets(Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                    Settings.System.LOCKSCREEN_EIGHT_TARGETS, 0,
                    UserHandle.USER_CURRENT) == 1;
    }
}
