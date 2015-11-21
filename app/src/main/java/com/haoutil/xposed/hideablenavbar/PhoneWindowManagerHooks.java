package com.haoutil.xposed.hideablenavbar;

import android.content.Context;
import android.content.res.Resources;
import android.view.MotionEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class PhoneWindowManagerHooks {
    private static final int SWIPE_NONE = 0;
    private static final int SWIPE_FROM_BOTTOM = 2;
    private static final int SWIPE_FROM_RIGHT = 3;
    private static final int SWIPE_TO_BOTTOM = 10;
    private static final int SWIPE_TO_RIGHT = 11;

    private int sNavBarWp, sNavBarHp, sNavBarHl;
    private Object sPhoneWindowManager;

    private long SWIPE_TIMEOUT_MS;
    private float[] mDownX;
    private float[] mDownY;
    private long[] mDownTime;
    private int mSwipeStartThreshold;
    private int mSwipeDistanceThreshold;

    public void doHook() {
        final String CLASS_PHONE_WINDOW_MANAGER = "com.android.internal.policy.impl.PhoneWindowManager";
        final String CLASS_IWINDOW_MANAGER = "android.view.IWindowManager";
        final String CLASS_WINDOW_MANAGER_FUNCS = "android.view.WindowManagerPolicy.WindowManagerFuncs";
        XposedHelpers.findAndHookMethod(CLASS_PHONE_WINDOW_MANAGER, null, "init",
                Context.class, CLASS_IWINDOW_MANAGER, CLASS_WINDOW_MANAGER_FUNCS, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sPhoneWindowManager = param.thisObject;
                        Context sContext = (Context) XposedHelpers.getObjectField(sPhoneWindowManager, "mContext");
                        Resources res = sContext.getResources();
                        int resWidthId = res.getIdentifier(
                                "navigation_bar_width", "dimen", "android");
                        int resHeightId = res.getIdentifier(
                                "navigation_bar_height", "dimen", "android");
                        int resHeightLandscapeId = res.getIdentifier(
                                "navigation_bar_height_landscape", "dimen", "android");
                        sNavBarWp = res.getDimensionPixelSize(resWidthId);
                        sNavBarHp = res.getDimensionPixelSize(resHeightId);
                        sNavBarHl = res.getDimensionPixelSize(resHeightLandscapeId);
                    }
                }
        );

        final String CLASS_SYSTEM_GESTURES_POINTER_EVENT_LISTENER = "com.android.internal.policy.impl.SystemGesturesPointerEventListener";
        final String CLASS_CALLBACKS = "com.android.internal.policy.impl.SystemGesturesPointerEventListener$Callbacks";
        final Class clazz = XposedHelpers.findClass(CLASS_SYSTEM_GESTURES_POINTER_EVENT_LISTENER, null);
        XposedHelpers.findAndHookConstructor(clazz, Context.class, CLASS_CALLBACKS, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                SWIPE_TIMEOUT_MS = XposedHelpers.getStaticLongField(clazz, "SWIPE_TIMEOUT_MS");
                mDownX = (float[]) XposedHelpers.getObjectField(param.thisObject, "mDownX");
                mDownY = (float[]) XposedHelpers.getObjectField(param.thisObject, "mDownY");
                mDownTime = (long[]) XposedHelpers.getObjectField(param.thisObject, "mDownTime");
                mSwipeStartThreshold = XposedHelpers.getIntField(param.thisObject, "mSwipeStartThreshold");
                mSwipeDistanceThreshold = XposedHelpers.getIntField(param.thisObject, "mSwipeDistanceThreshold");
            }
        });

        XposedHelpers.findAndHookMethod(clazz, "onPointerEvent", MotionEvent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final MotionEvent event = (MotionEvent) param.args[0];
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    switch ((int) XposedHelpers.callMethod(param.thisObject, "detectSwipe", event)) {
                        case SWIPE_FROM_BOTTOM:
                            if (XposedHelpers.getBooleanField(sPhoneWindowManager, "mNavigationBarOnBottom"))
                                showNavBar();
                            break;
                        case SWIPE_FROM_RIGHT:
                            if (!XposedHelpers.getBooleanField(sPhoneWindowManager, "mNavigationBarOnBottom"))
                                showNavBar();
                            break;
                        case SWIPE_TO_BOTTOM:
                            if (XposedHelpers.getBooleanField(sPhoneWindowManager, "mNavigationBarOnBottom"))
                                hideNavBar();
                            break;
                        case SWIPE_TO_RIGHT:
                            if (!XposedHelpers.getBooleanField(sPhoneWindowManager, "mNavigationBarOnBottom"))
                                hideNavBar();
                            break;
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod(clazz, "detectSwipe", int.class, long.class, float.class, float.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if ((Integer) param.getResult() == SWIPE_NONE) {
                    int screenHeight = XposedHelpers.getIntField(param.thisObject, "screenHeight");
                    int screenWidth = XposedHelpers.getIntField(param.thisObject, "screenWidth");

                    final int i = (Integer) param.args[0];
                    final long time = (Long) param.args[1];
                    final float x = (Float) param.args[2];
                    final float y = (Float) param.args[3];

                    final float fromX = mDownX[i];
                    final float fromY = mDownY[i];
                    final long elapsed = time - mDownTime[i];
                    if (y >= screenHeight - mSwipeStartThreshold
                            && fromY < y - mSwipeDistanceThreshold
                            && elapsed < SWIPE_TIMEOUT_MS) {
                        param.setResult(SWIPE_TO_BOTTOM);
                    } else if (x >= screenWidth - mSwipeStartThreshold
                            && fromX < x - mSwipeDistanceThreshold
                            && elapsed < SWIPE_TIMEOUT_MS) {
                        param.setResult(SWIPE_TO_RIGHT);
                    }
                }
            }
        });
    }

    private void showNavBar() {
        setNavBarDimensions(sNavBarWp, sNavBarHp, sNavBarHl);
    }

    private void hideNavBar() {
        setNavBarDimensions(0, 0, 0);
    }

    private void setNavBarDimensions(int wp, int hp, int hl) {
        int[] navigationBarWidthForRotation = (int[]) XposedHelpers.getObjectField(
                sPhoneWindowManager, "mNavigationBarWidthForRotation");
        int[] navigationBarHeightForRotation = (int[]) XposedHelpers.getObjectField(
                sPhoneWindowManager, "mNavigationBarHeightForRotation");
        final int portraitRotation = XposedHelpers.getIntField(sPhoneWindowManager, "mPortraitRotation");
        final int upsideDownRotation = XposedHelpers.getIntField(sPhoneWindowManager, "mUpsideDownRotation");
        final int landscapeRotation = XposedHelpers.getIntField(sPhoneWindowManager, "mLandscapeRotation");
        final int seascapeRotation = XposedHelpers.getIntField(sPhoneWindowManager, "mSeascapeRotation");
        navigationBarHeightForRotation[portraitRotation] =
                navigationBarHeightForRotation[upsideDownRotation] =
                        hp;
        navigationBarHeightForRotation[landscapeRotation] =
                navigationBarHeightForRotation[seascapeRotation] =
                        hl;

        navigationBarWidthForRotation[portraitRotation] =
                navigationBarWidthForRotation[upsideDownRotation] =
                        navigationBarWidthForRotation[landscapeRotation] =
                                navigationBarWidthForRotation[seascapeRotation] =
                                        wp;
        XposedHelpers.callMethod(sPhoneWindowManager, "updateRotation", false);
    }
}
