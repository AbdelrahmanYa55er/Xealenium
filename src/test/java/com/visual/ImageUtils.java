package com.visual;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class ImageUtils {
    private ImageUtils(){}

    /**
     * Takes a stitched full-page screenshot in CSS-pixel coordinates so
     * element metadata lines up with the captured image in both baseline and
     * interactive healing flows.
     */
    public static BufferedImage screenshotPage(WebDriver driver) throws Exception {
        if (!(driver instanceof JavascriptExecutor)) {
            return ImageIO.read(new ByteArrayInputStream(screenshotBytes(driver)));
        }

        JavascriptExecutor js = (JavascriptExecutor) driver;
        long totalWidth = jsLong(js,
            "return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth," +
            " document.body.offsetWidth, document.documentElement.offsetWidth," +
            " document.documentElement.clientWidth, window.innerWidth || 0);");
        long totalHeight = jsLong(js,
            "return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight," +
            " document.body.offsetHeight, document.documentElement.offsetHeight," +
            " document.documentElement.clientHeight, window.innerHeight || 0);");
        long viewportWidth = Math.max(1L, jsLong(js,
            "return Math.max(window.innerWidth || 0, document.documentElement.clientWidth || 0);") );
        long viewportHeight = Math.max(1L, jsLong(js,
            "return Math.max(window.innerHeight || 0, document.documentElement.clientHeight || 0);") );

        if (totalWidth <= viewportWidth && totalHeight <= viewportHeight) {
            return ImageIO.read(new ByteArrayInputStream(screenshotBytes(driver)));
        }

        long originalX = jsLong(js, "return window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || 0;");
        long originalY = jsLong(js, "return window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;");

        List<Integer> xs = tilePositions((int) totalWidth, (int) viewportWidth);
        List<Integer> ys = tilePositions((int) totalHeight, (int) viewportHeight);
        BufferedImage stitched = new BufferedImage((int) totalWidth, (int) totalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = stitched.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        try {
            for (int y : ys) {
                for (int x : xs) {
                    js.executeScript("window.scrollTo(arguments[0], arguments[1]);", x, y);
                    Thread.sleep(80);

                    BufferedImage tile = ImageIO.read(new ByteArrayInputStream(screenshotBytes(driver)));
                    int drawW = Math.min((int) viewportWidth, stitched.getWidth() - x);
                    int drawH = Math.min((int) viewportHeight, stitched.getHeight() - y);
                    if (drawW <= 0 || drawH <= 0) continue;

                    g.drawImage(tile, x, y, x + drawW, y + drawH, 0, 0, tile.getWidth(), tile.getHeight(), null);
                }
            }
        } finally {
            g.dispose();
            js.executeScript("window.scrollTo(arguments[0], arguments[1]);", originalX, originalY);
        }

        return stitched;
    }

    private static List<Integer> tilePositions(int total, int viewport) {
        List<Integer> positions = new ArrayList<>();
        if (total <= 0 || viewport <= 0) {
            positions.add(0);
            return positions;
        }
        for (int pos = 0; pos < total; pos += viewport) {
            positions.add(pos);
        }
        int last = Math.max(0, total - viewport);
        if (positions.isEmpty() || positions.get(positions.size() - 1) != last) {
            positions.add(last);
        }
        return positions;
    }

    private static long jsLong(JavascriptExecutor js, String script) {
        Object value = js.executeScript(script);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Math.round(Double.parseDouble((String) value));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

    private static byte[] screenshotBytes(WebDriver driver) {
        if (driver instanceof TakesScreenshot) {
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        }
        if (driver instanceof VisualDriver) {
             return screenshotBytes(((VisualDriver) driver).getWrapped());
        }
        throw new RuntimeException("Cannot take screenshot from driver: " + driver.getClass().getName());
    }

    public static BufferedImage crop(BufferedImage src,int x,int y,int w,int h){
        int sx=Math.max(0,Math.min(x,src.getWidth()-1));
        int sy=Math.max(0,Math.min(y,src.getHeight()-1));
        int sw=Math.max(1,Math.min(w,src.getWidth()-sx));
        int sh=Math.max(1,Math.min(h,src.getHeight()-sy));
        return src.getSubimage(sx,sy,sw,sh);
    }
    public static String toBase64(BufferedImage img) throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        ImageIO.write(img,"png",b);
        return Base64.getEncoder().encodeToString(b.toByteArray());
    }
    public static BufferedImage fromBase64(String b64) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(b64)));
    }
    public static double templateMatch(BufferedImage cand,BufferedImage tmpl){
        if(cand==null||tmpl==null)return 0.0;
        int tw=64,th=32;
        BufferedImage sc=scale(cand,tw,th),st=scale(tmpl,tw,th);
        double sad=0,max=(double)tw*th*255*3;
        for(int py=0;py<th;py++) for(int px=0;px<tw;px++){
            Color cc=new Color(sc.getRGB(px,py),true),tc=new Color(st.getRGB(px,py),true);
            sad+=Math.abs(cc.getRed()-tc.getRed())+Math.abs(cc.getGreen()-tc.getGreen())
                +Math.abs(cc.getBlue()-tc.getBlue());
        }
        return 1.0-(sad/max);
    }
    public static BufferedImage scale(BufferedImage src,int w,int h){
        BufferedImage o=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src,0,0,w,h,null);g.dispose();return o;
    }
    public static double positionScore(int bx,int by,int bw,int bh,
                                       int cx,int cy,int cw,int ch,double maxD){
        double dx=bx+bw/2.0-cx-cw/2.0,dy=by+bh/2.0-cy-ch/2.0;
        return Math.max(0.0,1.0-Math.sqrt(dx*dx+dy*dy)/maxD);
    }
}
