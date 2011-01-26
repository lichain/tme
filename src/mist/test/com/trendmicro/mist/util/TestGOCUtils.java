package com.trendmicro.mist.util;

import static org.junit.Assert.*;

import java.net.URI;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
//import org.junit.rules.TestName;

import com.google.protobuf.ByteString;
import com.trendmicro.codi.ZKSessionManager;
import com.trendmicro.codi.ZNode;
import com.trendmicro.mist.proto.MistMessage;
import com.trendmicro.mist.proto.MistMessage.MessageBlock;
import com.trendmicro.spn.proto.SpnMessage.Container;
import com.trendmicro.spn.proto.SpnMessage.ContainerBase;
import com.trendmicro.spn.proto.SpnMessage.Message;
import com.trendmicro.spn.proto.SpnMessage.MessageBase;
import com.trendmicro.spn.proto.SpnMessage.MessageList;

public class TestGOCUtils {
	private static String graphRoot = "/tme2/global/goc_server";
	private static ZNode goc_node = null;
	private static ZKTestServer zkTestServer = null;
	private static GOCTestServer gocTestServer = null;
	private GOCUtils gocUtil = null;

	@BeforeClass
	public static void initialize() throws Exception {
        // start goc server
        gocTestServer = new GOCTestServer(8000);

		// * start ZK
		zkTestServer = new ZKTestServer(33667);
        zkTestServer.start();
        ZKSessionManager.initialize("localhost:33667", 8000);
        ZNode goc_node = new ZNode(graphRoot);
        goc_node.create(false, "http://localhost:8000/*".getBytes());
	}

	@AfterClass
	public static void cleanup() throws Exception {
		if (goc_node != null)
			goc_node.deleteRecursively();

		ZKSessionManager.uninitialize();
		zkTestServer.stop();
		zkTestServer = null;

		if (gocTestServer != null)
			gocTestServer.close();
	}

	@Before
	public void starTest() {
		gocUtil = new GOCUtils();
		assertNotNull(gocUtil);
	}

	@After
	public void stopTest() {
		gocUtil = null;
		System.gc();
	}

	@Test(timeout = 10000)
	public void testGet() {
		try {
			URI uri = new URI("http://localhost:8000/*");
			String loc = gocUtil.post(uri, "Test".getBytes(), 1000);
			assertNotNull(loc);

			byte[] data = gocUtil.get(new URI(loc));
			assertNotNull(data);

			assertTrue(new String(data).equals("Test"));
		}
		catch (Exception e) {
			assertTrue(false);
		}
	}

	@Test(timeout = 10000)
	public void testPost() {
		try {
			URI uri = new URI("http://localhost:8000/*");
			String loc = gocUtil.post(uri, "Test".getBytes(), 1000);
			assertNotNull(loc);
		}
		catch (Exception e) {
			assertTrue(false);
		}
	}

	@Test(timeout = 10000)
	public void testGOCPack() {
		String message = "This is goc test case, and it will test GOCPack, GOCUnPack, get, post, and so on.@#$$%%%^%^^&^*";

		byte[] msg_packed = gocUtil.GOCPack(null, 900);
		assertNull(msg_packed);

		msg_packed = gocUtil.GOCPack(null, -100);
		assertNull(msg_packed);

		MessageBlock msgblk = createMsgBlock(message);
		byte [] msg_ref = gocUtil.GOCPack(msgblk.toByteArray(), 0);
		assertNotNull(msg_ref);
	}

	@Test(timeout = 10000)
	public void testGOCUnPack() {
		String message = "This is goc test case, and it will test GOCPack, GOCUnPack, get, post, and so on.@#$$%%%^%^^&^*";

		byte[] msg_packed = gocUtil.GOCPack(null, 900);
		assertNull(msg_packed);

		MessageBlock msgblk = createMsgBlock(message);

		byte [] msg_ref = gocUtil.GOCPack(msgblk.toByteArray(), 900);
		assertNotNull(msg_ref);

		byte [] unpacked_msg = gocUtil.GOCUnPack(msg_ref);
		assertNotNull(unpacked_msg);

		String m = parseMsgBlock(unpacked_msg);
		assertTrue(m.equals(message));
	}

	private MessageBlock createMsgBlock(String message) {
		try {

			MessageBase.Builder mbase_builder = MessageBase.newBuilder();
	        mbase_builder.setSubject(ByteString.copyFrom("".getBytes()));

	        Message.Builder msg_builder = Message.newBuilder();
	        msg_builder.setMsgBase(mbase_builder.build());
	        msg_builder.setDerived(ByteString.copyFrom(message.getBytes("UTF-16")));

	        MessageList.Builder mlist_builder = MessageList.newBuilder();
	        mlist_builder.addMessages(msg_builder.build());

	        ContainerBase.Builder cbase_builder = ContainerBase.newBuilder();
	        cbase_builder.setMessageList(mlist_builder.build());

	        Container.Builder cont_builder = Container.newBuilder();
	        cont_builder.setContainerBase(cbase_builder.build());

	        MistMessage.MessageBlock.Builder mblock_builder = MistMessage.MessageBlock.newBuilder();
	        mblock_builder.setId("");
	        mblock_builder.setMessage(ByteString.copyFrom(cont_builder.build().toByteArray()));
	        return mblock_builder.build();
		}
		catch (Exception e) {
			return null;
		}
	}

	private String parseMsgBlock(byte[] payload) {
		String line = null;
        try {
            MistMessage.MessageBlock.Builder mblock_builder = MistMessage.MessageBlock.newBuilder();
            mblock_builder.mergeFrom(payload);
            MistMessage.MessageBlock msg_block = mblock_builder.build();
            Container.Builder cont_builder = Container.newBuilder();
            cont_builder.mergeFrom(msg_block.getMessage().toByteArray());
            Container cont = cont_builder.build();
            line = new String(cont.getContainerBase().getMessageList().getMessages(0).getDerived().toByteArray(), "UTF-16");
        }
        catch(Exception e) {
        }

        return line;
	}

	public static void main(String args[]) {
	      org.junit.runner.JUnitCore.main(TestGOCUtils.class.getName());
    }
}
