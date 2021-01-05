package com.xdja;


import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author wenpeng
 */
public class ThreadPool {

    private static final AtomicInteger COUNT = new AtomicInteger(0);


    private ThreadPool() {
        throw new UnsupportedOperationException();
    }

    public static synchronized ThreadPoolExecutor executor() {
        return ThreadPoolEnum.EXECUTOR.getExecutor();
    }

    private enum ThreadPoolEnum {
        /**
         * 实例对象
         */
        EXECUTOR;
        /**
         * 线程池对象
         */
        private final ThreadPoolExecutor executor;

        public ThreadPoolExecutor getExecutor() {
            return executor;
        }

        /**
         * jvm保证这个方法只调用一次
         */
        ThreadPoolEnum() {
            int coreNum = Math.max(Runtime.getRuntime().availableProcessors(), 8);
            executor = new ThreadPoolExecutor(
                    coreNum,
                    coreNum * 2 + 1,
                    200,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    r -> {
                        Thread thread = new Thread(r);
                        thread.setName(String.format("pool-%s", COUNT.getAndIncrement()));
                        return thread;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );
        }
    }

}
