package com.aaronps.aremocam.viewer;

/**
 * Created by krom on 4/27/2016.
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @todo use NIO maybe?
 * @todo reuse buffers for pics?
 * @todo fps limiter?
 *
 * @author krom
 */
public class CameraThread implements Runnable
{
    private static final long LOOP_RESTART_MS = 5000;

    public interface Listener
    {
        void onConnected(final CameraThread ct);
        void onDisconnected(final CameraThread ct);
        void onVideoReady(final CameraThread ct, final CameraInfo info);
        void onSizeListReceived(final CameraThread ct, final String[] sizes);
        void onFrameReceived(final CameraThread ct, final byte[] frame);
    }

    private static final Logger logger = Logger.getLogger("CameraThread");

    private static final byte[] Request_Pic = "Pic\n".getBytes();
    private static final byte[] Request_SizeList = "SizeList\n".getBytes();
//    private static final byte[] Request_BeginVideo = "Begin\n".getBytes();

    private volatile Thread mThread = null;
    private final Listener mListener;
    private final String mHost;
    private final int mPort;

    private Socket mSocket;
    private OutputStream mSocketOutputStream;

    public CameraThread(final Listener listener, final String host, final int port)
    {
        this.mListener = listener;
        this.mHost = host;
        this.mPort = port;
    }

    public CameraThread(final Listener listener, final Socket socket)
    {
        this.mListener = listener;
        this.mHost = null;
        this.mPort = -1;
        this.mSocket = socket;
    }

    public synchronized void start()
    {
        if ( mThread == null )
        {
            logger.info("Thread start");
            mThread = new Thread(this);
            mThread.start();
        }
    }

    public synchronized void stop() throws InterruptedException
    {
        if ( mThread != null )
        {
            logger.info("Thread stop");
            final Thread t = mThread;
            mThread = null;
            try { mSocket.close(); } catch ( Exception ignored) {}
            t.interrupt();
            t.join();
        }

    }

    final public void request_pic()
    {
        try
        {
            mSocketOutputStream.write(Request_Pic);
            mSocketOutputStream.flush();
        }
        catch (IOException ex)
        {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    final public void request_sizelist()
    {
        try
        {
            mSocketOutputStream.write(Request_SizeList);
            mSocketOutputStream.flush();
        }
        catch (IOException ex)
        {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    final public void request_beginvideo(final String size)
    {
        try
        {
            mSocketOutputStream.write(("BeginVideo " + size + "\n").getBytes());
            mSocketOutputStream.flush();
        }
        catch (IOException ex)
        {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    final public void request_stopvideo()
    {
        try
        {
            mSocketOutputStream.write(("StopVideo\n").getBytes());
            mSocketOutputStream.flush();
        }
        catch (IOException ex)
        {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run()
    {
        logger.info("run begin");
        final ByteArrayOutputStream inputLine = new ByteArrayOutputStream(128);
        long lastLoopTime = 0;

        while ( mThread != null )
        {
            // check for exit condition in case it is a preconnected socket
            if ( mSocket == null && mHost == null ) break;

            final long msSinceLastLoop = System.currentTimeMillis() - lastLoopTime;
            if (msSinceLastLoop < LOOP_RESTART_MS)
                try
                {
                    Thread.sleep(LOOP_RESTART_MS - msSinceLastLoop);
                }
                catch (InterruptedException e)
                {
                    continue; // so it checks for exit condition
                }

            lastLoopTime += msSinceLastLoop; // as to avoid getting the time again...

            Socket socket = null;
            InputStream input = null;

            try
            {
                if ( mSocket == null )
                {
                    logger.info("Connecting...");
                    socket = new Socket(mHost, mPort);
                    mSocket = socket;
                    logger.info("Connected");
                }
                else
                {
                    socket = mSocket;
                }

                input = socket.getInputStream();
                mSocketOutputStream = socket.getOutputStream();

                // Only notify if we connect ourselves, mHost != null when we connect ourselves.
                if ( mHost != null )
                {
                    mListener.onConnected(this);
                }
            }
            catch (IOException e)
            {
                logger.log(Level.SEVERE, "Error connecting", e);
                if (input != null) try{ input.close(); } catch (IOException ignored) {}
                if (mSocketOutputStream != null) try { mSocketOutputStream.close(); } catch (IOException ignored) {}
                if (socket != null) try { socket.close(); } catch (IOException ignored) { }

                mSocket = null;
                mSocketOutputStream = null;
                continue;
            }

            try
            {
                readLine(input, inputLine);

                String line = inputLine.toString();
                while ( ! line.isEmpty() )
                {
                    final String[] parts = line.split(" ");
                    switch (parts[0])
                    {
                        case "Ready":
                        {
                            final CameraInfo info = new CameraInfo();
                            info.width = Integer.parseInt(parts[1]);
                            info.height = Integer.parseInt(parts[2]);
                            info.type = Integer.parseInt(parts[3]);

                            mListener.onVideoReady(this, info);
                            break;
                        }
                        case "Pic":
                        {
                            final int len = Integer.parseInt(parts[1]);
                            if ( len > 0 )
                            {
                                final byte[] frame_buffer = new byte[len];
                                readBuffer(input, frame_buffer, len);
                                mListener.onFrameReceived(this, frame_buffer);
                            }
                            break;
                        }
                        case "SizeList":
                        {
                            mListener.onSizeListReceived(this, Arrays.copyOfRange(parts, 1, parts.length));
                            break;
                        }
                        default:
                        {
                            logger.info("Unknown message from camera: [" + line + "]" );
                        }

                    }

                    readLine(input, inputLine);
                    line = inputLine.toString();
                }
            }
            catch (IOException ex)
            {
                logger.log(Level.SEVERE, null, ex);
            }
            finally
            {
                try { input.close(); } catch (IOException ignored) { }
                try { mSocketOutputStream.close(); } catch (IOException ignored) { }
                try { socket.close(); } catch (IOException ignored) { }

                mSocket = null;
                mSocketOutputStream = null;
            }
//            catch (InterruptedException ex)
//            {
//                logger.log(Level.INFO, "Interrupted", ex);
//            }
            logger.info("Loop end");

        }
        logger.info("run end");
    }

    private static void readLine(final InputStream input, final ByteArrayOutputStream line) throws IOException
    {
        line.reset();
        int c;
        while ( (c = input.read()) != -1 )
        {
            if ( c == '\n' ) break;
            line.write(c);
        }
    }

    private static void readBuffer(final InputStream input, final byte[] buffer, final int length) throws IOException
    {
        int pos = 0;
        do
        {
            final int readed = input.read(buffer, pos, length - pos);
            if ( readed == -1 ) break;
            pos += readed;
        } while ( pos < length );
    }

}