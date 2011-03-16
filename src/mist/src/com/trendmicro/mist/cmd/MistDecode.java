package com.trendmicro.mist.cmd;

import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import com.trendmicro.mist.util.Packet;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import com.google.protobuf.InvalidProtocolBufferException;
import com.trendmicro.mist.proto.MistMessage;
import com.trendmicro.spn.proto.SpnMessage.Container;

public class MistDecode {
    enum CmdType {
        NONE, LINE, STREAM, COUNT, FILE, DEBUG
    }

    private CmdType currCmd = CmdType.NONE;

    private void printUsage() {
        System.out.printf("Usage:%n");
        System.out.printf("      mist-decode [options [arguments...] ]... %n%n");
        System.out.printf("Options: %n");
        System.out.printf("  --count, -c %n");
        System.out.printf("        count the number of messages decoded %n%n");
        System.out.printf("  --file, -f %n");
        System.out.printf("        decode each message as single file %n%n");
        System.out.printf("  --line, -l %n");
        System.out.printf("        decode message stream into line text file %n%n");
        System.out.printf("  --stream, -s %n");
        System.out.printf("        unpack the received length-data stream from MessageBlock structure %n%n");
        System.out.printf("  --debug, -d %n");
        System.out.printf("        dump the message stream as plain text for debug %n%n");
        System.out.printf("  --help, -h %n");
        System.out.printf("        display help messages %n%n");
    }
    
    private byte [] unpackMessage(byte [] binary) {
        try {
            MistMessage.MessageBlock.Builder mblock_builder = MistMessage.MessageBlock.newBuilder();
            mblock_builder.mergeFrom(binary);
            MistMessage.MessageBlock msg_block = mblock_builder.build();
            Container.Builder cont_builder = Container.newBuilder();
            cont_builder.mergeFrom(msg_block.getMessage().toByteArray());
            Container cont = cont_builder.build();
            return cont.getContainerBase().getMessageList().getMessages(0).getDerived().toByteArray();
        }
        catch(Exception e) {
            return binary;
        }
    }

    private void processLine() {
        try {
            BufferedInputStream src = new BufferedInputStream(System.in);
            BufferedWriter dest = new BufferedWriter(new OutputStreamWriter(System.out));
            Packet pack = new Packet();
            int rdcnt = -1;
            do {
                if((rdcnt = pack.read(src)) > 0) {
                    String line = new String(unpackMessage(pack.getPayload()), "UTF-8");
                    dest.write(line);
                    dest.newLine();
                    dest.flush();
                    if(System.out.checkError()) {
                        System.err.println("MistDecode: Pipe is broken!");
                        break;
                    }
                }
            } while(rdcnt != -1);
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
                    pack.setPayload(unpackMessage(pack.getPayload()));
                    pack.write(dest);
                    if(System.out.checkError()) {
                        System.err.println("MistDecode: Pipe is broken!");
                        break;
                    }
                }
            } while(rdcnt != -1);
        }
        catch(IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private void processCount() {
        try {
            BufferedInputStream src = new BufferedInputStream(System.in);
            Packet pack = new Packet();
            int rdcnt = -1;
            int cnt = 0;
            do {
                if((rdcnt = pack.read(src)) > 0) {
                    MistMessage.MessageBlock.Builder mblock_builder = MistMessage.MessageBlock.newBuilder();
                    try {
                        mblock_builder.mergeFrom(pack.getPayload());
                        MistMessage.MessageBlock msg_block = mblock_builder.build();
                        System.out.printf("message %d: %d bytes %n", ++cnt, msg_block.getMessage().size());
                    }
                    catch(InvalidProtocolBufferException e) {
                        System.out.printf("message %d: %d bytes %n", ++cnt, pack.getPayload().length);
                    }
                    if(System.out.checkError()) {
                        System.err.println("MistDecode: Pipe is broken!");
                        break;
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
            BufferedInputStream src = new BufferedInputStream(System.in);
            Packet pack = new Packet();
            int rdcnt = -1;
            int cnt = 0;
            do {
                if((rdcnt = pack.read(src)) > 0) {
                    try {
                        byte [] buffer = unpackMessage(pack.getPayload());
                        String fname = String.format("msg-%06d.data", cnt);
                        BufferedOutputStream fout = new BufferedOutputStream(new FileOutputStream(fname));
                        fout.write(buffer);
                        fout.close();
                    }
                    catch(IOException e) {
                        System.err.printf("can not write message %d: %s", cnt, e.getMessage());
                    }
                    cnt++;
                }
            } while(rdcnt != -1);
        }
        catch(IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private void processDump() {
        try {
            BufferedInputStream src = new BufferedInputStream(System.in);
            Packet pack = new Packet();
            int rdcnt = -1;
            int cnt = 0;
            do {
                if((rdcnt = pack.read(src)) > 0) {
                    try {
                        MistMessage.MessageBlock.Builder mblock_builder = MistMessage.MessageBlock.newBuilder();
                        mblock_builder.mergeFrom(pack.getPayload());
                        MistMessage.MessageBlock msg_block = mblock_builder.build();
                        System.out.printf("[ %06d ] %n", cnt + 1);
                        System.out.printf("==> MessageBlock %n %s", msg_block.toString());
                    }
                    catch(Exception e) {
                        System.err.printf("not in protocol buffer format");
                    }
                    if(System.out.checkError()) {
                        System.err.println("MistDecode: Pipe is broken!");
                        break;
                    }
                    cnt++;
                }
            } while(rdcnt != -1);
        }
        catch(IOException e) {
            System.err.println(e.getMessage());
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public MistDecode() {
    }

    public void run(String argv[]) {
        LongOpt[] longopts = new LongOpt[] {
            new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'), 
            new LongOpt("line", LongOpt.NO_ARGUMENT, null, 'l'), 
            new LongOpt("stream", LongOpt.NO_ARGUMENT, null, 's'), 
            new LongOpt("file", LongOpt.NO_ARGUMENT, null, 'f'), 
            new LongOpt("count", LongOpt.NO_ARGUMENT, null, 'c'),
            new LongOpt("debug", LongOpt.NO_ARGUMENT, null, 'd'),
        };

        Getopt g = new Getopt("mist-decode", argv, "hlcfds", longopts);
        int c;
        while((c = g.getopt()) != -1) {
            switch(c) {
            case 'l':
                currCmd = CmdType.LINE;
                break;
            case 's':
                currCmd = CmdType.STREAM;
                break;
            case 'f':
                currCmd = CmdType.FILE;
                break;
            case 'c':
                currCmd = CmdType.COUNT;
                break;
            case 'd':
                currCmd = CmdType.DEBUG;
                break;
            }
        }

        switch(currCmd) {
        case LINE:
            processLine();
            break;
        case COUNT:
            processCount();
            break;
        case FILE:
            processFile();
            break;
        case STREAM:
            processStream();
            break;
        case DEBUG:
            processDump();
            break;
        case NONE:
            printUsage();
            break;
        }
    }

    public static void main(String argv[]) {
        new MistDecode().run(argv);
    }
}
