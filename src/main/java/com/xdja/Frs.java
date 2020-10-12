package com.xdja;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileInputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * @author created by wangbo on 2018/04/28 10:42:55
 */
@SuppressWarnings({"java:S106", "java:S115", "java:S3740", "java:S125", "java:S1186", "java:S1192", "FieldCanBeLocal", "ResultOfMethodCallIgnored", "java:S2259"})
public class Frs {

    private static final Pattern DEALING = Pattern.compile("^success$|^2$");
    private static final Pattern PUT_GET = Pattern.compile("^put$|^get$");

    private static String host = "localhost";
    private static final String SERVER_PORT = "8080";
    private static String lastResult = "";

    private static String optMethod = "";

    private static String uuid = "";
    private static String fileName = "";
    private static String md5Sum = "";
    private static String protocol = "ftp";
    private static String method = "put";

    @SneakyThrows
    public static void main(String... args) {
        if (args.length == 0) {
            System.out.print("java -jar frs.jar [front_ip] [UUID] [get|put] [file_name] [protocol]\n");
        } else {
            long start = System.currentTimeMillis();
            run(args);
            System.out.printf("cost %sms%n", System.currentTimeMillis() - start);
        }
    }

    @SneakyThrows
    public static synchronized void run(String... args) {
        init(args);
        FileExchangeInfo fileExchangeInfo;
        final String put = "put";
        if (put.equalsIgnoreCase(method)) {
            fileExchangeInfo = put();
            if (null != fileExchangeInfo && DEALING.matcher(fileExchangeInfo.getResult()).matches()) {
                query(fileExchangeInfo);
            }
        } else {
            fileExchangeInfo = get();
            if (null != fileExchangeInfo && DEALING.matcher(fileExchangeInfo.getResult()).matches()) {
                fileExchangeInfo = query(fileExchangeInfo);
            }
            final String complete = "0";
            if (null != fileExchangeInfo && fileExchangeInfo.getResult().matches(complete)) {
                down();
            }
        }
    }

    /**
     * @param args 验证以及初始化host uuid 文件名
     */
    @SneakyThrows
    private static void init(String... args) {
        try {
            host = args[0];
            uuid = args[1];
            method = args[2];
            if (!PUT_GET.matcher(method).matches()) {
                System.err.print("[METHOD] 错误\n");
            }
            System.out.printf("%s%n", Arrays.toString(args));
        } catch (Exception e) {
            System.err.print("[HOST]|[UUID] 缺失\n");
        }
        int three = 3;
        if (args.length > three) {
            fileName = args[3];
        }
        int four = 4;
        if (args.length > four) {
            protocol = args[4];
        }
    }

    @SneakyThrows
    private static FileExchangeInfo put() {
        method = "put";
        optMethod = method;
        FileExchangeInfo fileExchangeInfo = new FileExchangeInfo();
        String fileAbsolutePath = fileName.substring(fileName.lastIndexOf(File.separator) + 1);
        long start = System.currentTimeMillis();
        if (upload(fileAbsolutePath)) {
            System.out.printf("[FTP] 上传文件到前置 - 耗时: %sms%n", System.currentTimeMillis() - start);
            md5Sum = md5(Files.readAllBytes(Paths.get(fileAbsolutePath)));
            fileExchangeInfo = request(getHeader(method));
        }
        return fileExchangeInfo;
    }

    @SneakyThrows
    private static FileExchangeInfo get() {
        method = "get";
        optMethod = method;
        return request(getHeader(method));
    }

    @SneakyThrows
    private static FileExchangeInfo query(FileExchangeInfo fileExchangeInfo) {
        method = "query";
        while (DEALING.matcher(fileExchangeInfo.getResult()).matches()) {
            fileExchangeInfo = request(getHeader(method));
        }
        return fileExchangeInfo;
    }

    @SneakyThrows
    private static void down() {
        method = "down";
        request(getHeader(method));
    }

    private static String getHeader(String method) {
        return String.format("Uuid=%s;FileName=%s;Md5Sum=%s;protol=%s;Type=%s", uuid, fileName, md5Sum, protocol, method);
    }

