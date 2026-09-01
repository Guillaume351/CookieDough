package com.cookiebuild.cookiedough.utils;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipUtils {

    public static void unzip(File zipFile, File destDir) throws IOException {
        File canonicalDestination = destDir.getCanonicalFile();
        if (!canonicalDestination.exists() && !canonicalDestination.mkdirs()) {
            throw new IOException("Could not create destination directory: " + canonicalDestination);
        }
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryDestination = new File(canonicalDestination, entry.getName()).getCanonicalFile();
                String destinationPrefix = canonicalDestination.getPath() + File.separator;
                if (!entryDestination.getPath().startsWith(destinationPrefix)) {
                    throw new IOException("Blocked zip entry outside destination: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!entryDestination.exists() && !entryDestination.mkdirs()) {
                        throw new IOException("Could not create directory: " + entryDestination);
                    }
                } else {
                    File parent = entryDestination.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Could not create directory: " + parent);
                    }
                    try (InputStream in = zip.getInputStream(entry);
                         OutputStream out = new FileOutputStream(entryDestination)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }
}
