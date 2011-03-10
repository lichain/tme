package com.trendmicro.mist.util;

import java.util.ArrayList;

public class ParallelExecutor<T> {
    private ArrayList<Executor> executorList = new ArrayList<Executor>();

    public interface Runner<T> {
        T run();
    }

    class Executor extends Thread {
        private T result;
        private Runner<T> runner;

        public Executor(Runner<T> runner) {
            this.runner = runner;
        }

        public void run() {
            result = runner.run();
        }

        public T getResult() {
            return result;
        }
    }

    public void addRunner(Runner<T> runner) {
        executorList.add(new Executor(runner));
    }

    public ArrayList<T> waitCompleted() {
        ArrayList<T> resultList = new ArrayList<T>();
        for(Executor exe : executorList)
            exe.start();

        for(Executor exe : executorList) {
            try {
                exe.join();
                resultList.add(exe.getResult());
            }
            catch(InterruptedException e) {
            }
        }

        return resultList;
    }
}
