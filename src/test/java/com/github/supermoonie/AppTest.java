package com.github.supermoonie;

import com.github.supermoonie.spider.S80Spider;
import com.github.supermoonie.spider.SpiderDispatcher;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unit test for simple App.
 */
public class AppTest {

    @Test
    public void dispatcher() throws Exception {
        SpiderDispatcher dispatcher = new SpiderDispatcher();
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<String> links = new ArrayList<String>(){{
            add("http://www.8080s.net/ju/35460/play-377976");
            add("http://www.8080s.net/ju/35460/play-377977");
            add("http://www.8080s.net/ju/35460/play-378174");
            add("http://www.8080s.net/ju/35460/play-378175");
            add("http://www.8080s.net/ju/35460/play-378721");
            add("http://www.8080s.net/ju/35460/play-378722");
            add("http://www.8080s.net/ju/35460/play-378723");
            add("http://www.8080s.net/ju/35460/play-378726");
            add("http://www.8080s.net/ju/35460/play-378719");
            add("http://www.8080s.net/ju/35460/play-378720");
            add("http://www.8080s.net/ju/35460/play-378724");
            add("http://www.8080s.net/ju/35460/play-378725");
        }};
        for (int i = 0; i <= links.size(); i++) {
            S80Spider s80Spider = new S80Spider(links.get(i), "三生三世枕上书-" + (i + 31) + ".mp4", executorService);
            dispatcher.execute(s80Spider);
        }
        executorService.shutdown();
    }
}
