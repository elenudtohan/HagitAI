package com.example.hagitai;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class HagitApplication extends Application {

    private Activity currentActivity;
    private boolean isNetworkConnected = true;
    private View noInternetOverlay;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks();
        registerNetworkCallback();
    }

    private void registerActivityLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(@NonNull Activity activity) {}

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                currentActivity = activity;
                checkAndShowOverlay();
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (currentActivity == activity) {
                    currentActivity = null;
                }
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                if (currentActivity == activity) {
                    currentActivity = null;
                }
            }
        });
    }

    private void registerNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        // Check initial state
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork != null) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            isNetworkConnected = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            isNetworkConnected = false;
        }

        connectivityManager.registerNetworkCallback(networkRequest, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                isNetworkConnected = true;
                mainHandler.post(() -> removeOverlay());
            }

            @Override
            public void onLost(@NonNull Network network) {
                isNetworkConnected = false;
                mainHandler.post(() -> checkAndShowOverlay());
            }
        });
    }

    private void checkAndShowOverlay() {
        if (!isNetworkConnected && currentActivity != null) {
            ViewGroup rootView = currentActivity.findViewById(android.R.id.content);
            if (rootView != null) {
                if (noInternetOverlay != null && noInternetOverlay.getParent() != null) {
                    ((ViewGroup) noInternetOverlay.getParent()).removeView(noInternetOverlay);
                }
                noInternetOverlay = LayoutInflater.from(currentActivity).inflate(R.layout.overlay_no_internet, rootView, false);
                rootView.addView(noInternetOverlay);
            }
        } else if (isNetworkConnected) {
            removeOverlay();
        }
    }

    private void removeOverlay() {
        if (noInternetOverlay != null && noInternetOverlay.getParent() != null) {
            ((ViewGroup) noInternetOverlay.getParent()).removeView(noInternetOverlay);
            noInternetOverlay = null;
        }
    }
}
