package com.github.supermoonie;

import static org.junit.Assert.assertTrue;

import okhttp3.HttpUrl;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue() throws MalformedURLException {
        String url = "https://cn1.ruioushang.com/hls/20190801/a8fb440b4560277b8285135f969d5823/1564663153/index.m3u8";
        URL m3u8Url = new URL("https://cn1.ruioushang.com/hls/20190801/a8fb440b4560277b8285135f969d5823/1564663153/index.m3u8");
        System.out.println(url.substring(0, url.indexOf("/", 10)));
        System.out.println(System.getProperty("user.dir") + File.separator);
        assertTrue( true );
    }
}
