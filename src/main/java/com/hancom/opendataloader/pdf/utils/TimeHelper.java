package com.hancom.opendataloader.pdf.utils;

import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TimeHelper {
    private static final Logger LOGGER = Logger.getLogger(TimeHelper.class.getCanonicalName());
    long javaTime = 0;
    long pythonTime = 0;
    long javaAndPythonTime = 0;
    long startTime;
    
    public TimeHelper() {
        
    }
    
    public void start() {
        startTime = System.nanoTime();
    }
    
    public void endJava() {
        javaTime += System.nanoTime() - startTime;
    }

    public void endJavaAndPython() {
        javaAndPythonTime += System.nanoTime() - startTime;
    }

    public void endPython() {
        pythonTime += System.nanoTime() - startTime;
    }

    public void print(File file) {
        LOGGER.log(Level.WARNING, file.getAbsolutePath() + " java " + javaTime / 1_000_000_000.0 / StaticContainers.getDocument().getNumberOfPages());
        LOGGER.log(Level.WARNING, file.getAbsolutePath() + " python " + pythonTime / 1_000_000_000.0 / StaticContainers.getDocument().getNumberOfPages());
        LOGGER.log(Level.WARNING, file.getAbsolutePath() + " java+python " + javaAndPythonTime / 1_000_000_000.0 / StaticContainers.getDocument().getNumberOfPages());
    }
}
