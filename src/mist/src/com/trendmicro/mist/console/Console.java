
package com.trendmicro.mist.console;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.trendmicro.spn.common.util.Utils;

import jline.ArgumentCompletor;
import jline.Completor;
import jline.ConsoleReader;
import jline.History;
import jline.NullCompletor;
import jline.SimpleCompletor;

public class Console {
    class IdleMonitor extends Thread {
        private boolean run = true;
        private long idleSince = -1;

        public void kill() {
            run = false;
        }

        public synchronized void setIdle() {
            idleSince = System.currentTimeMillis();
        }

        public synchronized void setBusy() {
            idleSince = -1;
        }

        @Override
        public void run() {
            while(run) {
                if(idleSince != -1) {
                    synchronized(this) {
                        // if 30 min idle time reached, exit
                        if((System.currentTimeMillis() - idleSince) / 1000 > 30 * 60) {
                            System.out.println("idle timeout reached, exiting...");
                            System.exit(0);
                        }
                    }
                }
                Utils.justSleep(1000);
            }
        }
    }
    
    private String commandPrompt;
    private HashMap<String, CommandExecutable> commandTable = new HashMap<String, CommandExecutable>();
    private Completor completor = null;
    private boolean enableLog = false;
    private String curUser = null;
    private String pathLog = null;

    public interface CommandListener {
        public void processCommand(String command);
    }

    private CommandListener cmdListener = null;

    private class ExitCommand implements CommandExecutable {
        public void execute(String [] argv) {
            System.exit(0);
        }
        public void help() {
            System.out.printf("usage: exit%n");
            System.out.printf("    exit from the program%n");
        }
        public String explain() {
            return "exit program";
        }
        public Options getOptions() {
            return null;
        }
    }

    private class HelpCommand implements CommandExecutable {
        public void execute(String [] argv) {
            if(argv.length == 1) {
                System.out.printf("commands:%n");
                for(Map.Entry<String, CommandExecutable> e: commandTable.entrySet())
                    System.out.printf(" %-20s %s%n", e.getKey(), e.getValue().explain());
                return;
            }
            if(commandTable.containsKey(argv[1]))
                commandTable.get(argv[1]).help();
            else
                System.out.printf("Don't know how to help on `%s'.%n", argv[1]);
        }
        public void help() {
            System.out.printf("usage: help <command>%n");
            System.out.printf("    list all commands or get help message for specific command%n");
        }
        public String explain() {
            return "getting help";
        }
        public Options getOptions() {
            return null;
        }
    }

    private ConsoleReader prepareReader() throws IOException {
        ConsoleReader reader = new ConsoleReader();
        reader.setUseHistory(true);
        Map<String, String> env = System.getenv();
        String fileHistory = String.format("%s/.%s.history", env.containsKey("HOME") ? env.get("HOME"): "/tmp", commandPrompt);
        reader.setHistory(new History(new File(fileHistory)));
        return reader;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public Console(String prompt) {
        commandPrompt = prompt;
        commandTable.put("help", new HelpCommand());
        commandTable.put("exit", new ExitCommand());
    }

    public void enableLog(boolean enable) {
    	enableLog = enable;
    	if (enableLog) {
    		Map<String, String> env = System.getenv();
    		pathLog = String.format("%s/.%s.log", env.containsKey("HOME") ? env.get("HOME"): "/tmp", commandPrompt);
    	} else {
    		pathLog = null;
    	}
    }

    public void setLogUser(String user) {
    	curUser = user;
    }

    public void setCommandListener(CommandListener listener) {
        cmdListener = listener;
    }

    public void setCompletor(Completor cptr) {
        completor = cptr;
    }

    public static String [] getAllCommands(Map<String, CommandExecutable> cmdTable) {
        String [] cmds = new String[cmdTable.size()];
        cmds = cmdTable.keySet().toArray(cmds);
        return cmds;
    }

    public static String [] getAllArguments(Map<String, CommandExecutable> cmdTable) {
        HashMap<String, Boolean> allArgs = new HashMap<String, Boolean>();
        for(Map.Entry<String, CommandExecutable> e: cmdTable.entrySet()) {
            Options opts = e.getValue().getOptions();
            if(opts == null)
                continue;
            for(Object obj: opts.getOptions()) {
                Option o = (Option) obj;
                allArgs.put("--" + o.getLongOpt(), true);
            }
        }
        String [] args = new String[allArgs.size()];
        args = allArgs.keySet().toArray(args);
        return args;
    }

    public static Completor extractCompletor(Map<String, CommandExecutable> cmdTable) {
        SimpleCompletor cmdCompletor = new SimpleCompletor(getAllCommands(cmdTable));
        SimpleCompletor argCompletor = new SimpleCompletor(getAllArguments(cmdTable));
        return new ArgumentCompletor(new Completor [] { cmdCompletor, argCompletor, new NullCompletor() });
    }

    public void receiveCommand() {
    	DataOutputStream fout = null;

    	IdleMonitor idleMonitor = new IdleMonitor();
    	idleMonitor.start();
        try {
        	if (enableLog && pathLog != null) {
            	fout = new DataOutputStream(new FileOutputStream(pathLog, true));
            }

            ConsoleReader reader = prepareReader();
            if(completor != null)
                reader.addCompletor(completor);
            do {
                idleMonitor.setIdle();
                String line = reader.readLine(commandPrompt + "> ").trim();
                if(cmdListener != null) {
                    idleMonitor.setBusy();
                    cmdListener.processCommand(line);
                }

                if (fout != null) {
                	String log = String.format("[%s] %s: %s\n", Calendar.getInstance().getTime(), curUser, line);
                	fout.writeBytes(log);
                }
            } while(true);
        }
        catch(NullPointerException e) {
            System.err.println("exit");
        }
        catch(Exception e) {
            System.err.println(e.getMessage());
        } finally {
        	if (fout != null) {
        		try { fout.close(); } catch (Exception e) {}
        	}
        	idleMonitor.kill();
            try {
                idleMonitor.join();
            }
            catch(InterruptedException e) {
            }
        }
    }

    public void launch() {
    	DataOutputStream fout = null;

    	IdleMonitor idleMonitor = new IdleMonitor();
        try {
            ConsoleReader reader = prepareReader();
            completor = extractCompletor(commandTable);
            reader.addCompletor(completor);

            if (enableLog && pathLog != null) {
            	fout = new DataOutputStream(new FileOutputStream(pathLog, true));
            }

            idleMonitor.start();

            do {
                idleMonitor.setIdle();
                String line = reader.readLine(commandPrompt + "> ").trim();
                StringTokenizer tok = new StringTokenizer(line);
                if(tok.hasMoreElements()) {
                    String cmd = tok.nextToken();
                    if(commandTable.containsKey(cmd)) {
                        idleMonitor.setBusy();
                        commandTable.get(cmd).execute(line.split("\\s+"));
                    }
                    else
                        System.out.printf("Unknown command `%s'.%n", cmd);
                }

                if (fout != null) {
                	String log = String.format("[%s] %s: %s\n", Calendar.getInstance().getTime(), curUser, line);
                	fout.writeBytes(log);
                }
            } while(true);
        }
        catch(NullPointerException e) {
            System.err.println("exit");
        }
        catch(Exception e) {
            System.err.println(e.getMessage());
        } finally {
        	if (fout != null) {
        		try { fout.close(); } catch (Exception e) {}
        	}
        	idleMonitor.kill();
        	try {
                idleMonitor.join();
            }
            catch(InterruptedException e) {
            }
        }
    }

    public void addCommand(String name, CommandExecutable cmd) {
        commandTable.put(name, cmd);
    }
}
