package com.trendmicro.tme.portal;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExchangeMetricArchiver {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeMetricArchiver.class);
    private ProcessBuilder processBuilder;
    private long lastArchiveTimestamp = 0;
    private long intervalMillis;

    public ExchangeMetricArchiver(String rrddir, String maxRecords, int intervalSec) {
        processBuilder = new ProcessBuilder(new String[] {
            "/opt/trend/tme/bin/archive_metrics.sh", rrddir, maxRecords
        });
        processBuilder.redirectErrorStream(true);
        intervalMillis = intervalSec * 1000;
    }

    public void execute() {
        long currentTimestamp = System.currentTimeMillis();
        if(currentTimestamp - lastArchiveTimestamp < intervalMillis) {
            return;
        }
        lastArchiveTimestamp = currentTimestamp;
        try {
            Process process = processBuilder.start();
            String output = IOUtils.toString(process.getInputStream());
            if(process.waitFor() != 0) {
                logger.error("error executing archive script: {}", output);
            }
            process.destroy();
        }
        catch(IOException e) {
            logger.error(e.getMessage(), e);
        }
        catch(InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
