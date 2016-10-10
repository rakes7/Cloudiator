package de.uniulm.omi.cloudiator.lance.util.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * Created by daniel on 07.10.16.
 */
public class LoggingThreadPoolExecutor extends ThreadPoolExecutor {

    final static Logger LOGGER = LoggerFactory.getLogger(LoggingThreadPoolExecutor.class);

    public LoggingThreadPoolExecutor(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            threadFactory);
    }

    @Override protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        Throwable tToLog = t;
        if (tToLog == null && r instanceof Future<?>) {
            try {
                if (((Future) r).isDone() && !((Future) r).isCancelled()) {
                    ((Future) r).get();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                tToLog = e.getCause();
            }
        }
        if (tToLog != null) {
            LOGGER.error("Uncaught exception occurred during the execution of task " + r + ".",
                tToLog);
        }
    }
}
