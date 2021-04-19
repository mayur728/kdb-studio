package studio.core;

import java.awt.Font;
import java.util.TimeZone;
import javax.swing.UIManager;
import studio.kdb.Config;
import studio.ui.ExceptionGroup;
import studio.ui.StudioPanel;

public class Studio {
    public static void main(final String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));

        if (System.getProperty("os.name", "").contains("OS X")) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            //     System.setProperty("apple.awt.brushMetalLook", "true");
            System.setProperty("apple.awt.showGrowBox", "true");
            System
                .setProperty("com.apple.mrj.application.apple.menu.about.name", "Studio for kdb+");
            System.setProperty("com.apple.mrj.application.live-resize", "true");
            System.setProperty("com.apple.macos.smallTabs", "true");
            System.setProperty("com.apple.mrj.application.growbox.intrudes", "false");
        }

        if (Config.getInstance().getLookAndFeel() != null) {
            try {
                UIManager.setLookAndFeel(Config.getInstance().getLookAndFeel());
            } catch (Exception ex) {
                // go on with default one
                ex.printStackTrace();
            }
        }

        UIManager.put("Table.font", new javax.swing.plaf.FontUIResource("Monospaced", Font.PLAIN,
            UIManager.getFont("Table.font").getSize()));
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        ThreadGroup exceptionThreadGroup = new ExceptionGroup();

        new Thread(exceptionThreadGroup, "Init thread") {
            public void run() {
                StudioPanel.init(args);
            }
        }.start();

    }
}
