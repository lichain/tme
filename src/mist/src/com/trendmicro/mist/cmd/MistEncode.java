package com.trendmicro.mist.cmd;

import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.trendmicro.mist.util.Packet;
import com.trendmicro.mist.proto.MistMessage;
import com.trendmicro.mist.proto.MistMessage.KeyValuePair;
import com.trendmicro.spn.proto.SpnMessage.Container;
import com.trendmicro.spn.proto.SpnMessage.ContainerBase;
import com.trendmicro.spn.proto.SpnMessage.Message;
import com.trendmicro.spn.proto.SpnMessage.MessageBase;
import com.trendmicro.spn.proto.SpnMessage.MessageList;

import com.google.protobuf.ByteString;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

public class MistEncode {
    enum CmdType {
        NONE, LINE, STREAM, FILE, 
    }

    private CmdType currCmd = CmdType.NONE;
    private String messageId = "";
    private String messageFile;
    private int messageTTL = -1;
    private HashMap<String, String> messageProperties = new HashMap<String, String>(); 
    private boolean hasProperties = false;

    private void printUsage() {
        System.out.printf("Usage:%n");
        System.out.printf("      mist-encode [options [arguments...] ]... %n%n");
        System.out.printf("Options: %n");
        System.out.printf("  --line, -l %n");
        System.out.printf("        encode each text line as a message %n%n");
        System.out.printf("    --wrap=MESSAGEID, -w MESSAGEID %n");
        System.out.printf("        wrap as message block of MESSAGEID %n");
        System.out.printf("        MESSAGEID={queue|topic}:EXCHANGENAME %n");
        System.out.printf("        if exchange type prefix is not given, default to queue %n%n");
        System.out.printf("    --property=NAME:VALUE, -p NAME:VALUE %n");
        System.out.printf("        set properties in MessageBlock %n%n");
        System.out.printf("    --ttl=SECONDS, -t SECONDS %n");
        System.out.printf("        specify the message TTL %n%n");
        System.out.printf("  --file=FILENAME, -f FILENAME %n");
        System.out.printf("        encode the given FILENAME as a message %n%n");
        System.out.printf("  --stream, -s %n");
        System.out.printf("        pack the input length-data stream with MessageBlock structure %n%n");
        System.out.printf("  --help, -h %n");
        System.out.printf("        display help messages %n%n");
    }
    
    private MistMessage.MessageBlock packMessage(byte [] messageContent) {
        MessageBase.Builder mbase_builder = MessageBase.newBuilder();
        mbase_builder.setSubject(ByteString.copyFrom("".getBytes()));
        
        Message.Builder msg_builder = Message.newBuilder();
        msg_builder.setMsgBase(mbase_builder.build());
        msg_builder.setDerived(ByteString.copyFrom(messageContent));
        
        MessageList.Builder mlist_builder = MessageList.newBuilder();
        mlist_builder.addMessages(msg_builder.build());
        
        ContainerBase.Builder cbase_builder = ContainerBase.newBuilder();
        cbase_builder.setMessageList(mlist_builder.build());
        
        Container.Builder cont_builder = Container.newBuilder();
        cont_builder.setContainerBase(cbase_builder.build());
        
        MistMessage.MessageBlock.Builder mblock_builder = MistMessage.MessageBlock.newBuilder();
        if(hasProperties) {
            Iterator<Map.Entry<String, String>> iter = messageProperties.entrySet().iterator();
            while(iter.hasNext()) {
                Map.Entry<String, String> prop = iter.next();
                mblock_builder.addProperties(KeyValuePair.newBuilder().setKey(prop.getKey()).setValue(prop.getValue()).build());
            }
        }
        mblock_builder.setId(messageId);
        mblock_builder.setMessage(ByteString.copyFrom(cont_builder.build().toByteArray()));
        if(messageTTL != -1)
            mblock_builder.setTtl(messageTTL);
        return mblock_builder.build();
    }

