package studio.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import studio.kdb.Config;
import studio.kdb.Config.ThemeEntry;

public class ThemeDialog extends EscapeDialog {
    private JButton btnImport;
    private JButton btnExport;
    private JButton btnOk;
    private JButton btnCancel;
    private Map<String, JTextField> boxColor = new HashMap();

    private final static int FIELD_SIZE = 150;

    public ThemeDialog(JFrame owner) {
        super(owner, "Theme");
        initComponents();
    }

    @Override
    public void align() {
        super.align();
        btnOk.requestFocusInWindow();
    }

    private void initComponents() {

        btnImport = new JButton("Import...");
        btnExport = new JButton("Export...");
        btnOk = new JButton("OK");
        btnCancel = new JButton("Cancel");

        btnImport.addActionListener(e->importFromJSON());
        btnExport.addActionListener(e->exportToJSON());
        btnOk.addActionListener(e->accept());
        btnCancel.addActionListener(e->cancel());

        JPanel pnlEntries = new JPanel();
        pnlEntries.setLayout(new GridLayout(ThemeEntry.values().length,2));
        for (ThemeEntry entry : ThemeEntry.values()) {
            String name = Config.getThemeEntry(entry);
            JLabel label = new JLabel(name);
            Color color = Config.getInstance().getColor(name);
            JTextField box = new JTextField();
            box.setEditable(false);
            box.setBackground(color);
            box.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    chooseColor(name);
                }
            });
            pnlEntries.add(label);
            pnlEntries.add(box);
            boxColor.put(name, box);
        }


        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pnlButtons.add(btnImport);
        pnlButtons.add(btnExport);
        pnlButtons.add(btnOk);
        pnlButtons.add(btnCancel);

        JLabel backgroundWarning = new JLabel("Background is set per server. The BACKGROUND item only sets the default.");

        JPanel pnlBottom = new JPanel(new GridLayout(2, 1));
        pnlBottom.add(backgroundWarning);
        pnlBottom.add(pnlButtons);

        JPanel root = new JPanel(new BorderLayout());
        root.add(pnlEntries, BorderLayout.CENTER);
        root.add(pnlBottom, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void chooseColor(String name) {
        JTextField box = boxColor.get(name);
        Color color = JColorChooser.showDialog(this, name, box.getBackground());
        if (color != null)
            box.setBackground(color);
    }

    public Color getColor(String name) {
        return boxColor.get(name).getBackground();
    }

    private void fileDialog(String title, boolean isSave, Consumer<File> fileOp) {
        File file = StudioFileChooser.chooseFile(this,
            Config.THEME_FILE_CHOOSER,
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
                JOptionPane.showMessageDialog(this,
                                              "Error in file operation:\n" + e,
                                              "Studio for kdb+",
                                              JOptionPane.ERROR_MESSAGE,
                                              Util.ERROR_ICON);
            }
        }
    }

    private void exportToJSON() {
        fileDialog("Export theme as",
                   true,
                   file->exportThemeToJSON(file));
    }

    private void importFromJSON() {
        fileDialog("Import theme from",
                   false,
                   file->{
                        String errors = importThemeFromJSON(file);
                        if (0<errors.length())
                            JOptionPane.showMessageDialog(this,
                                              "Error while importing theme:\n" + errors,
                                              "Studio for kdb+",
                                              JOptionPane.ERROR_MESSAGE,
                                              Util.ERROR_ICON);
                   });
   }

    public void exportThemeToJSON(File f) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        Map<String,Object> cfg = new LinkedHashMap<>();
        Map<String,Object> theme = new LinkedHashMap<>();
        ArrayList<Map<String,Object>> svs = new ArrayList<>();
        for (String name : boxColor.keySet()) {
            theme.put(name, Config.colorToJSON(boxColor.get(name).getBackground()));
        }
        cfg.put("theme", theme);
        try {
            FileWriter sw = new FileWriter(f);
            objectMapper.writeValue(sw, cfg);
        } catch(IOException e) {
            e.printStackTrace(System.err);
        }
    }

    public String importThemeFromJSON(File f) {
        ObjectMapper objectMapper = new ObjectMapper();
        StringBuilder sb = new StringBuilder();
        try {
            JsonNode root = objectMapper.readTree(f);
            if (!root.isObject()) return "JSON root node is not an object";
            if (!root.has("theme")) return "JSON root node doesn't have a \"theme\" property";
            JsonNode themeNode = root.get("theme");
            if (!themeNode.isObject()) return "\"theme\" node is not an object";
            for (Iterator<String> keyIt = themeNode.fieldNames(); keyIt.hasNext(); ) {
                String key = keyIt.next();
                if (boxColor.containsKey(key)) {
                    JsonNode color = themeNode.get(key);
                    if (!color.isArray() || color.size() != 3) {
                        sb.append("Not an array of length 3: "+key+".");
                    } else {
                        Color col = new Color(color.get(0).asInt(255),color.get(1).asInt(255),color.get(2).asInt(255));
                        boxColor.get(key).setBackground(col);
                    }
                }
            }
        } catch(IOException e) {
            return e.toString();
        }
        return sb.toString();
    }

}
