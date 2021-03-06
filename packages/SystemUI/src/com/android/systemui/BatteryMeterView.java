/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;

import com.android.internal.util.omni.ColorUtils;
import com.android.internal.util.liquid.DevUtils;

import com.android.systemui.R;

public class BatteryMeterView extends View implements DemoMode {
    public static final String TAG = BatteryMeterView.class.getSimpleName();
    public static final String ACTION_LEVEL_TEST = "com.android.systemui.BATTERY_LEVEL_TEST";

    public static final boolean ENABLE_PERCENT = true;
    public static final boolean SINGLE_DIGIT_PERCENT = false;
    public boolean SHOW_100_PERCENT = false;

    public static final int BATTERY_STYLE_NORMAL                = 0;
    public static final int BATTERY_STYLE_PERCENT               = 1;
    public static final int BATTERY_STYLE_ICON_PERCENT          = 2;
    public static final int BATTERY_STYLE_CIRCLE                = 3;
    public static final int BATTERY_STYLE_CIRCLE_PERCENT        = 4;
    public static final int BATTERY_STYLE_DOTTED_CIRCLE         = 5;
    public static final int BATTERY_STYLE_DOTTED_CIRCLE_PERCENT = 6;
    public static final int BATTERY_STYLE_ICON_JBSTYLE_PERCENT  = 7;
    public static final int BATTERY_STYLE_GONE                  = 8;

    private static final int OPAQUE_MASK = 0xff000000;	
    private static final int FRAME_MASK = 0x66000000;

    public static final int FULL = 96;
    public static final int EMPTY = 4;

    public static final float SUBPIXEL = 0.4f;  // inset rects for softer edges

    int[] mColors;

    boolean mShowIcon = true;
    boolean mIsQuickSettings = false;
    boolean mShowPercent = false;
    Paint mFramePaint, mBatteryPaint, mWarningTextPaint, mTextPaint, mBoltPaint;
    int mButtonHeight;
    private float mTextHeight, mWarningTextHeight;

    private int mHeight;
    private int mWidth;
    private String mWarningString;
    private int mChangeColor = -3;
    private final float[] mBoltPoints;
    private final Path mBoltPath = new Path();

    private final RectF mFrame = new RectF();
    private final RectF mButtonFrame = new RectF();
    private final RectF mClipFrame = new RectF();
    private final RectF mBoltFrame = new RectF();

    private int mBatteryStyle;
    private int mBatteryColor;
    private int mPercentageColor;
    private int mPercentageChargingColor;
    private boolean mPercentageOnly = false;
    private String mBatteryTypeView;

    private class BatteryTracker extends BroadcastReceiver {
        public static final int UNKNOWN_LEVEL = -1;

        // current battery status
        int level = UNKNOWN_LEVEL;
        String percentStr;
        int plugType;
        boolean plugged;
        int health;
        int status;
        String technology;
        int voltage;
        int temperature;
        boolean testmode = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                if (testmode && ! intent.getBooleanExtra("testmode", false)) return;

                level = (int)(100f
                        * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                        / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));

                plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                plugged = plugType != 0;
                health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH,
                        BatteryManager.BATTERY_HEALTH_UNKNOWN);
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN);
                technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
                voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);

                setContentDescription(
                        context.getString(R.string.accessibility_battery_level, level));
                updateSettings(mIsQuickSettings);
            } else if (action.equals(ACTION_LEVEL_TEST)) {
                testmode = true;
                post(new Runnable() {
                    int curLevel = 0;
                    int incr = 1;
                    int saveLevel = level;
                    int savePlugged = plugType;
                    Intent dummy = new Intent(Intent.ACTION_BATTERY_CHANGED);
                    @Override
                    public void run() {
                        if (curLevel < 0) {
                            testmode = false;
                            dummy.putExtra("level", saveLevel);
                            dummy.putExtra("plugged", savePlugged);
                            dummy.putExtra("testmode", false);
                        } else {
                            dummy.putExtra("level", curLevel);
                            dummy.putExtra("plugged", incr > 0 ? BatteryManager.BATTERY_PLUGGED_AC : 0);
                            dummy.putExtra("testmode", true);
                        }
                        getContext().sendBroadcast(dummy);

                        if (!testmode) return;

                        curLevel += incr;
                        if (curLevel == 100) {
                            incr *= -1;
                        }
                        postDelayed(this, 200);
                    }
                });
            }
        }
    }

    BatteryTracker mTracker = new BatteryTracker();

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(ACTION_LEVEL_TEST);
        final Intent sticky = getContext().registerReceiver(mTracker, filter);
        if (sticky != null) {
            // preload the battery level
            mTracker.onReceive(getContext(), sticky);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        getContext().unregisterReceiver(mTracker);
    }

    public BatteryMeterView(Context context) {
        this(context, null, 0);
    }

    public BatteryMeterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryMeterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final Resources res = context.getResources();
        TypedArray levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray colors = res.obtainTypedArray(R.array.batterymeter_color_values);

        TypedArray batteryType = context.obtainStyledAttributes(attrs,
            com.android.systemui.R.styleable.BatteryIcon, 0, 0);
        mBatteryTypeView = batteryType.getString(
            com.android.systemui.R.styleable.BatteryIcon_batteryView);

        if (mBatteryTypeView == null) {
            mBatteryTypeView = "statusbar";
        }

        final int N = levels.length();
        mColors = new int[2*N];
        for (int i=0; i<N; i++) {
            mColors[2*i] = levels.getInt(i, 0);
            mColors[2*i+1] = colors.getColor(i, 0);
        }
        levels.recycle();
        colors.recycle();
        batteryType.recycle();

        mWarningString = context.getString(R.string.battery_meter_very_low_overlay_symbol);

        mFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFramePaint.setDither(true);
        mFramePaint.setStrokeWidth(0);
        mFramePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mFramePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP));

        mBatteryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBatteryPaint.setDither(true);
        mBatteryPaint.setStrokeWidth(0);
        mBatteryPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        mWarningTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWarningTextPaint.setColor(mColors[1]);
        Typeface font = Typeface.create("sans-serif", Typeface.BOLD);
        mWarningTextPaint.setTypeface(font);
        mWarningTextPaint.setTextAlign(Paint.Align.CENTER);

        mBoltPaint = new Paint();
        mBoltPaint.setAntiAlias(true);
        mBoltPoints = loadBoltPoints(res);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        updateSettings(mIsQuickSettings);
    }

    private static float[] loadBoltPoints(Resources res) {
        final int[] pts = res.getIntArray(R.array.batterymeter_bolt_points);
        int maxX = 0, maxY = 0;
        for (int i = 0; i < pts.length; i += 2) {
            maxX = Math.max(maxX, pts[i]);
            maxY = Math.max(maxY, pts[i + 1]);
        }
        final float[] ptsF = new float[pts.length];
        for (int i = 0; i < pts.length; i += 2) {
            ptsF[i] = (float)pts[i] / maxX;
            ptsF[i + 1] = (float)pts[i + 1] / maxY;
        }
        return ptsF;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mHeight = h;
        mWidth = w;
        mWarningTextPaint.setTextSize(h * 0.75f);
        mWarningTextHeight = -mWarningTextPaint.getFontMetrics().ascent;
    }

    private int getColorForLevel(int percent) {
        int thresh, color = 0;
        for (int i=0; i<mColors.length; i+=2) {
            thresh = mColors[i];
            color = mColors[i+1];
            if (percent <= thresh) {
                if (mChangeColor != -3 && (mBatteryColor == -2 || mBatteryColor == 0xFFFFFFFF)) {
                    return mChangeColor;
                } else {
                    return color;
                }
            }
        }
        if (mChangeColor != -3 && (mBatteryColor == -2 || mBatteryColor == 0xFFFFFFFF)) {
            return mChangeColor;
        }
        return color;
    }

    public void updateSettings(int color) {
        mChangeColor = color;
        postInvalidate();
    }

    @Override
    public void draw(Canvas c) {
        BatteryTracker tracker = mDemoMode ? mDemoTracker : mTracker;
        final int level = tracker.level;

        if (level == BatteryTracker.UNKNOWN_LEVEL) return;

        float drawFrac = (float) level / 100f;
        final int pt = getPaddingTop();
        final int pl = getPaddingLeft();
        final int pr = getPaddingRight();
        final int pb = getPaddingBottom();
        int height = mHeight - pt - pb;
        int width = mWidth - pl - pr;

        mButtonHeight = (int) (height * 0.12f);

        if (mBatteryStyle == BATTERY_STYLE_ICON_JBSTYLE_PERCENT) {
          mFrame.set(0, 0, (width / 3), height);
          mFrame.offset(pl + ((width / 3) * 2), pt);
        } else {
          mFrame.set(0, 0, width, height);
          mFrame.offset(pl, pt);
        }

        mButtonFrame.set(
                mFrame.left + width * 0.25f,
                mFrame.top,
                mFrame.right - width * 0.25f,
                mFrame.top + mButtonHeight + 5 /*cover frame border of intersecting area*/);

        mButtonFrame.top += SUBPIXEL;
        mButtonFrame.left += SUBPIXEL;
        mButtonFrame.right -= SUBPIXEL;

        mFrame.top += mButtonHeight;
        mFrame.left += SUBPIXEL;
        mFrame.top += SUBPIXEL;
        mFrame.right -= SUBPIXEL;
        mFrame.bottom -= SUBPIXEL;

        // first, draw the battery shape
        if (mShowIcon) {
		    int color = 0;
			if (mChangeColor != -3 && (mBatteryColor == -2 || mBatteryColor == 0xFFFFFFFF)) {
				color = ColorUtils.changeColorTransparency(mChangeColor, 75);
			} else {
				color = DevUtils.extractRGB(mBatteryColor) | FRAME_MASK;
			}
			mFramePaint.setColor(color);
            c.drawRect(mFrame, mFramePaint);
        }

        // fill 'er up
        int color = 0;
        if (tracker.plugged) {
            if (mChangeColor != -3 && (mBatteryColor == -2 || mBatteryColor == 0xFFFFFFFF)) {
                color = mChangeColor;
            } else {
                color = mBatteryColor;
            }
        } else {
            color = getColorForLevel(level);
        }
        mBatteryPaint.setColor(color);

        if (level >= FULL) {
            drawFrac = 1f;
        } else if (level <= EMPTY) {
            drawFrac = 0f;
        }

        if (mShowIcon) {
            c.drawRect(mButtonFrame, drawFrac == 1f ? mBatteryPaint : mFramePaint);
        }
        mClipFrame.set(mFrame);
        mClipFrame.top += (mFrame.height() * (1f - drawFrac));

        c.save(Canvas.CLIP_SAVE_FLAG);
        c.clipRect(mClipFrame);
        if (mShowIcon) {
            c.drawRect(mFrame, mBatteryPaint);
        }
        c.restore();
		
		if (mChangeColor != -3 && (mBatteryColor == -2 || mBatteryColor == 0xFFFFFFFF)) {
            if (ColorUtils.isBrightColor(mChangeColor)) {
                color = Color.BLACK;
            } else {
                color = Color.WHITE;
            }
        } else {
            color = Color.WHITE;
        }

        if (tracker.plugged && !mPercentageOnly) {
			if (mChangeColor != -3 && (mPercentageChargingColor == -2 || mPercentageChargingColor == 0xFFFFFFFF)) {
                mBoltPaint.setColor(color);
            } else {
                mBoltPaint.setColor(mPercentageChargingColor);
            }
            // draw the bolt
            final float bl = mFrame.left + mFrame.width() / 4.5f;
            final float bt = mFrame.top + mFrame.height() / 6f;
            final float br = mFrame.right - mFrame.width() / 7f;
            final float bb = mFrame.bottom - mFrame.height() / 10f;
            if (mBoltFrame.left != bl || mBoltFrame.top != bt
                    || mBoltFrame.right != br || mBoltFrame.bottom != bb) {
                mBoltFrame.set(bl, bt, br, bb);
                mBoltPath.reset();
                mBoltPath.moveTo(
                        mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                        mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
                for (int i = 2; i < mBoltPoints.length; i += 2) {
                    mBoltPath.lineTo(
                            mBoltFrame.left + mBoltPoints[i] * mBoltFrame.width(),
                            mBoltFrame.top + mBoltPoints[i + 1] * mBoltFrame.height());
                }
                mBoltPath.lineTo(
                        mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                        mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
            }
            c.drawPath(mBoltPath, mBoltPaint);
        } else if (level <= EMPTY && mBatteryStyle == BATTERY_STYLE_NORMAL) {
            final float x = mWidth * 0.5f;
            final float y = (mHeight + mWarningTextHeight) * 0.48f;
            c.drawText(mWarningString, x, y, mWarningTextPaint);
        }
        if (mShowPercent && !(mBatteryStyle == BATTERY_STYLE_ICON_PERCENT && tracker.plugged)) {
            if (mPercentageOnly) {
                DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
                if (mBatteryTypeView.equals("statusbar")) {
                    mTextPaint.setTextSize((int) (metrics.density * 16f));
                } else if (mBatteryTypeView.equals("quicksettings")) {
                    mTextPaint.setTextSize((int) (metrics.density * 22f + 0.5f));
                }
            } else if (mBatteryStyle == BATTERY_STYLE_ICON_PERCENT) {
                mTextPaint.setTextSize(height *
                                      (SINGLE_DIGIT_PERCENT ? 0.75f
                                        : (tracker.level == 100 ? 0.38f : 0.5f)));
            } else if (mBatteryStyle == BATTERY_STYLE_ICON_JBSTYLE_PERCENT) {
                DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
                if (mBatteryTypeView.equals("statusbar")) {
                    mTextPaint.setTextSize(height *
                                          (SINGLE_DIGIT_PERCENT ? 0.95f
                                            : (tracker.level == 100 ? 0.58f : 0.7f)));
                } else if (mBatteryTypeView.equals("quicksettings")) {
                    mTextPaint.setTextSize((int) (metrics.density * 14f + 0.5f));
                }
            }
            mTextHeight = -mTextPaint.getFontMetrics().ascent;

            int textColor = mPercentageColor;
            if (mChangeColor != -3 && (mPercentageColor == -2 || mPercentageColor == 0xFFFFFFFF)) {
                textColor = mChangeColor;
            }
            mTextPaint.setColor(textColor);
            String str;
            if (mPercentageOnly) {
                str = String.valueOf(SINGLE_DIGIT_PERCENT ? (level/10) : level) + "%";
            } else {
                str = String.valueOf(SINGLE_DIGIT_PERCENT ? (level/10) : level);
            }

            final float x;
            if (mBatteryStyle == BATTERY_STYLE_ICON_JBSTYLE_PERCENT) {
              if (mBatteryTypeView.equals("statusbar")) {
                x = (mWidth * 0.5f) - (mWidth * 0.25f);
              } else if (mBatteryTypeView.equals("quicksettings")) {
                x = (mWidth * 0.5f) - (mWidth * 0.2f);
              } else {
                x = mWidth * 0.5f;
              }
            } else {
              x = mWidth * 0.5f;
            }
            final float y = (mHeight + mTextHeight) * 0.47f;
            c.drawText(str,
                    x,
                    y,
                    mTextPaint);
        }
    }

    private boolean mDemoMode;
    private BatteryTracker mDemoTracker = new BatteryTracker();

    @Override
    public void dispatchDemoCommand(String command, Bundle args) {
        if (!mDemoMode && command.equals(COMMAND_ENTER)) {
            mDemoMode = true;
            mDemoTracker.level = mTracker.level;
            mDemoTracker.plugged = mTracker.plugged;
        } else if (mDemoMode && command.equals(COMMAND_EXIT)) {
            mDemoMode = false;
            postInvalidate();
        } else if (mDemoMode && command.equals(COMMAND_BATTERY)) {
           String level = args.getString("level");
           String plugged = args.getString("plugged");
           if (level != null) {
               mDemoTracker.level = Math.min(Math.max(Integer.parseInt(level), 0), 100);
           }
           if (plugged != null) {
               mDemoTracker.plugged = Boolean.parseBoolean(plugged);
           }
           postInvalidate();
        }
    }

    public void updateSettings(final boolean isQuickSettingsTile) {
        ContentResolver resolver = mContext.getContentResolver();

        mBatteryStyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY, 0, UserHandle.USER_CURRENT);

        mIsQuickSettings = isQuickSettingsTile;

        if (isQuickSettingsTile && mBatteryStyle == BATTERY_STYLE_GONE) {
            mBatteryStyle = BATTERY_STYLE_NORMAL;
        }

        mBatteryColor = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_COLOR, -2, UserHandle.USER_CURRENT);
        mPercentageColor = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_TEXT_COLOR, -2, UserHandle.USER_CURRENT);
        mPercentageChargingColor = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_TEXT_CHARGING_COLOR, -2,
                UserHandle.USER_CURRENT);

        boolean activated = (mBatteryStyle == BATTERY_STYLE_NORMAL ||
                      mBatteryStyle == BATTERY_STYLE_PERCENT ||
                      mBatteryStyle == BATTERY_STYLE_ICON_PERCENT ||
                      mBatteryStyle == BATTERY_STYLE_ICON_JBSTYLE_PERCENT);

        setVisibility(activated ? View.VISIBLE : View.GONE);

        if (activated) {
            LinearLayout.LayoutParams lp = null;
            float width = 0f;
            float height = 0f;
            Resources res = mContext.getResources();
            DisplayMetrics metrics = res.getDisplayMetrics();
            if (mBatteryTypeView.equals("statusbar")) {
                height = metrics.density * 16f + 0.5f;
                if (mBatteryStyle == BATTERY_STYLE_PERCENT && (mTracker.level == 100)) {
                    width = metrics.density * 38f + 0.5f;
                } else if (mBatteryStyle == BATTERY_STYLE_PERCENT && (mTracker.level < 10)) {
                    width = metrics.density * 18f + 0.5f;
                } else if (mBatteryStyle == BATTERY_STYLE_PERCENT) {
                    width = metrics.density * 28f + 0.5f;
                } else if (mBatteryStyle == BATTERY_STYLE_ICON_JBSTYLE_PERCENT) {
                    width = metrics.density * 28f + 0.5f;
                } else {
                    width = metrics.density * 10.5f + 0.5f;
                }
                lp = new LinearLayout.LayoutParams((int) width, (int) height);
                lp.setMarginStart((int) (metrics.density * 6f + 0.5f));
                lp.setMargins(0, 0, 0, (int) (metrics.density * 0.5f + 0.5f));
                setLayoutParams(lp);
            } else if (mBatteryTypeView.equals("quicksettings")) {
                height = metrics.density * 32f + 0.5f;
                if (mBatteryStyle == BATTERY_STYLE_PERCENT) {
                    width = metrics.density * 52f + 0.5f;
                } else if (mBatteryStyle == BATTERY_STYLE_ICON_JBSTYLE_PERCENT) {
                    width = metrics.density * 37f + 0.5f;
                } else {
                    width = metrics.density * 22f + 0.5f;
                }
                lp = new LinearLayout.LayoutParams((int) width, (int) height);
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                setLayoutParams(lp);
            }

            updateBattery();
        }
    }

    private void updateBattery() {
        mPercentageOnly = false;
        SHOW_100_PERCENT = false;

        Typeface font = Typeface.create("sans-serif", Typeface.BOLD);
        if (mBatteryStyle == BATTERY_STYLE_NORMAL) {
            mShowIcon = true;
            mShowPercent = false;
        } else if (mBatteryStyle == BATTERY_STYLE_ICON_PERCENT) {
            mShowIcon = true;
            mShowPercent = true;
            SHOW_100_PERCENT = true;
        } else if (mBatteryStyle == BATTERY_STYLE_ICON_JBSTYLE_PERCENT) {
            font = Typeface.create("sans-serif", Typeface.NORMAL);
            mShowIcon = true;
            mShowPercent = true;
            SHOW_100_PERCENT = true;
        } else if (mBatteryStyle == BATTERY_STYLE_PERCENT) {
            font = Typeface.create("sans-serif", Typeface.NORMAL);
            mShowIcon = false;
            mShowPercent = true;
            mPercentageOnly = true;
            SHOW_100_PERCENT = true;
        }
        mTextPaint.setTypeface(font);

        BatteryTracker tracker = mDemoMode ? mDemoTracker : mTracker;

        if (tracker.level <= 14 && !tracker.plugged) {
            mBatteryPaint.setColor(Color.RED);
        } else if (mBatteryColor == -2) {
            mBatteryPaint.setColor(mContext.getResources().getColor(
                    R.color.batterymeter_charge_color));
        } else {
        mBatteryPaint.setColor(DevUtils.extractRGB(mBatteryColor) | OPAQUE_MASK);
        mFramePaint.setColor(DevUtils.extractRGB(mBatteryColor) | FRAME_MASK);
        }

        boolean isInLevelCharge = false;
        if (tracker.level <= 14 && (mBatteryStyle == BATTERY_STYLE_PERCENT
           || mBatteryStyle == BATTERY_STYLE_ICON_JBSTYLE_PERCENT
           || mBatteryStyle == BATTERY_STYLE_ICON_PERCENT)) {
            mTextPaint.setColor(Color.RED);
            isInLevelCharge = true;
        } else if (tracker.level >= 90 && tracker.plugged &&
           (mBatteryStyle == BATTERY_STYLE_PERCENT
            || mBatteryStyle == BATTERY_STYLE_ICON_JBSTYLE_PERCENT)) {
            mTextPaint.setColor(Color.GREEN);
            isInLevelCharge = true;
        } else if (mPercentageColor == -2) {
            if (mBatteryStyle == BATTERY_STYLE_ICON_PERCENT) {
                mTextPaint.setColor(mContext.getResources().getColor(
                        R.color.batterymeter_bolt_color));
            } else {
                mTextPaint.setColor(mContext.getResources().getColor(
                        R.color.batterymeter_charge_color));
            }
        } else {
        mTextPaint.setColor(mPercentageColor);
        }

        if (tracker.plugged) {
            if (mPercentageChargingColor == -2) {
                if (mBatteryStyle == BATTERY_STYLE_PERCENT) {
                    mBoltPaint.setColor(mContext.getResources().getColor(
                            R.color.batterymeter_charge_color));
                } else {
                    mBoltPaint.setColor(mContext.getResources().getColor(
                            R.color.batterymeter_bolt_color));
                }
            } else {
                mBoltPaint.setColor(mPercentageChargingColor);
            }
            if (mBatteryStyle == BATTERY_STYLE_PERCENT && !isInLevelCharge) {
                mTextPaint.setColor(mBoltPaint.getColor());
            }
        }
        postInvalidate();
    }
}
