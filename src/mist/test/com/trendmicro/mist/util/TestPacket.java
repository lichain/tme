package com.trendmicro.mist.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.trendmicro.mist.Daemon;

public class TestPacket extends TestCase {
    BufferedInputStream in;
    BufferedOutputStream out;
    PipedInputStream pis;
    PipedOutputStream pos;

    class Writer extends Thread {
        byte[] payload;
        boolean partial;

        public Writer(byte[] payload, boolean partial) {
            this.payload = payload;
            this.partial = partial;
        }

        @Override
        public void run() {
            try {
                if(partial) {
                    out.write(payload, 0, payload.length - 1);
                    out.close();
                }
                else
                    out.write(payload);
            }
            catch(IOException e) {
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        pos = new PipedOutputStream();
        pis = new PipedInputStream(pos);
        out = new BufferedOutputStream(pos);
        in = new BufferedInputStream(pis);

        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        in.close();
        out.close();
        pis.close();
        pos.close();

        super.tearDown();
    }

    public void testWriteSize() throws IOException {
        /**
         * Test write a valid size
         */
        int size = 10;
        Packet.writeSize(out, size);
        assertEquals(size, new DataInputStream(pis).readInt());
    }

    public void testReadSize() throws IOException {
        /**
         * Read a valid size
         */
        int size = 10;
        new DataOutputStream(out).writeInt(size);
        out.flush();
        assertEquals(size, Packet.readSize(in));

        /**
         * Read an incomplete size
         */
        new DataOutputStream(out).write(0);
        out.close();
        assertEquals(-1, Packet.readSize(in));
    }

    public void testGetAndSetPayload() {
        /**
         * Test set and get payload
         */
        Packet packet = new Packet();
        byte[] msg = new byte[10];
        packet.setPayload(msg);
        assertSame(msg, packet.getPayload());
    }

    public void testRead() throws IOException, InterruptedException {
        Packet packet = new Packet();

        /**
         * Test read a normal packet
         */
        String testMsg = "test";
        new DataOutputStream(out).writeInt(testMsg.length());
        out.write(testMsg.getBytes());
        out.flush();
        assertEquals(testMsg.length(), packet.read(in));
        assertEquals(testMsg, new String(packet.getPayload()));

        /**
         * Test read a too large size packet and skip
         */
        byte[] largeMsg = new byte[Daemon.MAX_MESSAGE_SIZE + 1];
        new DataOutputStream(out).writeInt(largeMsg.length);
        Writer writer = new Writer(largeMsg, false);
        writer.start();
        assertEquals(0, packet.read(in));
        writer.join();

        /**
         * Test read a normal packet again
         */
        testMsg = "test again";
        new DataOutputStream(out).writeInt(testMsg.length());
        out.write(testMsg.getBytes());
        out.flush();
        assertEquals(testMsg.length(), packet.read(in));
        assertEquals(testMsg, new String(packet.getPayload()));

        /**
         * Test read a negative packet size
         */
        new DataOutputStream(out).writeInt(-10);
        out.flush();
        assertEquals(-1, packet.read(in));

        /**
         * Test read a incomplete packet
         */
        largeMsg = new byte[10];
        new DataOutputStream(out).writeInt(largeMsg.length);
        writer = new Writer(largeMsg, true);
        writer.start();
        assertEquals(-1, packet.read(in));
        writer.join();
    }

    public void testWrite() throws IOException {
        Packet packet = new Packet();

        /**
         * Test write a normal message
         */
        String testMsg = "test message";
        packet.setPayload(testMsg.getBytes());
        packet.write(out);
        int len = new DataInputStream(pis).readInt();
        assertEquals(testMsg.length(), len);
        byte[] payload = new byte[len];
        pis.read(payload, 0, len);
        assertEquals(testMsg, new String(payload));

        /**
         * Test set partial payload
         */
        packet.setPayload(testMsg.getBytes(), 4);
        packet.write(out);
        len = new DataInputStream(pis).readInt();
        assertEquals(4, len);
        payload = new byte[len];
        pis.read(payload, 0, len);
        assertEquals("test", new String(payload));
    }

    public void testReadWrite() throws IOException {
        Packet packet = new Packet();

        /**
         * Test use packet to send and receive a message
         */
        String testMsg = "test message";
        packet.setPayload(testMsg.getBytes());
        packet.write(out);
        packet.read(in);
        assertEquals(testMsg, new String(packet.getPayload()));

        /**
         * Test send partial payload
         */
        packet.setPayload(testMsg.getBytes(), 4);
        packet.write(out);
        packet.read(in);
        assertEquals("test", new String(packet.getPayload()));
    }

    public static Test suite() {
        return new TestSuite(TestPacket.class);
    }
}
