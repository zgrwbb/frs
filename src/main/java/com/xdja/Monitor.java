package com.xdja;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

/**
 * @author created by wangbo on 2018/04/28 10:42:55
 */
@SuppressWarnings({"java:S106", "java:S115", "java:S3740", "java:S1192", "FieldCanBeLocal"})
public class Monitor {
    private static String host;
    private static String uuid;
    private static String waitTime = "1000";
    @Getter
    @Setter
    private static String monitorFolder = "";

    public static void main(String... args) {
        System.out.print("*** start ***\n\n");
        host = args[0];
        uuid = args[1];
        monitorFolder = args[2];
        waitTime = args.length > 3 ? args[3] : "1000";
        File file = new File(monitorFolder);
        if (!file.exists() && !file.mkdirs()) {
            System.err.printf("创建文件夹失败: %s%n", file.getAbsolutePath());
        }
        watch();
    }

    @SneakyThrows
    public static void watch() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            //给path路径加上文件观察服务
            Paths.get(monitorFolder).register(watchService, ENTRY_CREATE);
            while (true) {
                final WatchKey watchKey = watchService.take();
                watchKey.pollEvents().stream()
                        .filter(watchEvent -> ENTRY_CREATE == watchEvent.kind())
                        .forEach(watchEvent -> {
                            sleep(waitTime);
                            System.out.printf("[CREATE]: %s%n", watchEvent.context());
                            Frs.run(host, uuid, "put", String.valueOf(watchEvent.context()));
                        });
                if (!watchKey.reset()) {
                    break;
                }
            }
        }
    }

    @SneakyThrows
    private static void sleep(String milliseconds) {
        TimeUnit.MILLISECONDS.sleep(Long.parseLong(milliseconds));

    }
}