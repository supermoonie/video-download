package com.github.supermoonie.spider;

import com.github.supermoonie.core.Finish;
import com.github.supermoonie.core.SpiderContext;
import com.github.supermoonie.core.node.Node;
import com.github.supermoonie.core.node.Pipeline;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.impl.client.BasicCookieStore;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author supermoonie
 * @since 2020-02-19
 */
@Slf4j
public class S80Spider implements Spider<Void> {

    private static final String USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3_1 like Mac OS X) AppleWebKit/603.1.30 (KHTML, like Gecko) Mobile/14E304 MicroMessenger/6.7.0 NetType/WIFI Language/zh_CN";

    private String indexUrl;

    private String videoName;

    private static final Pattern VIDEO_URL_PATTERN = Pattern.compile("\"(.*index\\.m3u8.*)\"");

    private static final AtomicInteger TS_COUNTER = new AtomicInteger(100000000);

    private static final String VIDEO_DIR = System.getProperty("user.home") + File.separator + "Desktop/";

    private ExecutorService executorService;

    public S80Spider(String indexUrl, String videoName, ExecutorService executorService) {
        this.indexUrl = indexUrl;
        this.videoName = videoName;
        this.executorService = executorService;
    }

    @Override
    public String name() {
        return this.getClass().getName();
    }

    @Override
    public Executor executor() {
        return Executor.newInstance().use(new BasicCookieStore());
    }

    @Override
    public ExecutorService concurrentExec() {
        return executorService;
    }

    @Override
    public Pipeline<Void> pipeline() {
        Pipeline<Void> pipeline = new Pipeline<>();
        pipeline.add(iframeNode());
        return pipeline;
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
    }

    private Node iframeNode() {
        return Node.syncNode(Request.Get(indexUrl).socketTimeout(20000)
                .setHeader(HttpHeaders.USER_AGENT, USER_AGENT), content -> {
            String html = content.asString();
            Document document = Jsoup.parse(html);
            Elements iframeList = document.select("#olpcode iframe[src]");
            SpiderContext.getPipeline().add(videoNode(iframeList.first().attr("src")));
        });
    }

    private Node videoNode(String link) {
        URI uri = URI.create(link);
        return Node.syncNode(Request.Get(uri).socketTimeout(20_000)
                        .setHeader(HttpHeaders.USER_AGENT, USER_AGENT),
                content -> {
                    String html = content.asString();
                    String preM3u8Uri = uri.getScheme() + "://" + uri.getHost() + parseVideoHtml(html);
                    SpiderContext.getPipeline().add(preM3u8Node(preM3u8Uri));
                });
    }

    private String parseVideoHtml(String html) {
        Matcher matcher = VIDEO_URL_PATTERN.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("pre m3u8 uri not found");
    }

    private Node preM3u8Node(String uri) {
        return Node.syncNode(Request.Get(uri).socketTimeout(20_000).setHeader(HttpHeaders.USER_AGENT, S80Spider.USER_AGENT),
                content -> {
                    String m3u8Text = content.asString();
                    String path = parsePreM3u8Text(m3u8Text);
                    String m3u8Uri = uri.substring(0, uri.lastIndexOf("/") + 1) + path;
                    SpiderContext.getPipeline().add(m3u8Node(m3u8Uri));
                });
    }

    private String parsePreM3u8Text(String m3u8Text) {
        String[] lines = m3u8Text.split("\n");
        for (String line : lines) {
            if (!line.startsWith("#")) {
                return line;
            }
        }
        throw new IllegalStateException("m3u8 uri not found");
    }

    private Node m3u8Node(String uri) {
        return Node.syncNode(Request.Get(uri).socketTimeout(20_000).setHeader(HttpHeaders.USER_AGENT, S80Spider.USER_AGENT),
                content -> {
                    String m3u8Text = content.asString();
                    String baseUri = uri.substring(0, uri.lastIndexOf("/") + 1);
                    List<String> tsList = parseM3u8Text(baseUri, m3u8Text);
                    for (String tsUrl : tsList) {
                        SpiderContext.getPipeline().add(tsNode(tsUrl));
                    }
                    SpiderContext.getPipeline().last().setFinish(() -> {
                        try {
                            concat();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                });
    }

    private List<String> parseM3u8Text(String baseUri, String m3u8Text) {
        String[] lines = m3u8Text.split("\n");
        List<String> tsList = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("#")) {
                tsList.add(baseUri + line);
            }
        }
        return tsList;
    }

    private Node tsNode(String uri) {
        return Node.asyncNode(Request.Get(uri).socketTimeout(20_000).setHeader(HttpHeaders.USER_AGENT, USER_AGENT),
                content -> {
                    byte[] bytes = content.asBytes();
                    String tsName = VIDEO_DIR + TS_COUNTER.getAndIncrement() + ".ts";
                    try {
                        FileUtils.writeByteArrayToFile(new File(tsName), bytes);
                        log.info(tsName + " saved!");
                    } catch (IOException e) {
                        throw new IllegalStateException(tsName + " save failed", e);
                    }
                });
    }

    private void concat() throws IOException {
        Collection<File> listFiles = FileUtils.listFiles(new File(VIDEO_DIR), new String[]{"ts"}, false);
        List<File> fileList = listFiles.stream().sorted((o1, o2) -> {
            int num1 = Integer.parseInt(o1.getName().split("\\.")[0]);
            int num2 = Integer.parseInt(o2.getName().split("\\.")[0]);
            if (num1 > num2) {
                return 1;
            } else if (num1 < num2) {
                return -1;
            }
            return 0;
        }).collect(Collectors.toList());
        List<String> pathList = new ArrayList<>();
        fileList.forEach(file -> pathList.add("file '" + file.getAbsoluteFile() + "'"));
        File txtFile = new File(VIDEO_DIR + "ts.txt");
        FileUtils.writeLines(txtFile, pathList, false);
        FFmpeg fFmpeg = new FFmpeg();
        String destFilePath = VIDEO_DIR + "00000000001.mp4";
        fFmpeg.run(new ArrayList<String>() {{
            add("-f");
            add("concat");
            add("-safe");
            add("0");
            add("-i");
            add(VIDEO_DIR + "ts.txt");
            add("-c");
            add("copy");
            add(destFilePath);
        }});
        fileList.forEach(File::delete);
        FileUtils.moveFile(new File(destFilePath), new File(VIDEO_DIR + videoName + ".mp4"));
        txtFile.delete();
    }
}
