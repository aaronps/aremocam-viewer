package com.aaronps.aremocam.viewer;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity implements ConnectFragment.OnConnectFragmentInteractionListener {

    ConnectFragment connectFragment;
    CameraFragment cameraFragment;
    CameraThread cameraThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connectFragment = new ConnectFragment();

        getSupportFragmentManager().beginTransaction()
                .add(R.id.layout_root, connectFragment)
                .commit();

        cameraFragment = new CameraFragment();

    }

    @Override
    protected void onPause()
    {
        super.onPause();
        Log.d("Main", "onPause");
        stopConnection();
    }

    private void stopConnection()
    {
        if ( cameraThread != null )
        {
            try
            {
                cameraThread.stop();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                Log.d("Main", "Interrupted while finishing camera thread", e);
            }
            finally
            {
                cameraThread = null;
            }
        }
    }

    @Override
    public void onConnectParameters(String host, int port)
    {
        Log.d("Main", "Connect parameters arrived: " + host + ":" + port);

        if ( cameraThread != null )
        {
            try
            {
                cameraThread.stop();
            }
            catch (InterruptedException e)
            {
                Log.d("Main", "Interruped while canceling old mCameraThread", e);
            }

            cameraThread = null;
        }

        cameraThread = new CameraThread(cameraFragment, host, port);
        cameraThread.start();

        this.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                getSupportFragmentManager().beginTransaction()
                        .detach(connectFragment)
                        .add(R.id.layout_root, cameraFragment)
                        .commit();
            }
        });

//        getSupportFragmentManager().beginTransaction()
//                .detach(connectFragment)
//                .add(R.id.layout_root, cameraFragment)
//                .commit();
    }


}
