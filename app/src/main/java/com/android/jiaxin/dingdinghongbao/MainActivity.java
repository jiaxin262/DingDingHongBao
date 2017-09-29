package com.android.jiaxin.dingdinghongbao;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener, AccessibilityManager.AccessibilityStateChangeListener{
    public static final String TAG = "MainActivity";

    private TextView mOpenServiceTv;
    private AccessibilityManager mAccessibilityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOpenServiceTv = (TextView) findViewById(R.id.tv_open_service);
        mOpenServiceTv.setOnClickListener(this);

        mAccessibilityManager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        mAccessibilityManager.addAccessibilityStateChangeListener(this);

        updateServiceStatus();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        updateServiceStatus();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        mAccessibilityManager.removeAccessibilityStateChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.tv_open_service) {
            openAccessibility();
        }
    }

    public void openAccessibility() {
        try {
            Toast.makeText(this, getString(isServiceEnabled() ? R.string.turn_off_toast : R.string.turn_on_toast), Toast.LENGTH_SHORT).show();
            Intent accessibleIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(accessibleIntent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.turn_on_error_toast), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

    }

    @Override
    public void onAccessibilityStateChanged(boolean b) {
        Log.d(TAG, "onAccessibilityStateChanged");
        updateServiceStatus();
    }

    private void updateServiceStatus() {
        if (isServiceEnabled()) {
            mOpenServiceTv.setText(R.string.close_service);
        } else {
            mOpenServiceTv.setText(R.string.open_service);
        }
    }

    private boolean isServiceEnabled() {
        List<AccessibilityServiceInfo> accessibilityServices =
                mAccessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo info : accessibilityServices) {
            if (info.getId().equals(getPackageName() + "/.services.DdHbService")) {
                return true;
            }
        }
        return false;
    }
}
