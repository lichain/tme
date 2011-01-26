
package com.trendmicro.mist;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.IOException;
import java.util.ArrayList;

abstract public class ThreadInvoker implements Runnable {
    protected InputStream myIn;
    protected PrintStream myOut;
    protected PrintStream myErr;
    protected int exitCode = 0;
    protected boolean inputReplaced = false;
    protected boolean outputReplaced = false;
    protected boolean errorReplaced = false;
    
    private String myArg;
    private Thread myThread;
    private String invokerName;

    private class OutputHooker implements Runnable {
        private OutputListener outputListener;
        private BufferedReader streamIn;
        private String name;
        
        public OutputHooker(String id, InputStream in, OutputListener listener) {
            name = id;
            streamIn = new BufferedReader(new InputStreamReader(in));
            outputListener = listener;
        }
        
        public void run() {
            try {
                String line;
                while((line = streamIn.readLine()) != null)
                    outputListener.receiveOutput(name, line);
            }
            catch(IOException e) {
            }
        }
    }
    
    private String [] splitIgnoreEmpty(String str, String sep) {
        ArrayList<String> fields = new ArrayList<String>();
        String[] vec = str.split(sep);
        for(String t : vec) {
            if(t != null && t.length() > 0)
                fields.add(t);
        }
        String [] ret = new String[fields.size()];
        fields.toArray(ret);
        return ret;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public interface OutputListener {
        void receiveOutput(String name, String msg);
    }

    public ThreadInvoker(String name) {
        myIn = System.in;
        myOut = System.out;
        myErr = System.err;
        invokerName = name;
    }
   
    public abstract int run(String [] argv);

    public InputStream getInputStream() {
        try {
            PipedOutputStream processOut = new PipedOutputStream();
            PipedInputStream externalIn = new PipedInputStream(processOut);
            myOut = new PrintStream(processOut);
            outputReplaced = true;
            return externalIn;
        }
        catch(Exception e) {
            return null;
        }
    }

    public InputStream getErrorStream() {
        try {
            PipedOutputStream processErr = new PipedOutputStream();
            PipedInputStream externalIn = new PipedInputStream(processErr);
            myErr = new PrintStream(processErr);
            errorReplaced = true;
            return externalIn;
        }
        catch(Exception e) {
            return null;
        }
    }

    public void setErrorListener(OutputListener listener) {
        try {
            PipedOutputStream processErr = new PipedOutputStream();
            PipedInputStream externalIn = new PipedInputStream(processErr);
            myErr = new PrintStream(processErr);
            errorReplaced = true;
            new Thread(new OutputHooker(invokerName + " stderr", externalIn, listener)).start();
        }
        catch(Exception e) {
        }
    }

    public OutputStream getOutputStream() {
        try {
            PipedInputStream processIn = new PipedInputStream();
            PipedOutputStream externalOut = new PipedOutputStream(processIn);
            myIn = processIn;
            inputReplaced = true;
            return externalOut;
        }
        catch(Exception e) {
            return null;
        }
    }

    public void setOutputListener(OutputListener listener) {
        try {
            PipedOutputStream processOut = new PipedOutputStream();
            PipedInputStream externalIn = new PipedInputStream(processOut);
            myOut = new PrintStream(processOut);
            outputReplaced = true;
            new Thread(new OutputHooker(invokerName + " stdout", externalIn, listener)).start();
        }
        catch(Exception e) {
        }
    }

    public void invoke(String arg) {
        myArg = arg;
        myThread = new Thread(this);
        myThread.start();
    }

    public Thread getThread() {
        return myThread;
    }
    
    public void waitForComplete() {
        try {
            myThread.join();
        }
        catch(InterruptedException e) {
        }
    }
    
    public int exitValue() {
        return exitCode;
    }

    public void run() {
        exitCode = run(splitIgnoreEmpty(myArg, " "));
        
        if(inputReplaced) {
            try {
                myIn.close();
            }
            catch(IOException e) {
            }
        }
        if(outputReplaced) {
            myOut.close();
        }
        if(errorReplaced) {
            myErr.close();
        }
    }
}
