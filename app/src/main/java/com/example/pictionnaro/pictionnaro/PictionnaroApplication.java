package com.example.pictionnaro.pictionnaro;

import android.app.Application;

import com.example.pictionnaro.pictionnaro.Network.SyncedBoardManager;
import com.firebase.client.Firebase;

/**
 * Initialize Firebase with the application context and set disk persistence (to ensure our data survives
 * app restarts).
 * These must happen before the Firebase client is used.
 */
public class PicionisApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Firebase.setAndroidContext(this);
        Firebase.getDefaultConfig().setPersistenceEnabled(true);
        SyncedBoardManager.setContext(this);
    }
}
