package com.github.supermoonie;

import org.apache.commons.io.IOUtils;
import org.tensorflow.Graph;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Created by supermoonie on 2019/8/21
 *
 * @author supermoonie
 * @date 2019/8/21
 */
public class TfTest {

    private static final String[] CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".split("");

    public static void main(String[] args) throws IOException {
        try (Graph graph = new Graph()) {
            //导入图
            byte[] graphBytes = IOUtils.toByteArray(new
                    FileInputStream("D:\\Projects\\Java\\video-download\\src\\main\\resources\\model.pb"));
            graph.importGraphDef(graphBytes);
            BufferedImage image = ImageIO.read(new FileInputStream("D:\\BaiduNetdiskDownload\\20181211\\data\\export\\crawler_captcha\\correct\\2GEY-e957a012d8e1495dad0b80d0150935d7.png"));
            GrayFilter grayFilter = new GrayFilter();
            BufferedImage grayImg = grayFilter.filter(image, null);
            int width = grayImg.getWidth();
            int height = grayImg.getHeight();
            int[] inPixels = new int[width * height];
            grayFilter.getRGB(grayImg, 0, 0, width, height, inPixels);

            //根据图建立Session
            try (Session session = new Session(graph)) {
                //相当于TensorFlow Python中的sess.run(z, feed_dict = {'x': 10.0})
                Tensor<?> tensor = session.runner()
                        .feed("data/Placeholder", Tensor.create(int2Float(inPixels)))
                        .feed("data/Placeholder_2", Tensor.create(1.0f))
                        .fetch("y_prediction/Add").run().get(0);
                long[] shape = tensor.shape();
                float[][] result = new float[(int) shape[0]][(int) shape[1]];
                tensor.copyTo(result);
                float[][] arr_4x62 = reshape(result);
                System.out.println(Arrays.deepToString(arr_4x62));
                int[] max = argMax(arr_4x62);
                System.out.println(Arrays.toString(max));
                String[] text = text(max);
                System.out.println(Arrays.toString(text));
            }
        }
    }

    private static float[] int2Float(int[] in) {
        float[] out = new float[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i] * 1.0f;
        }
        return out;
    }

    private static float[][] reshape(float[][] in) {
        float[] lables = in[0];
        int rowNum = lables.length / CHARS.length;
        float[][] out = new float[rowNum][CHARS.length];
        for (int i = 0; i < lables.length;) {
            if (i % CHARS.length == 0) {
                for (int n = 0; n < CHARS.length; n ++) {
                    out[i/CHARS.length][n] = lables[i + n];
                }
                i += CHARS.length;
            }
        }
        return out;
    }

    private static int[] argMax(float[][] in) {
        int[] out = new int[in.length];
        for (int i = 0; i < in.length; i ++) {
            float max = in[i][0];
            for (int j = 0; j < in[i].length; j ++) {
                if (in[i][j] > max) {
                    out[i] = j;
                    max = in[i][j];
                    System.out.print(in[i][j] + " index: " + j + "\t");
                }
            }
            System.out.println();
        }
        return out;
    }

    private static String[] text(int[] in) {
        String[] out = new String[in.length];
        for (int i = 0; i <in.length; i ++) {
            out[i] = CHARS[in[i]];
        }
        return out;
    }
}
