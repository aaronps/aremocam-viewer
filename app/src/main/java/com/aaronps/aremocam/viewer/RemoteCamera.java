package com.aaronps.aremocam.viewer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author krom
 */
public final class RemoteCamera implements Runnable {

    private static final Logger logger = Logger.getLogger("RemoteCamera");

    static class Protocol {

        public static final byte[] REQ_PIC = "Pic\n".getBytes();
        public static final byte[] REQ_SIZELIST = "SizeList\n".getBytes();
        public static final byte[] REQ_BEGINVIDEO = "BeginVideo ".getBytes();
        public static final byte[] REQ_STOPVIDEO = "StopVideo\n".getBytes();

        public static final byte[] MSG_READY = "Ready".getBytes();
        public static final byte[] MSG_PIC = "Pic".getBytes();
        public static final byte[] MSG_SIZELIST = "SizeList".getBytes();
    }

    public interface Listener {

        void onConnected(final RemoteCamera ct);

        void onDisconnected(final RemoteCamera ct);

        void onVideoReady(final RemoteCamera ct, final CameraInfo info);

        void onSizeListReceived(final RemoteCamera ct, final String[] sizes);

        void onFrameReceived(final RemoteCamera ct, final ByteBuffer frame);
    }

    private final Listener mListener;

    // buffer sizes... if receiving pics, it might need a big buffer to hold the full pic
    private static final int BUFFER_SIZE = 1 * 1024 * 1024;

    private final SocketAddress mSocketAddress;
    private OutputStream mOutputStream;
    private final ByteBufferOutputStream mSendBuffer;

    public RemoteCamera(Listener listener, final String host, final int port) {
        mListener = listener;
        mSocketAddress = new InetSocketAddress(host, port);
        mSendBuffer = new ByteBufferOutputStream(ByteBuffer.allocate(256));
    }

    public void request_pic() throws IOException {
        mOutputStream.write(Protocol.REQ_PIC);
        mOutputStream.flush();
    }

    public void request_sizelist() throws IOException {
        logger.info("Request SizeList");
        mOutputStream.write(Protocol.REQ_SIZELIST);
        mOutputStream.flush();
    }

    public void request_beginvideo(final String size) throws IOException {
        logger.info("Request BeginVideo");

        mSendBuffer.reset();
        mSendBuffer.write(Protocol.REQ_BEGINVIDEO);
        mSendBuffer.write(size);
        mSendBuffer.write(10);

        final ByteBuffer bb = mSendBuffer.getByteBuffer();
        mOutputStream.write(bb.array(), 0, bb.position());
        mOutputStream.flush();
    }

    public void request_stopvideo() throws IOException {
        logger.info("Request StopVideo");
        mOutputStream.write(Protocol.REQ_STOPVIDEO);
        mOutputStream.flush();
    }

