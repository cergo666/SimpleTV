package io.mediacast.simpletv;

import android.app.Application;
import android.content.Context;
import android.os.Debug;
import io.mediacast.simpletv.protection.StringVault;

public final class StvApp extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        enforceReleaseDebuggerPolicy();
        super.attachBaseContext(base);
        if (!BuildConfig.DEBUG) StringVault.initialize(base);
    }

    @Override
    public void onCreate() {
        enforceReleaseDebuggerPolicy();
        super.onCreate();
        CoreMessages.install(this);
    }

    public static void enforceReleaseDebuggerPolicy() {
        if (!BuildConfig.DEBUG && (Debug.isDebuggerConnected() || Debug.waitingForDebugger())) {
            throw new SecurityException();
        }
    }
}
