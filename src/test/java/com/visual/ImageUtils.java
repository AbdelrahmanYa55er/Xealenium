package com.visual;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;

public final class ImageUtils {
    private ImageUtils(){}

    /**
     * Takes a full-page screenshot by dynamically resizing the window 
     * to the document's maximum scroll height before capturing.
     */
    public static BufferedImage screenshotPage(WebDriver driver) throws Exception {
        try {
            if (driver instanceof JavascriptExecutor) {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                long width = (long) js.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, document.body.offsetWidth, document.documentElement.offsetWidth, document.documentElement.clientWidth);");
                long height = (long) js.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, document.body.offsetHeight, document.documentElement.offsetHeight, document.documentElement.clientHeight);");
                
                // Set window to a large enough size for full capture
                // If it's maximized, setting size might fail in some drivers, so we wrap it
                try {
                    driver.manage().window().setSize(new Dimension((int) width, (int) (height + 100)));
                } catch (Exception e) {
                    // Fallback: stay as is, but try to scroll to top at least
                    js.executeScript("window.scrollTo(0,0);");
                }
            }
        } catch (Exception e) {
            System.err.println("[IMAGE-UTILS] Note: Could not fully resize window: " + e.getMessage());
        }
        
        byte[] bytes = screenshotBytes(driver);
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static byte[] screenshotBytes(WebDriver driver) {
        // Direct cast first (most common case)
        if (driver instanceof TakesScreenshot) {
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        }
        // Unwrap VisualDriver
        if (driver instanceof VisualDriver) {
             return screenshotBytes(((VisualDriver) driver).getWrapped()); // wrapped should be screenshot driver
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