    @Override
    public void run() {
        final Thread thread = Thread.currentThread();
        final SimpleLoopBarrier loopBarrier = new SimpleLoopBarrier(5000);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        SocketChannel socketChannel = null;

        Exception lastException = null;

        try
        {
            logger.info("Start");
            while (!thread.isInterrupted())
            {
                try
                {
                    loopBarrier.check();

                    logger.log(Level.INFO, "Connecting to [{0}]", mSocketAddress.toString());
                    socketChannel = SocketChannel.open(mSocketAddress);
                    mOutputStream = Channels.newOutputStream(socketChannel); // out of here use this...

                    logger.log(Level.INFO, "Connected to [{0}]", mSocketAddress.toString());

                    mListener.onConnected(this);

                    handleConnection(socketChannel, byteBuffer);

                    logger.log(Level.INFO, "Disconnected from [{0}]", mSocketAddress.toString());
                }
                catch (IOException ex)
                {
                    if (lastException != null && isSameException(ex, lastException))
                    {
                        logger.log(Level.SEVERE, "Repeat: {0}", ex.toString());
                    }
                    else
                    {
                        lastException = ex;
                        logger.log(Level.SEVERE, ex.toString(), ex);
                    }
                }
                finally
                {
                    if (socketChannel != null)
                    {
                        try
                        {
                            // @note that mOutputStream is not nulled this would
                            // allow to give more meaningfull errors in case is
                            // written before reopened
                            socketChannel.close();
                        }
                        catch (IOException ex)
                        {
                            // YESSSS this one time can be ignored!!!!
                        }
                        socketChannel = null;
                        mListener.onDisconnected(this);
                    }
                }
            }
        }
        catch (InterruptedException ex)
        {
            thread.interrupt();
            logger.log(Level.SEVERE, "Thread was interrupted", ex);
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, "Weird exception", e);
        }
        finally
        {
            logger.info("End");
        }
    }

    private static boolean isSameException(Exception a, Exception b) {
        return a.getClass() == b.getClass()
                && Arrays.deepEquals(a.getStackTrace(), b.getStackTrace());
    }

    void handleConnection(final SocketChannel socketChannel,
                          final ByteBuffer byteBuffer) throws IOException {
        int linePos = 0;
        byteBuffer.clear();
        final byte[] byteArray = byteBuffer.array();

        do
        {
            if (byteBuffer.position() == linePos)
            {
                if (socketChannel.read(byteBuffer) <= 0)
                {
                    // didn't read, probably was closed.
                    return;
                }
            }

            if (byteArray[linePos] == 10)
            {
                byteBuffer.limit(byteBuffer.position());
                byteBuffer.position(linePos + 1); // +1 is the new line byte

                handleMessage(socketChannel, byteBuffer, linePos);

                byteBuffer.compact();
                linePos = -1;
            }

        } while (++linePos < byteBuffer.capacity());

    }

    void handleMessage(final SocketChannel socketChannel,
                       final ByteBuffer byteBuffer,
                       final int pos) throws IOException {
        final byte[] buffer = byteBuffer.array();
        final String msg = new String(buffer, 0, pos);
        final String[] parts = msg.split(" ");

        switch (parts[0])
        {
            case "Pic":
            {
                final int len = Integer.parseInt(parts[1]);
                if (len > 0)
                {
                    if (byteBuffer.remaining() < len)
                    {
                        byteBuffer.compact();
                        do
                        {
                            if (socketChannel.read(byteBuffer) <= 0)
                            {
                                throw new IOException("Buffer full or server closed connection");
                            }
                        } while (byteBuffer.position() < len);

                        byteBuffer.flip();
                    }

                    final int olimit = byteBuffer.limit();
                    final int newlimit = byteBuffer.position() + len;
                    byteBuffer.limit(newlimit);

                    mListener.onFrameReceived(this, byteBuffer);

                    byteBuffer.limit(olimit);
                    byteBuffer.position(newlimit);
                }
                break;
            }
            case "Ready":
            {
                final CameraInfo info = new CameraInfo();
                info.width = Integer.parseInt(parts[1]);
                info.height = Integer.parseInt(parts[2]);
                info.type = Integer.parseInt(parts[3]);

                mListener.onVideoReady(this, info);
                break;
            }
            case "SizeList":
            {
                mListener.onSizeListReceived(this, Arrays.copyOfRange(parts, 1, parts.length));
                break;
            }
            default:
            {
                logger.log(Level.INFO, "Unknown message from camera: [{0}]", msg);
            }
        }

        // I set them by estimated calling frequency.
//        if (startsWidth(buffer, pos, Protocol.MSG_PIC))
//        {
//            logger.info("Got a pic");
//        }
//        else if (startsWidth(buffer, pos, Protocol.MSG_READY))
//        {
//            logger.info("Got ready");
//
//        }
//        else if (startsWidth(buffer, pos, Protocol.MSG_SIZELIST))
//        {
//            logger.info("Got sizelist: ");
//        }
    }

//    private static boolean startsWidth(final byte[] buffer,
//                                       final int pos,
//                                       final byte[] head) {
//        if (pos != head.length)
//        {
//            return false;
//        }
//
//        for (int i = 0; i < pos; ++i)
//        {
//            if (buffer[i] != head[i])
//            {
//                return false;
//            }
//        }
//
//        return true;
//    }
}
