package com.github.supermoonie;

import com.github.supermoonie.auto.AutoChrome;
import com.github.supermoonie.event.network.RequestWillBeSent;
import com.github.supermoonie.handler.BytesResponseHandler;
import com.github.supermoonie.handler.DocumentResponseHandler;
import com.github.supermoonie.httpclient.HttpClient;
import com.github.supermoonie.todo.Todo;
import com.github.supermoonie.type.network.GetResponseBodyResult;
import com.github.supermoonie.type.page.NavigateResult;
import net.bramp.ffmpeg.FFmpeg;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    public static final String VIDEO_PATH = USER_DIR + "video" + File.separator;

    private static final Pattern NUM_PATTERN = Pattern.compile("ts-(\\d+)\\.ts");

    private static final Pattern LINK_PATTERN = Pattern.compile(".*(http.*)");

    private static final Pattern VIDEO_NAME_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5_a-zA-Z0-9]*).*");

    public static void main(String[] args) throws IOException {
        File videoDir = new File(VIDEO_PATH);
        if (!videoDir.exists() && !videoDir.mkdir()) {
            JOptionPane.showMessageDialog(null,VIDEO_PATH + " 创建失败", "ERROR", JOptionPane.ERROR_MESSAGE);
        }
        HttpClient httpClient = HttpClient.defaultInstance();
        String indexUrl = "http://goudaitv.com/vodplay/64845-1-43.html";
        Document document = httpClient.get(indexUrl, 3, DocumentResponseHandler.getInstance()).getContent();
        Elements iframes = document.select("iframe");
        if (null != iframes && iframes.size() > 0) {
            for (Element iframe : iframes) {
                String src = iframe.attr("src");
                Matcher matcher = LINK_PATTERN.matcher(src);
                if (matcher.find()) {
                    indexUrl = matcher.group(1);
                }
            }
        }
        try (AutoChrome autoChrome = new AutoChrome.Builder()
//                .setOtherArgs(Collections.singletonList("--headless"))
                .build()) {
            String iframeUrl = indexUrl;
            Todo<NavigateResult> todo = (chrome) -> chrome.navigate(iframeUrl);
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
            String srcFilePath = VIDEO_PATH + "111111111.mp4";
            fFmpeg.run(new ArrayList<String>() {{
                add("-f");
                add("concat");
                add("-safe");
                add("0");
                add("-i");
                add(VIDEO_PATH + "ts.txt");
                add("-c");
                add("copy");
                add(srcFilePath);
            }});
            fileList.forEach(file -> file.delete());
            File srcFile = new File(srcFilePath);
            File destFile;
            Matcher matcher = VIDEO_NAME_PATTERN.matcher(document.title());
            if (matcher.find()) {
                destFile = new File(VIDEO_PATH + matcher.group(1) + ".mp4");
            } else {
                destFile = new File(VIDEO_PATH + document.title().replaceAll("[^\\u4e00-\\u9fa5_a-zA-Z0-9]", "") + ".mp4");
            }
            FileUtils.moveFile(srcFile, destFile);
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
