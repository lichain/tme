package com.trendmicro.mist.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

import com.trendmicro.mist.Daemon;

/**
 * A helper class to read and write MIST packet format [length][payload]<br>
 * Note that it is NOT thread-safe
 */
public class Packet {
    private byte[] payload;
    private int payloadLength;

    private static boolean isValidSize(int length) {
        return(length >= 0 && length <= Daemon.MAX_MESSAGE_SIZE);
    }

    private static int readExact(BufferedInputStream in, byte[] buffer, int length) throws IOException {
        int cnt = 0, offset = 0;
        try {
            do {
                int rd = in.read(buffer, offset, length - cnt);
                if(rd == -1)
                    return -1;
                cnt += rd;
                offset += rd;
            } while(cnt < length);
        }
        catch(IOException e) {
            throw e;
        }
        return cnt;
    }

    private int readPayload(BufferedInputStream in, int length) throws Throwable {
        int rdcnt = 0;
        try {
            payload = new byte[length];
            if((rdcnt = readExact(in, payload, length)) == -1)
                return -1;
        }
        catch(Throwable e) {
            throw e;
        }
        payloadLength = payload.length;
        return rdcnt;
    }

    private void writePayload(BufferedOutputStream out) throws IOException {
        try {
            out.write(payload, 0, payloadLength);
            out.flush();
        }
        catch(IOException e) {
            throw e;
        }
    }

    // //////////////////////////////////////////////////////////////////////////////

    public Packet() {
    }

    /**
     * Read an 4-byte big-endian integer from inputstream in. Note that it does
     * not do a valid size check
     * 
     * @param in
     *            The input stream to read data from
     * @return An integer if success<br>
     *         -1 if the it fails to read 4 bytes
     * @throws IOException
     */
    public static int readSize(BufferedInputStream in) throws IOException {
        try {
            return new DataInputStream(in).readInt();
        }
        catch(EOFException e) {
            return -1;
        }
        catch(IOException e) {
            throw e;
        }
    }

    /**
     * Writes a four byte big-endian integer with the value "size" to an output
     * stream
     * 
     * @param out
     *            The output stream to write to
     * @param size
     *            The size integer to write to the output stream
     * @throws IOException
     *             Any write error encountered
     */
    public static void writeSize(BufferedOutputStream out, int size) throws IOException {
        try {
            new DataOutputStream(out).writeInt(size);
            out.flush();
        }
        catch(IOException e) {
            throw e;
        }
    }

    /**
     * Read a packet from input stream, and the payload will be stuffed into
     * this.payload
     * 
     * @param in
     *            The input stream
     * @return the number of bytes read, 0 if the packet is too large and
     *         skipped, or -1 for EOF or negative size
     * @throws IOException
     *             Any read error encountered
     */
    public int read(BufferedInputStream in) throws IOException {
        int length = -1;
        try {
            length = readSize(in);
            if(length < 0)
                return -1;
            else if(!isValidSize(length)) {
                readPayload(in, length);
                return 0;
            }
            else {
                if(readPayload(in, length) < length)
                    return -1;
            }
        }
        catch(Throwable e) {
            throw new IOException(e.getMessage());
        }
        return length;
    }

    /**
     * Write the previously set payload to output stream
     * 
     * @param out
     *            The output stream to write to
     * @throws IOException
     *             Any network error encountered
     */
    public void write(BufferedOutputStream out) throws IOException {
        try {
            writeSize(out, payloadLength);
            writePayload(out);
        }
        catch(IOException e) {
            throw e;
        }
    }

    /**
     * Get the payload read previously
     * 
     * @return A byte[] reference to the payload
     */
    public byte[] getPayload() {
        return payload;
    }

    /**
     * Set the payload to point to the buffer
     * 
     * @param buffer
     *            The buffer to be sent
     */
    public void setPayload(byte[] buffer) {
        setPayload(buffer, buffer.length);
    }

    /**
     * Set partial payload to write, note that payload still points to the
     * original buffer, but when the write operation is invoked, only partial
     * payload (in length size) will be written out
     * 
     * @param buffer
     *            Input buffer
     * @param length
     *            The length of the payload to be written out
     */
    public void setPayload(byte[] buffer, int length) {
        payload = buffer;
        payloadLength = length;
    }
}
