/*
 * MegaMekLab - Copyright (C) 2008
 *
 * Original author - jtighe (torren@users.sourceforge.net)
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 */

package megameklab.com;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;

import javax.swing.UIManager;

import megamek.MegaMek;
import megamek.common.Configuration;
import megamek.common.EquipmentType;
import megamek.common.MechSummaryCache;
import megamek.common.logging.DefaultMmLogger;
import megamek.common.logging.LogLevel;
import megamek.common.logging.MMLogger;
import megamek.common.preference.PreferenceManager;
import megamek.common.util.MegaMekFile;
import megameklab.com.ui.StartupGUI;
import megameklab.com.util.CConfig;
import megameklab.com.util.UnitUtil;

public class MegaMekLab {
    public static final String VERSION = "0.47.3-SNAPSHOT";

    private static final String FILENAME_BT_CLASSIC_FONT = "btclassic/BTLogo_old.ttf"; //$NON-NLS-1$

    private static MMLogger logger = null;

    public static void main(String[] args) {
    	System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name","MegaMekLab");
        redirectOutput();
        //add classic battletech font
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            File btFontFile = new MegaMekFile(Configuration.fontsDir(), FILENAME_BT_CLASSIC_FONT).getFile();
            Font btFont = Font.createFont(Font.TRUETYPE_FONT, btFontFile);
            System.out.println("Loaded Font: " + btFont.getName());
            ge.registerFont(btFont);
        } catch (IOException | FontFormatException e) {
            System.out.println("Error Registering BT Classic Font! Error: " + e.getMessage());
        }
        startup();
    }

    private static void redirectOutput() {
        try {
            System.out.println("Redirecting output to megameklablog.txt"); //$NON-NLS-1$
            File logDir = new File("logs");
            if (!logDir.exists()) {
                logDir.mkdir();
            }
            final String logFilename = "logs" + File.separator + "megameklablog.txt";
            MegaMek.resetLogFile(logFilename);
            PrintStream ps = new PrintStream(
                    new BufferedOutputStream(
                            new FileOutputStream(logFilename,
                                                 true),
                            64));
            System.setOut(ps);
            System.setErr(ps);
        } catch (Exception e) {
            System.err.println("Unable to redirect output to megameklablog.txt"); //$NON-NLS-1$
            e.printStackTrace();
        }
    }

    public static MMLogger getLogger() {
        if (null == logger) {
            logger = DefaultMmLogger.getInstance();
        }
        return logger;
    }
    
    /**
     * Prints some information about MegaMekLab. Used in logfiles to figure out the
     * JVM and version of MegaMekLab.
     */
    private static void showInfo() {
        final String METHOD_NAME = "showInfo";
        final long TIMESTAMP = new File(PreferenceManager
                .getClientPreferences().getLogDirectory()
                + File.separator
                + "timestamp").lastModified();
        // echo some useful stuff
        String msg = "Starting MegaMekLab v" + VERSION + " ..."; //$NON-NLS-1$ //$NON-NLS-2$
        if (TIMESTAMP > 0) {
            msg += "\n\tCompiled on " + new Date(TIMESTAMP).toString(); //$NON-NLS-1$
        }
        msg += "\n\tToday is " + new Date().toString(); //$NON-NLS-1$
        msg += "\n\tJava vendor " + System.getProperty("java.vendor"); //$NON-NLS-1$ //$NON-NLS-2$
        msg += "\n\tJava version " + System.getProperty("java.version"); //$NON-NLS-1$ //$NON-NLS-2$
        msg += "\n\tPlatform " //$NON-NLS-1$
               + System.getProperty("os.name") //$NON-NLS-1$
               + " " //$NON-NLS-1$
               + System.getProperty("os.version") //$NON-NLS-1$
               + " (" //$NON-NLS-1$
               + System.getProperty("os.arch") //$NON-NLS-1$
               + ")"; //$NON-NLS-1$
        long maxMemory = Runtime.getRuntime().maxMemory() / 1024;
        msg += "\n\tTotal memory available to MegaMek: " + NumberFormat.getInstance().format(maxMemory) + " kB"; //$NON-NLS-1$ //$NON-NLS-2$
        getLogger().log(MegaMekLab.class, METHOD_NAME, LogLevel.INFO, msg);
    }
    
    private static void startup() {
        showInfo();
        Locale.setDefault(Locale.US);
        EquipmentType.initializeTypes();
        MechSummaryCache.getInstance();
        new CConfig();
        UnitUtil.loadFonts();
        setLookAndFeel();
        //create a start up frame and display it
        StartupGUI sud = new StartupGUI();
        sud.setVisible(true);
    }
    
    private static void setLookAndFeel() {
        try {
            String plaf = CConfig.getParam(CConfig.CONFIG_PLAF, UIManager.getSystemLookAndFeelClassName());
            UIManager.setLookAndFeel(plaf);
        } catch (Exception e) {
            MegaMekLab.getLogger().error(MegaMekLab.class, "setLookAndFeel()", e);
       }
    }
    
    /**
     * Helper function that calculates the maximum screen width available locally.
     * @return Maximum screen width.
     */
    public static double calculateMaxScreenWidth() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();
        double maxWidth = 0;
        for (int i = 0; i < gs.length; i++) {
            Rectangle b = gs[i].getDefaultConfiguration().getBounds();
            if (b.getWidth() > maxWidth) {   // Update the max size found on this monitor
                maxWidth = b.getWidth();
            }
        }
        
        return maxWidth;
    }
}