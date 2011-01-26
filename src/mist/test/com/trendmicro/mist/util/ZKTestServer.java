package com.trendmicro.mist.util;

public class ZKTestServer {
    private Process serverProcess = null;
    private int port;

    class Killer extends Thread {
        private Process serverProcess;

        public Killer(Process serverProcess) {
            this.serverProcess = serverProcess;
        }

        public void run() {
            serverProcess.destroy();
        }
    }

    public ZKTestServer(int port) {
        this.port = port;
    }

    public void stop() {
        serverProcess.destroy();
        try {
            serverProcess.waitFor();
        }
        catch(InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        try {
            Runtime.getRuntime().exec("rm -rf /tmp/testzk").waitFor();
            String cmd = "java -cp \"" + System.getProperty("java.class.path") + "\" org.apache.zookeeper.server.ZooKeeperServerMain " + port + " /tmp/testzk";
            serverProcess = Runtime.getRuntime().exec(cmd);
            Runtime.getRuntime().addShutdownHook(new Killer(serverProcess));
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
}
