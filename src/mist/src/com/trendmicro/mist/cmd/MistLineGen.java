package com.trendmicro.mist.cmd;

import java.util.Random;

import com.trendmicro.spn.common.util.Utils;

import gnu.getopt.Getopt;

public class MistLineGen 
{
    private int msgLimit = -1;
    private int msgInterval = 0;
    private int msgSize = 64;
    private String [] argv;

    private void printUsage() {
        System.out.printf("Usage:%n");
        System.out.printf("      mist-line-gen [options...] %n%n");
        System.out.printf("Options: %n");
        System.out.printf("  -c `COUNT'%n");
        System.out.printf("        generated line count, default infinite %n%n");
        System.out.printf("  -s `SIZE'%n");
        System.out.printf("        average line length, default 64 bytes %n%n");
        System.out.printf("  -i `INTERVAL'%n");
        System.out.printf("        average time interval between each line, in millisecond, default 0 %n%n");
        System.out.printf("  -h %n");
        System.out.printf("        display help messages %n%n");
    }

    private String makeLine() {
        Random rand = new Random();
        int currSize = Math.abs(rand.nextInt()) % (msgSize * 2) + 1;
        byte [] line = new byte[currSize];
        int i;
        for(i = 0; i < currSize; i++) 
            line[i] = (byte)((Math.abs(rand.nextInt()) % 64) + 32);
        return new String(line);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public MistLineGen(String [] _argv) {
        argv = _argv;
    }

    public void run() {
        Getopt g = new Getopt("MistLineGen", argv, "hi:c:s:");

        boolean help = false;
        boolean noerror = true;
        int c;
        String arg = null;
        try {
            while((c = g.getopt()) != -1) {
                switch(c) {
                    case 'i':
                        arg = g.getOptarg();
                        msgInterval = Integer.parseInt(arg);
                        break;
                    case 'c':
                        arg = g.getOptarg();
                        msgLimit = Integer.parseInt(arg);
                        break;
                    case 's':
                        arg = g.getOptarg();
                        msgSize = Integer.parseInt(arg);
                        break;
                    case 'h':
                        help = true;
                        break;
                    case '?':
                        // getopt() already printed an error
                        break;
                    default:
                        System.err.printf("getopt() returned %d %n", c);
                        break;
                }
            }
        }
        catch(NumberFormatException e) {
            System.err.printf("%s, invalid number format %n", e.getMessage());
            noerror = false;
        }

        if(help) 
            printUsage();
        else if(noerror) {
            Random rand = new Random();
            int msg_cnt = 0;
            while(true) {
                System.out.println(makeLine());
                if(System.out.checkError()){
                    System.err.println("MistLineGen: Pipe is broken!");
                    break;
                }
                msg_cnt++;
                if(msgLimit != -1 && msg_cnt >= msgLimit) 
                    break;
                if(msgInterval > 0)
                    Utils.justSleep(Math.abs(rand.nextInt()) % (msgInterval * 2) + 1);
            }
        }
    }

    public static void main(String [] argv) {
        new MistLineGen(argv).run();
    }
}
