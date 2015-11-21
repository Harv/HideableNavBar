package com.haoutil.xposed.hideablenavbar;

import de.robv.android.xposed.IXposedHookZygoteInit;

public class XposedMod implements IXposedHookZygoteInit {
    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        new PhoneWindowManagerHooks().doHook();
    }
}
