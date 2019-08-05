package com.github.supermoonie;

import com.github.supermoonie.auto.AutoChrome;
import com.github.supermoonie.event.network.RequestWillBeSent;
import com.github.supermoonie.handler.BytesResponseHandler;
import com.github.supermoonie.httpclient.HttpClient;
import com.github.supermoonie.todo.Todo;
import com.github.supermoonie.type.network.GetResponseBodyResult;
import com.github.supermoonie.type.page.NavigateResult;
import net.bramp.ffmpeg.FFmpeg;
import org.apache.commons.io.FileUtils;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Hello world!
 *
 * @author Administrator
 */
public class App {

    private static final String USER_DIR = System.getProperty("user.dir") + File.separator;

    private static final String VIDEO_PATH = USER_DIR + "video" + File.separator;

    private static final Pattern NUM_PATTERN = Pattern.compile("ts-(\\d+)\\.ts");

    public static void main(String[] args) {
        File videoDir = new File(VIDEO_PATH);
        if (!videoDir.exists() && !videoDir.mkdir()) {
            JOptionPane.showMessageDialog(null,VIDEO_PATH + " 创建失败", "ERROR", JOptionPane.ERROR_MESSAGE);
        }
        try (AutoChrome autoChrome = new AutoChrome.Builder()
//                .setOtherArgs(Collections.singletonList("--headless"))
                .build()) {
            HttpClient httpClient = HttpClient.defaultInstance();
            Todo<NavigateResult> todo = (chrome) -> chrome.navigate("http://goudaitv.com/vodplay/64845-1-38.html");
            RequestWillBeSentListener requestListener = new RequestWillBeSentListener("http*m3u8");
            autoChrome.waitEvent(todo, requestListener, 20_000);
            RequestWillBeSent request = requestListener.getRequestWillBeSent();
            String url = request.getRequest().getUrl();
            GetResponseBodyResult responseBody = autoChrome.getResponseBody(request.getRequestId());
            String responseContent;
            if (responseBody.getBase64Encoded()) {
                responseContent = new String(Base64.getDecoder().decode(responseBody.getBody()));
            } else {
                responseContent = responseBody.getBody();
            }
            String baseUrl = url.substring(0, url.indexOf("/", 10));
            String[] lines = responseContent.split("\n");
            List<String> links = extractTsLinks(baseUrl, lines);
            int index = 100001;
            for (String link : links) {
                System.out.println("正在下载 " + link);
                byte[] content = httpClient.get(link, 3, BytesResponseHandler.getInstance()).getContent();
                FileUtils.writeByteArrayToFile(new File(VIDEO_PATH + "ts-" + index + ".ts"), content);
                index ++;
            }
            Collection<File> listFiles = FileUtils.listFiles(new File(VIDEO_PATH), new String[]{"ts"}, false);
            List<File> fileList = listFiles.stream().sorted((o1, o2) -> {
                String fileName1 = o1.getName();
                String fileName2 = o2.getName();
                Matcher matcher1 = NUM_PATTERN.matcher(fileName1);
                if (matcher1.find()) {
                    int num1 = Integer.parseInt(matcher1.group(1));
                    Matcher matcher2 = NUM_PATTERN.matcher(fileName2);
                    if (matcher2.find()) {
                        int num2 = Integer.parseInt(matcher2.group(1));
                        if (num2 > num1) {
                            return -1;
                        } else {
                            return 1;
                        }
                    }
                }
                return 0;
            }).collect(Collectors.toList());
            List<String> paths = new ArrayList<>();
            fileList.forEach(file -> paths.add("file '" + file.getAbsoluteFile() + "'"));
            FileUtils.writeLines(new File(VIDEO_PATH + "ts.txt"), paths, false);
            FFmpeg fFmpeg = new FFmpeg();
            fFmpeg.run(new ArrayList<String>() {{
                add("-f");
                add("concat");
                add("-safe");
                add("0");
                add("-i");
                add(VIDEO_PATH + "ts.txt");
                add("-c");
                add("copy");
                add(VIDEO_PATH + "64845-1-38.mp4");
            }});
            fileList.forEach(file -> file.delete());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<String> extractTsLinks(String baseUrl, String[] lines) {
        List<String> links = new ArrayList<>();
        for (String line : lines) {
            if (line.endsWith(".ts")) {
                if (!line.startsWith("http")) {
                    if (line.startsWith("/")) {
                        line = baseUrl + line;
                    } else {
                        line = baseUrl + "/" + line;
                    }
                }
                links.add(line);
            }
        }
        return links;
    }
}
