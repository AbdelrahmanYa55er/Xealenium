package com.visual.semantic;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public interface SemanticProvider {
    SemanticSignals extract(WebDriver driver, WebElement element);
}


