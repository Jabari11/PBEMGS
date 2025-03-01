package com.pbemgs;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class OpenHtml {


    public static void write(String board, String fname) throws IOException {

        // Fix newlines: Convert "\n" to "<br>" for correct rendering
        String htmlContent = "<html><body style='font-family:monospace; white-space:pre;'>" +
                board.replace("\n", "<br>") + "</body></html>";

        // Save to a file
        File file = new File(fname + ".html");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(htmlContent);
        }

        // Open in default web browser
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(file.toURI());
        } else {
            System.out.println("Open the file manually: " + file.getAbsolutePath());
        }
    }
}