    private void processLine() {
        try {
            BufferedReader src = new BufferedReader(new InputStreamReader(System.in));
            BufferedOutputStream dest = new BufferedOutputStream(System.out);
            Packet pack = new Packet();
            String line;
            while((line = src.readLine()) != null) {
                pack.setPayload(packMessage(line.getBytes("UTF-8")).toByteArray());
                pack.write(dest);
                if(System.out.checkError()){
                    System.err.println("MistEncode: Pipe is broken!");
                    return;
                }
            }
        }
        catch(IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private void processStream() {
        try {
            BufferedInputStream src = new BufferedInputStream(System.in);
            BufferedOutputStream dest = new BufferedOutputStream(System.out);
            Packet pack = new Packet();
            int rdcnt = -1;
            do {
                if((rdcnt = pack.read(src)) > 0) {
                    pack.setPayload(packMessage(pack.getPayload()).toByteArray());
                    pack.write(dest);
                    if(System.out.checkError()){
                        System.err.println("MistEncode: Pipe is broken!");
                        return;
                    }
                }
            } while(rdcnt != -1);
        }
        catch(IOException e) {
            System.err.println(e.getMessage());
        }
    }
    
    private void processFile() {
        try {
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(messageFile));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte [] block = new byte[1024];
            int rdcnt = -1;
            while((rdcnt = in.read(block)) != -1)
                bos.write(block, 0, rdcnt);

            BufferedOutputStream dest = new BufferedOutputStream(System.out);
            Packet pack = new Packet();
            pack.setPayload(packMessage(bos.toByteArray()).toByteArray());
            pack.write(dest);
        }
        catch(IOException e) {
            System.err.println(e.getMessage());
        }
    }
    
    private void parseProperties(ArrayList<String> props) {
        int i;
        for(i = 0; i < props.size(); i++) {
            String [] v = props.get(i).split(":");
            String key = "", value = "";
            if(v.length >= 1) {
                key = v[0];
                if(v.length >= 2) 
                    value = v[1];
                messageProperties.put(key, value);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public MistEncode() {
    }

    public void run(String argv[]) {
        LongOpt[] longopts = new LongOpt[] {
            new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'), 
            new LongOpt("line", LongOpt.NO_ARGUMENT, null, 'l'), 
            new LongOpt("stream", LongOpt.NO_ARGUMENT, null, 's'), 
            new LongOpt("file", LongOpt.REQUIRED_ARGUMENT, null, 'f'), 
            new LongOpt("wrap", LongOpt.REQUIRED_ARGUMENT, null, 'w'),
            new LongOpt("property", LongOpt.REQUIRED_ARGUMENT, null, 'p'),
            new LongOpt("ttl", LongOpt.REQUIRED_ARGUMENT, null, 't'),
        };

        ArrayList<String> props = new ArrayList<String>();

        Getopt g = new Getopt("mist-encode", argv, "hlw:f:p:t:s", longopts);
        int c;
        while((c = g.getopt()) != -1) {
            switch(c) {
            case 'f':
                currCmd = CmdType.FILE;
                messageFile = g.getOptarg();
                break;
            case 'l':
                currCmd = CmdType.LINE;
                break;
            case 's':
                currCmd = CmdType.STREAM;
                break;
            case 'w':
                messageId = g.getOptarg();
                break;
            case 'p':
                props.add(g.getOptarg());
                break;
            case 't':
                try {
                    messageTTL = Integer.parseInt(g.getOptarg()) * 1000; 
                }
                catch(NumberFormatException e) {
                    System.err.println(String.format("%s, parse TTL fail.", e.getMessage()));
                }
                break;
            }
        }
        
        if(props.size() > 0) {
            parseProperties(props);
            hasProperties = true;
        }

        switch(currCmd) {
        case LINE:
            processLine();
            break;
        case STREAM:
            processStream();
            break;
        case FILE:
            processFile();
            break;
        case NONE:
            printUsage();
            break;
        }
    }

    public static void main(String argv[]) {
        new MistEncode().run(argv);
    }
}
