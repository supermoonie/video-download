package com.github.supermoonie;

import org.junit.Test;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * Unit test for simple App.
 */
public class AppTest {

    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue() throws IOException {
        String text = "<iframe id=\"fed-play-iframe\" class=\"fed-play-iframe fed-part-full\" frameborder=\"0\" scrolling=\"no\" allowfullscreen=\"true\" data-lock=\"0\" data-pass=\"0\" data-auto=\"1\" data-seek=\"0\" data-code=\"1\" data-trys=\"0\" data-advs=\"0\" data-link=\"\" data-time=\"5\" data-chat=\"0\" data-word=\"SZk\" data-stat=\"1\" data-pars=\"/template/vfed/asset/fed/player.php?id=peer&amp;url=\" data-play=\"https://cn3.download05.com/hls/20190805/97e745627c1b982a1dbf0cd34bff224b/1565009347/index.m3u8\" data-next=\"\"></iframe>";
        Matcher matcher = Pattern.compile(".*(https?://[a-zA-Z0-9./]*)\".*").matcher(text);
        if (matcher.find()) {
            System.out.println(matcher.group(1));
        }
        assertTrue(true);
    }
}
