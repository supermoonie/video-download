package com.github.supermoonie;

import com.github.supermoonie.auto.AutoChrome;
import com.github.supermoonie.event.network.RequestWillBeSent;
import com.github.supermoonie.handler.BytesResponseHandler;
import com.github.supermoonie.handler.DocumentResponseHandler;
import com.github.supermoonie.handler.StringResponseHandler;
import com.github.supermoonie.httpclient.HttpClient;
import com.github.supermoonie.todo.Todo;
import com.github.supermoonie.type.network.GetResponseBodyResult;
import com.github.supermoonie.type.page.NavigateResult;
import net.bramp.ffmpeg.FFmpeg;
import org.apache.commons.io.FileUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.supermoonie.util.StringUtils.isEmpty;

/**
 * Hello world!
 *
 * @author Administrator
 */
public class App {

    private static final String USER_DIR = System.getProperty("user.dir") + File.separator;

    private static final String VIDEO_PATH = USER_DIR + "video" + File.separator;

    private static final Pattern NUM_PATTERN = Pattern.compile("ts-(\\d+)\\.ts");

    private static final Pattern LINK_PATTERN = Pattern.compile(".*(https?://[a-zA-Z0-9./]*)\".*");

    private static final Pattern VIDEO_NAME_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5_a-zA-Z0-9\\u3002\\uff1f\\uff01\\uff0c\\u3001\\uff1b\\uff1a\\u201c\\u201d\\u2018\\u2019\\uff08\\uff09\\u300a\\u300b\\u3008\\u3009\\u3010\\u3011\\u300e\\u300f\\u300c\\u300d\\ufe43\\ufe44\\u3014\\u3015\\u2026\\u2014\\uff5e\\ufe4f\\uffe5]*).*");

    public static void main(String[] args) throws IOException {
        File videoDir = new File(VIDEO_PATH);
        if (!videoDir.exists() && !videoDir.mkdir()) {
            JOptionPane.showMessageDialog(null, VIDEO_PATH + " 创建失败", "ERROR", JOptionPane.ERROR_MESSAGE);
        }
        HttpClient httpClient = HttpClient.defaultInstance();
        String indexUrl = "";
        Document document = httpClient.get(indexUrl, 3, DocumentResponseHandler.getInstance()).getContent();
        String frameSrc = findFrameSrc(document);
        if (null != frameSrc) {
            indexUrl = frameSrc;
        }
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<String> links = m3u8Content(httpClient, indexUrl);
            CountDownLatch latch = new CountDownLatch(links.size());
            int i = 1000000;
            for (String link : links) {
                final int index = i;
                executor.submit(() -> {
                    System.out.println("正在下载 " + link);
                    try {
                        byte[] content = httpClient.get(link, 3, BytesResponseHandler.getInstance()).getContent();
                        FileUtils.writeByteArrayToFile(new File(VIDEO_PATH + "ts-" + index + ".ts"), content);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
                i ++;
            }
            latch.await();
            concat();
            rename(document.title());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }

    private static List<String> m3u8Content(HttpClient httpClient, String indexUrl) throws Exception {
        if (indexUrl.endsWith(".m3u8")) {
            String content = httpClient.get(indexUrl, 3, StringResponseHandler.getInstance()).getContent();
            String baseUrl = indexUrl.substring(0, indexUrl.indexOf("/", 10));
            String[] lines = content.split("\n");
            return extractTsLinks(baseUrl, lines);
        }
        try (AutoChrome autoChrome = new AutoChrome.Builder()
//                .setOtherArgs(Collections.singletonList("--headless"))
                .build()) {
            Todo<NavigateResult> todo = (chrome) -> chrome.navigate(indexUrl);
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
            return extractTsLinks(baseUrl, lines);
        }

    }

    private static String findFrameSrc(Document document) {
        Elements iframes = document.select("iframe");
        if (null != iframes && iframes.size() > 0) {
            for (Element iframe : iframes) {
                String src = iframe.attr("src");
                if (isEmpty(src)) {
                    src = iframe.outerHtml();
                } else {
                    return src;
                }
                Matcher matcher = LINK_PATTERN.matcher(src);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return null;
    }

    private static void rename(String name) throws IOException {
        File srcFile = new File(VIDEO_PATH + "111111111.mp4");
        File destFile;
        Matcher matcher = VIDEO_NAME_PATTERN.matcher(name);
        if (matcher.find()) {
            destFile = new File(VIDEO_PATH + matcher.group(1) + ".mp4");
        } else {
            destFile = new File(VIDEO_PATH + name.replaceAll("[^\\u4e00-\\u9fa5_a-zA-Z0-9]", "") + ".mp4");
        }
        FileUtils.moveFile(srcFile, destFile);
    }

    private static void concat() throws IOException {
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
        fileList.forEach(File::delete);
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
