package com.aaronps.aremocam.viewer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * OutputStream targetting a ByteBuffer. This ByteBuffer is provided by user and will never grow.
 * If the buffer fills, an exception will be thrown.
 * Created by krom on 12/20/16.
 */
public final class ByteBufferOutputStream extends OutputStream {

    private final ByteBuffer mByteBuffer;
    private final byte[] mNumberBuffer = new byte[32];

    public ByteBufferOutputStream(final ByteBuffer byteBuffer) {
        mByteBuffer = byteBuffer;
    }

    public ByteBuffer getByteBuffer() {
        return mByteBuffer;
    }

    public void reset() {
        mByteBuffer.position(0);
        mByteBuffer.limit(mByteBuffer.capacity());
    }

    public void writeInt(int i) throws IOException {
        final byte[] digits = mNumberBuffer;
        int pos = digits.length;
        do
        {
            digits[--pos] = (byte)((i % 10) + 0x30);
            i /= 10;
        } while ( i > 0 );

        write(digits, pos, digits.length - pos);
    }
    
    public void write(ByteBuffer bb) throws IOException {
        // @todo this doesn't throw IOException, do manually
        mByteBuffer.put(bb);
    }
    
    public void write(String s) throws IOException {
        write(s.getBytes());
    }

    @Override
    public void write(int i) throws IOException {
        if ( mByteBuffer.hasRemaining() )
        {
            mByteBuffer.put((byte)i);
        }
        else
        {
            throw new IOException("Buffer full");
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        final int remaining = mByteBuffer.remaining();

        if ( len <= remaining )
        {
            mByteBuffer.put(b, off, len);
        }
        else
        {
            if (remaining > 0)
            {
                mByteBuffer.put(b, off, remaining);
            }

            throw new IOException("Buffer full");
        }
    }

    @Override
    public void close() throws IOException {
        mByteBuffer.position(mByteBuffer.capacity());
    }

    @Override
    public void flush() throws IOException {
        // this is a noop
    }
}
