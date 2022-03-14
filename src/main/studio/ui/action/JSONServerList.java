package studio.ui.action;

import java.awt.Component;
import java.io.File;
import java.util.function.Consumer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import studio.kdb.Config;
import studio.ui.StudioFileChooser;
import studio.ui.StudioPanel;
import studio.ui.Util;

public class JSONServerList {

    private static void fileDialog(Component parent, String title, boolean isSave, Consumer<File> fileOp) {
        File file = StudioFileChooser.chooseFile(parent,
            Config.SERVERLIST_FILE_CHOOSER,
            isSave ? JFileChooser.SAVE_DIALOG : JFileChooser.OPEN_DIALOG,
            title,
            null,   //defaultFile
            new FileNameExtensionFilter("JSON file", "json"));

        if (file != null) {
            try {
                fileOp.accept(file);
            }
            catch (Exception e) {
                e.printStackTrace(System.err);
                JOptionPane.showMessageDialog(parent,
                                              "Error in file operation:\n" + e,
                                              "Studio for kdb+",
                                              JOptionPane.ERROR_MESSAGE,
                                              Util.ERROR_ICON);
            }
        }
    }

    public static void exportToJSON(Component parent) {
        fileDialog(parent,
                   "Export server list as",
                   true,
                   file->Config.getInstance().exportServerListToJSON(file));
    }

    public static void importFromJSON(Component parent, StudioPanel studioPanel) {
        fileDialog(parent,
                   "Import server list from",
                   false,
                   file->{
                        String errors = Config.getInstance().importServerListFromJSON(file);
                        if (0<errors.length())
                            JOptionPane.showMessageDialog(parent,
                                              "Error while importing server list:\n" + errors,
                                              "Studio for kdb+",
                                              JOptionPane.ERROR_MESSAGE,
                                              Util.ERROR_ICON);
                        studioPanel.updateServerComboBox();
                   });
   }
}
