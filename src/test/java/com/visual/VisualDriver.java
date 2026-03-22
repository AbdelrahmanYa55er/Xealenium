package com.visual;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Interactive;
import org.openqa.selenium.interactions.Sequence;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * VisualDriver: wraps any WebDriver (typically SelfHealingDriver) and adds
 * a visual healing fallback. Accepts a separate screenshotDriver (the raw
 * ChromeDriver) for taking screenshots, bypassing proxy casting issues.
 */
public class VisualDriver implements WebDriver,JavascriptExecutor,TakesScreenshot,Interactive {
    private final WebDriver wrapped;
    private final WebDriver screenshotDriver; // raw ChromeDriver for screenshots
    private final VisualHealingEngine engine;

    /** Uses the wrapped driver itself for screenshots too. */
    public VisualDriver(WebDriver wrapped){
        this.wrapped=wrapped; this.screenshotDriver=wrapped;
        engine=new VisualHealingEngine();
    }
    /** Preferred: pass raw ChromeDriver as screenshotDriver to avoid proxy cast issues. */
    public VisualDriver(WebDriver wrapped, WebDriver screenshotDriver){
        this.wrapped=wrapped; this.screenshotDriver=screenshotDriver;
        engine=new VisualHealingEngine();
    }
    public VisualDriver(WebDriver wrapped, WebDriver screenshotDriver, VisualHealingEngine e){
        this.wrapped=wrapped; this.screenshotDriver=screenshotDriver; engine=e;
    }

    @Override
    public WebElement findElement(By by){
        try{
            WebElement el=wrapped.findElement(by);
            engine.captureBaseline(screenshotDriver,el,by);
            return el;
        }catch(NoSuchElementException nse){
            System.out.println("[VISUAL-DRIVER] Fallback for "+by);
            return fallback(by,nse);
        }
    }
    @Override
    public List<WebElement> findElements(By by){
        try{return wrapped.findElements(by);}catch(Exception e){return List.of();}
    }
    private WebElement fallback(By by,NoSuchElementException orig){
        ScoreResult r=engine.heal(screenshotDriver,by);
        if(r.decision==ScoreResult.Decision.HEALED && r.candidateIndex >= 0){
            try{
                // Retrieve the actual DOM element reference using the index
                WebElement el=(WebElement)((JavascriptExecutor)wrapped).executeScript(
                    "return window.__visualCandidates ? window.__visualCandidates[arguments[0]] : null;", r.candidateIndex);
                if(el!=null){
                    System.out.println("[VISUAL-DRIVER] Recovered "+by+" score="+String.format("%.3f",r.totalScore));
                    return el;
                } else {
                    System.err.println("[VISUAL-DRIVER] Returned candidate was null at index " + r.candidateIndex);
                }
            }catch(Exception e){System.err.println("[VISUAL-DRIVER] Error retrieving JS candidate: "+e.getMessage());}
        }
        System.out.println("[VISUAL-DRIVER] Safety ABORT "+by+" score="+String.format("%.3f",r.totalScore));
        throw orig;
    }
    @Override public void get(String u){wrapped.get(u);}
    @Override public String getCurrentUrl(){return wrapped.getCurrentUrl();}
    @Override public String getTitle(){return wrapped.getTitle();}
    @Override public String getPageSource(){return wrapped.getPageSource();}
    @Override public void close(){wrapped.close();}
    @Override public void quit(){wrapped.quit();}
    @Override public Set<String> getWindowHandles(){return wrapped.getWindowHandles();}
    @Override public String getWindowHandle(){return wrapped.getWindowHandle();}
    @Override public WebDriver.TargetLocator switchTo(){return wrapped.switchTo();}
    @Override public WebDriver.Navigation navigate(){return wrapped.navigate();}
    @Override public WebDriver.Options manage(){return wrapped.manage();}
    @Override public Object executeScript(String s,Object...a){return((JavascriptExecutor)wrapped).executeScript(s,a);}
    @Override public Object executeAsyncScript(String s,Object...a){return((JavascriptExecutor)wrapped).executeAsyncScript(s,a);}
    @Override public <X> X getScreenshotAs(OutputType<X> t)throws WebDriverException{return((TakesScreenshot)screenshotDriver).getScreenshotAs(t);}
    @Override public void perform(Collection<Sequence> a){((Interactive)wrapped).perform(a);}
    @Override public void resetInputState(){((Interactive)wrapped).resetInputState();}
    public WebDriver getWrapped(){return wrapped;}
    public VisualHealingEngine getEngine(){return engine;}
}