    @SneakyThrows
    private static FileExchangeInfo request(String header) {
        String down = "down";
        String url = String.format("http://%s:%s/file_exchange.php", host, SERVER_PORT);
        if (down.equalsIgnoreCase(method)) {
            File downFile = new File(down);
            if (!downFile.exists()) {
                downFile.mkdirs();
            }
            final byte[] responseBytes = HttpRequest.get(url).header("xdja_auth", header).execute().bodyBytes();
            Files.write(Paths.get(down + File.separator + fileName.substring(fileName.lastIndexOf(File.separator) + 1)), responseBytes);
            System.out.printf("[DOWN] 文件成功, 位于./down/%s%n", fileName.substring(fileName.lastIndexOf(File.separator) + 1));
            return new FileExchangeInfo();
        } else {
            final String responseStr = HttpRequest.get(url).header("xdja_auth", header).execute().body();
            return result(JSON.parseObject(JSON.toJSONString(JSON.parseObject(responseStr).get("result")), FileExchangeInfo.class));
        }
    }

    @SneakyThrows
    private static FileExchangeInfo result(FileExchangeInfo fileExchangeInfo) {
        if (null != fileExchangeInfo) {
            String failedCode = fileExchangeInfo.getFailedCode();
            String uploadResult = fileExchangeInfo.getResult();
            switch (uploadResult) {
                case "0":
                    System.out.printf("[%s 文件成功]%n", optMethod.toUpperCase());
                    lastResult = "0";
                    break;
                case "failed":
                case "1":
                    failed(failedCode);
                    break;
                case "success":
                case "2":
                    if (!uploadResult.equals(lastResult)) {
                        System.out.printf("[%s] 文件处理中%n", optMethod.toUpperCase());
                        lastResult = "2";
                    }
                    break;
                default:
            }
        }
        return fileExchangeInfo;
    }

    @SneakyThrows
    private static String md5(byte[] bytes) {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : md5.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @SneakyThrows
    private static void failed(String failedCode) {
        switch (failedCode) {
            case "-101":
            case "-106":
                System.err.print("[FAILED] [PUT] unique_id 错误\n");
                break;
            case "-102":
                System.err.print("[FAILED] [PUT] UUID 错误\n");
                break;
            case "-103":
                System.err.print("[FAILED] [PUT] 文件未上传或MD5值不对 | [GET]还没到前置\n");
                break;
            case "-1031":
                System.err.print("[FAILED] [PUT] RSYNC同步文件失败\n");
                break;
            case "-1032":
                System.err.print("[FAILED] [PUT] 文件大小超过1G或空文件\n");
                break;
            case "-1033":
                System.err.print("[FAILED] [PUT] 文件大小为0\n");
                break;
            case "-105":
                System.err.print("[FAILED] [PUT] UUID错误\n");
                break;
            case "-107":
                System.err.printf("[FAILED] [%s] 目的服务器异常%n", method.toUpperCase());
                break;
            case "-109":
                System.err.print("[FAILED] [PUT] 文件格式已被禁止上传\n");
                break;
            case "-110":
                System.err.print("[FAILED] [PUT] 文件安全监控未通过\n");
                break;
            case "-111":
                System.err.print("[FAILED] 关键字过滤出错\n");
                break;
            case "-112":
                System.err.print("[FAILED] 关键字过滤被拦截\n");
                break;
            default:
        }
    }

    @SneakyThrows
    static boolean upload(String absoluteFileName) {
        String fileName = absoluteFileName.substring(absoluteFileName.lastIndexOf(File.separator) + 1);
        System.out.print("[FTP] 开始上传文件到前置\n");
        FTPClient ftp = new FTPClient();
        ftp.enterLocalPassiveMode();
        ftp.connect(host, 21);
        ftp.login("xdja", "Ftp@123");
        int reply = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            System.err.print("建立FTP链接异常\n");
            return false;
        }
        ftp.changeWorkingDirectory("");
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        try (FileInputStream fileInputStream = new FileInputStream(absoluteFileName)) {
            if (!ftp.storeFile(fileName, fileInputStream)) {
                System.err.print("[FTP]写入文件失败\n");
                return false;
            }
            ftp.logout();
            if (ftp.isConnected()) {
                ftp.disconnect();
            }
        }
        return true;
    }

    @Data
    private static class FileExchangeInfo implements Serializable {
        private static final long serialVersionUID = -5394570988929622182L;
        private String result = "";
        @JSONField(name = "failed_code")
        private String failedCode = "";
        @JSONField(name = "failed_reason")
        private String failedReason = "";
        @JSONField(name = "unique_id")
        private String uniqueId = "";
    }

}
