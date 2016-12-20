package com.aaronps.aremocam.viewer;


import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.nio.IntBuffer;
import java.util.Arrays;


/**
 * A simple {@link Fragment} subclass.
 */
public class CameraFragment extends Fragment implements CameraThread.Listener {
    CameraThread mCameraThread;
    ImageView    mImageView;
    Bitmap       mRemoteBitmap;
    int[]        mIntArray;
    IntBuffer    mIntBuffer;
    CameraInfo   mCameraInfo;

    final Runnable mInvalidater = new Runnable() {
        @Override
        public void run() {
            mImageView.invalidate();
        }
    };

    public CameraFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_camera, container, false);

        mImageView = (ImageView) view.findViewById(R.id.imageView);

        return view;
    }


    @Override
    public void onConnected(CameraThread ct) {
        mCameraThread = ct;
        ct.request_sizelist();
    }

    @Override
    public void onDisconnected(CameraThread ct) {

    }

    @Override
    public void onVideoReady(CameraThread ct, CameraInfo info) {
        Log.d("CameraFragment", "Video Ready: " + info);
        mCameraInfo = info;

        mRemoteBitmap = Bitmap.createBitmap(info.width, info.height, Bitmap.Config.ARGB_8888);

        mIntArray = new int[info.width * info.height];
        mIntBuffer = IntBuffer.wrap(mIntArray);

        Arrays.fill(mIntArray, 0);


        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mImageView.setImageBitmap(mRemoteBitmap);
                mCameraThread.request_pic();
            }
        });
    }

    private static String findSmallestSize(final String[] sizes) {
        int    smallest_size = -1;
        String smallest      = null;

        for (String s : sizes)
        {
            // @todo optimize with s.indexOf("x") instead of a regex.
            final String[] parts = s.split("x");
            final int      total = Integer.parseInt(parts[0], 10) * Integer.parseInt(parts[1], 10);
            if (smallest == null || total < smallest_size)
            {
                smallest_size = total;
                smallest = s;
            }
        }

        return smallest;
    }

    @Override
    public void onSizeListReceived(CameraThread ct, String[] sizes) {
        ct.request_beginvideo(findSmallestSize(sizes));
    }

    @Override
    public void onFrameReceived(CameraThread ct, byte[] frame) {
        frame2image(frame, mIntArray, mCameraInfo);

        mIntBuffer.rewind();
        mRemoteBitmap.copyPixelsFromBuffer(mIntBuffer);

        final Activity activity = getActivity();
        if (activity != null)
        {
            activity.runOnUiThread(mInvalidater);
            // @todo maybe limit fps here?
            ct.request_pic();
        }
    }

    /**
     * This could be used to call different conversion functions... maybe
     *
     * @param src
     * @param dst
     * @param info
     */
    static void frame2image(final byte[] src, int[] dst, CameraInfo info) {
        nv21ToARGB(src, dst, info.width, info.height);
    }


    /**
     * Converts an nv21 byte array to a int array as expected by Bitmap.Config.ARGB_8888
     *
     * @param src    nv21 format source array
     * @param dst    destination array
     * @param width  image width
     * @param height image height
     */
    private static void nv21ToARGB(final byte[] src,
                                   final int[] dst,
                                   final int width,
                                   final int height) {
        final int size = width * height;

        for (int yi = 0, uvi = size; yi < size; uvi += 2)
        {
            final int v = (src[uvi] & 0xff) - 128;
            final int u = (src[uvi + 1] & 0xff) - 128;

            final int rval = (int) (1.402f * v);
            final int gval = (int) (0.344f * u + 0.714f * v);
            final int bval = (int) (1.772f * u);

            dst[yi] = y2argb(src[yi] & 0xff, rval, gval, bval);
            dst[yi + 1] = y2argb(src[yi + 1] & 0xff, rval, gval, bval);
            dst[width + yi] = y2argb(src[width + yi] & 0xff, rval, gval, bval);
            dst[width + yi + 1] = y2argb(src[width + yi + 1] & 0xff, rval, gval, bval);

            yi += 2;
            if ((yi % width) == 0)
                yi += width;
        }
    }

    /**
     * Converts Y value (from yuv) to ARGB using precomputed uv values.
     *
     * @param y    Y value from yuv
     * @param rval precomputed value for red
     * @param gval precomputed value for green
     * @param bval precomputed value for blue
     * @return integer value expected by Bitmap.Config.ARGB_8888
     */
    private static int y2argb(final int y,
                              final int rval,
                              final int gval,
                              final int bval) {
        int r = y + rval;
        int g = y - gval;
        int b = y + bval;

        r = r > 255 ? 255 : r < 0 ? 0 : r;
        g = g > 255 ? 255 : g < 0 ? 0 : g;
        b = b > 255 ? 255 : b < 0 ? 0 : b;

        return 0xff000000 | (b << 16) | (g << 8) | r;
    }
}
