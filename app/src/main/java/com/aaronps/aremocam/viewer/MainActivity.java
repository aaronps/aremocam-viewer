package com.aaronps.aremocam.viewer;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

public class MainActivity extends Activity implements ConnectFragment.OnConnectFragmentInteractionListener {

    ConnectFragment mConnectFragment;
    CameraFragment  mCameraFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // these two lines to show the app even when locked...
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

//         this one switch the screen on, and keep it on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mConnectFragment = new ConnectFragment();

        getFragmentManager().beginTransaction()
                .add(R.id.layout_root, mConnectFragment)
                .commit();

        mCameraFragment = new CameraFragment();

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("Main", "onPause");
    }


    @Override
    public void onConnectParameters(String host, int port) {
        Log.d("Main", "Connect parameters arrived: " + host + ":" + port);
        mCameraFragment.connect(host, port);

        this.runOnUiThread(() -> {
            Log.d("Main", "Detaching mConnectFragment");
            getFragmentManager().beginTransaction()
                    .replace(R.id.layout_root, mCameraFragment)
                    .addToBackStack(null)
                    .commit();
        });

    }


}
