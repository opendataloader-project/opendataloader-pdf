package com.hancom.opendataloader.pdf.utils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ResourceLoader {
    
    public static File loadResource(String resourcePath) {
        File externalFile = getExternalResource(resourcePath);
        if (externalFile != null && externalFile.exists()) {
            try {
                return externalFile;
            } catch (Exception ignored) {
            }
        }
        return getInternalResource(resourcePath);
    }
    
    private static File getExternalResource(String resourcePath) {
        try {
            String jarDir = getJarDirectory();
            Path externalPath = Paths.get(jarDir, resourcePath);

            File file = externalPath.toFile();
            return file.exists() ? file : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static File getInternalResource(String resourcePath) {
        return new File(ResourceLoader.class.getClassLoader().getResource(resourcePath).getFile());
    }
    
    private static String getJarDirectory() {
        try {
            String classPath = ResourceLoader.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();

            File jarFile = new File(classPath);
            return jarFile.getParent();
        } catch (Exception e) {
            return System.getProperty("user.dir");
        }
    }
}