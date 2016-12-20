package com.aaronps.aremocam.viewer;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.net.Socket;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link OnConnectFragmentInteractionListener} interface
 * to handle interaction events.
 */
public class ConnectFragment extends Fragment {
    private OnConnectFragmentInteractionListener mListener;
    private EditText                             mIpAddress;
    private EditText                             mPort;
    private Button                               mConnectButton;

    public ConnectFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("ConnectFragment", "onCreate");
//        if (getArguments() != null)
//        {
//            mParam1 = getArguments().getString(ARG_PARAM1);
//            mParam2 = getArguments().getString(ARG_PARAM2);
//        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connect, container, false);

        mIpAddress = (EditText) view.findViewById(R.id.connect_ip);
        mPort = (EditText) view.findViewById(R.id.connect_port);

        mPort.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL)
                {
//                    Log.d(ConnectFragment.class.getName(), "EditorAction " + actionId);
                    tryConnect();
                    return true;
                }
                return false;
            }
        });

        mConnectButton = (Button) view.findViewById(R.id.connect_button);
        mConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tryConnect();
            }
        });

        Log.d("ConnectFragment", "onCreateView");

        return view;
    }

    void tryConnect() {
        mConnectButton.setEnabled(false);
        boolean reenable_button = true;
        try
        {
            final String ipaddress = mIpAddress.getText().toString();
            final int    port      = Integer.parseInt(mPort.getText().toString(), 10);

            Log.d(ConnectFragment.class.getName(), "Trying to connect to: " + ipaddress + ":" + port);

            mListener.onConnectParameters(ipaddress, port);

            reenable_button = false;
        }
        finally
        {
            if (reenable_button)
            {
                mConnectButton.setEnabled(true);
            }
        }


    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnConnectFragmentInteractionListener)
        {
            Log.d("ConnectFragment", "Attached");
            mListener = (OnConnectFragmentInteractionListener) context;
        }
        else
        {
            throw new RuntimeException(context.toString()
                                               + " must implement OnConnectFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d("ConnectFragment", "Detached");
        mListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnConnectFragmentInteractionListener {
        void onConnectParameters(String host, int port);
    }
}
