package studio.ui;

import kx.c;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;
import studio.core.AuthenticationManager;
import studio.core.Credentials;
import studio.core.Studio;
import studio.kdb.*;
import studio.kdb.Config.ThemeEntry;
import studio.qeditor.RSToken;
import studio.qeditor.RSTokenMaker;
import studio.ui.action.QueryResult;
import studio.ui.action.WorkspaceSaver;
import studio.ui.chart.Chart;
import studio.ui.dndtabbedpane.DragEvent;
import studio.ui.dndtabbedpane.DraggableTabbedPane;
import studio.ui.rstextarea.FindReplaceAction;
import studio.ui.rstextarea.RSTextAreaFactory;
import studio.utils.*;
import studio.utils.log4j.EnvConfig;

import javax.swing.FocusManager;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.TableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;

import static javax.swing.JSplitPane.VERTICAL_SPLIT;
import static studio.ui.EscapeDialog.DialogResult.CANCELLED;

public class StudioPanel extends JPanel implements WindowListener {

    private static final Logger log = LogManager.getLogger();
    private static final Action editorUndoAction;
    private static final Action editorRedoAction;
    private static final Action editorCutAction;
    private static final Action editorCopyAction;
    private static final Action editorPasteAction;
    private static final Action editorSelectAllAction;
    private static final Action editorFindAction;
    private static final Action editorReplaceAction;

    static {
        // Action name will be used for text in menu items. Kit's actions have internal names.
        // We will create new actions for menu/toolbar and use kit's actions as action itself.
        editorCopyAction = RSTextAreaFactory.getAction(RSTextAreaFactory.rstaCopyAsStyledTextAction);
        editorCutAction = RSTextAreaFactory.getAction(RSTextAreaFactory.rstaCutAsStyledTextAction);
        editorPasteAction = RSTextAreaFactory.getAction(RSyntaxTextAreaEditorKit.pasteAction);
        editorSelectAllAction = RSTextAreaFactory.getAction(RSyntaxTextAreaEditorKit.selectAllAction);
        editorUndoAction = RSTextAreaFactory.getAction(RSyntaxTextAreaEditorKit.rtaUndoAction);
        editorRedoAction = RSTextAreaFactory.getAction(RSyntaxTextAreaEditorKit.rtaRedoAction);
        editorFindAction = RSTextAreaFactory.getAction(FindReplaceAction.findAction);
        editorReplaceAction = RSTextAreaFactory.getAction(FindReplaceAction.replaceAction);
    }


    private static boolean loading = false;

    private JComboBox<String> comboServer;
    private JTextField txtServer;
    private String exportFilename;
    private String lastQuery = null;
    private JToolBar toolbar;
    private DraggableTabbedPane tabbedEditors;
    private EditorTab editor;
    private JSplitPane splitpane;
    private DraggableTabbedPane tabbedPane;
    private ServerList serverList;
    private UserAction arrangeAllAction;
    private UserAction closeFileAction;
    private UserAction closeTabAction;
    private UserAction cleanAction;
    private UserAction openFileAction;
    private UserAction openInExcel;
    private UserAction codeKxComAction;
    private UserAction serverListAction;
    private UserAction serverHistoryAction;
    private UserAction newWindowAction;
    private UserAction newTabAction;
    private UserAction saveFileAction;
    private UserAction saveAllFilesAction;
    private UserAction saveAsFileAction;
    private UserAction exportAction;
    private UserAction chartAction;
    private Action undoAction;
    private Action redoAction;
    private Action cutAction;
    private Action copyAction;
    private Action pasteAction;
    private Action selectAllAction;
    private Action findAction;
    private Action replaceAction;
    private UserAction stopAction;
    private UserAction executeAction;
    private UserAction executeCurrentLineAction;
    private UserAction refreshAction;
    private UserAction aboutAction;
    private UserAction exitAction;
    private UserAction settingsAction;
    private UserAction themeAction;
    private UserAction toggleDividerOrientationAction;
    private UserAction minMaxDividerAction;
    private UserAction toggleCommaFormatAction;
    private UserAction nextEditorTabAction;
    private UserAction prevEditorTabAction;
    private UserAction[] lineEndingActions;
    private UserAction wordWrapAction;
    private UserAction autoCompleteAction;

    private JFrame frame;

    private static List<StudioPanel> allPanels = new ArrayList<>();

    private final List<Server> serverHistory;

    public final static int menuShortcutKeyMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    private final static Cursor textCursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
    private final static Cursor waitCursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);

    private static final Config CONFIG = Config.getInstance();
    
    public void refreshTitle() {
        if (editor.getTitle() == null) return;

        int count = tabbedEditors.getTabCount();
        for (int index=0; index<count; index++) {
            String oldTitle = tabbedEditors.getTitleAt(index);
            String title = getEditor(index).getTabTitle();
            if (oldTitle!= null && oldTitle.equals(title)) continue;
            tabbedEditors.setTitleAt(index, title);
        }

        if (! loading) {
            Server server = editor.getServer();
            String env = EnvConfig.getEnvironment();
            String frameTitle = editor.getTitle() + (editor.isModified() ? " (not saved) " : "") + (server != null ? " @" + server.toString() : "") + " Studio for kdb+ " + Lm.version + (env == null ? "" : " [" + env + "]");
            if (!frameTitle.equals(frame.getTitle())) {
                frame.setTitle(frameTitle);
            }
        }
    }

    //@TODO: Should we have a generic code which override or remove all our actions from the netbeans JEditorPane
    private void overrideDefaultKeymap(JComponent component, UserAction... actions) {
        for (UserAction action: actions) {
            component.getInputMap().put(action.getKeyStroke(), action.getText());
            component.getActionMap().put(action.getText(), action);
        }
    }

    private void removeFocusChangeKeysForWindows(JComponent component) {
        if (Util.MAC_OS_X) return;

        KeyStroke ctrlTab = KeyStroke.getKeyStroke("ctrl TAB");
        KeyStroke ctrlShiftTab = KeyStroke.getKeyStroke("ctrl shift TAB");

        // Remove ctrl-tab from normal focus traversal
        Set<AWTKeyStroke> forwardKeys = new HashSet<>(component.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
        forwardKeys.remove(ctrlTab);
        component.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, forwardKeys);

        // Remove ctrl-shift-tab from normal focus traversal
        Set<AWTKeyStroke> backwardKeys = new HashSet<>(component.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS));
        backwardKeys.remove(ctrlShiftTab);
        component.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, backwardKeys);
    }

    private void setActionsEnabled(boolean value, Action... actions) {
        for (Action action: actions) {
            if (action != null) {
                action.setEnabled(value);
            }
        }
    }

    public void refreshActionState() {
        RSyntaxTextArea textArea = editor.getTextArea();
        if (textArea == null || tabbedPane == null) {
            setActionsEnabled(false, undoAction, redoAction, stopAction, executeAction,
                    executeCurrentLineAction, refreshAction);
            return;
        }

        undoAction.setEnabled(textArea.canUndo());
        redoAction.setEnabled(textArea.canRedo());

        wordWrapAction.setSelected(CONFIG.getBoolean(Config.RSTA_WORD_WRAP));
        autoCompleteAction.setSelected(CONFIG.getBoolean(Config.RSTA_AUTO_COMPLETE));

        for (LineEnding lineEnding: LineEnding.values() ) {
            lineEndingActions[lineEnding.ordinal()].setSelected(editor.getLineEnding() == lineEnding);
        }

        boolean queryRunning = editor.getQueryExecutor().running();
        stopAction.setEnabled(queryRunning);
        boolean allowParallel = CONFIG.getBoolean(Config.ALLOW_PARALLEL_QUERY);
        executeAction.setEnabled(allowParallel || !queryRunning);
        executeCurrentLineAction.setEnabled(allowParallel || !queryRunning);
        refreshAction.setEnabled(lastQuery != null && !queryRunning);

        TabPanel tab = (TabPanel) tabbedPane.getSelectedComponent();
        if (tab == null) {
            setActionsEnabled(false, exportAction, chartAction, openInExcel, refreshAction);
        } else {
            exportAction.setEnabled(tab.isTable());
            chartAction.setEnabled(tab.getType() == TabPanel.ResultType.TABLE);
            openInExcel.setEnabled(tab.isTable());
            refreshAction.setEnabled(true);
            tab.refreshActionState(queryRunning);
        }
    }

    private void exportAsExcel(final String filename) {
        new ExcelExporter().exportTableX(frame,getSelectedTable(),new File(filename),false);
    }

    private void exportAsDelimited(final TableModel model,final String filename,final char delimiter) {
        UIManager.put("ProgressMonitor.progressText","Studio for kdb+");
        final ProgressMonitor pm = new ProgressMonitor(frame,"Exporting data to " + filename,
                "0% complete",0,100);
        pm.setMillisToDecideToPopup(100);
        pm.setMillisToPopup(100);
        pm.setProgress(0);

        Runnable runner = () -> {
            if (filename != null) {
                String lineSeparator = System.getProperty("line.separator");;

                try (BufferedWriter fw = new BufferedWriter(new FileWriter(filename))) {
                    for(int col = 0; col < model.getColumnCount(); col++) {
                        if (col > 0)
                            fw.write(delimiter);
                        fw.write(model.getColumnName(col));
                    }
                    fw.write(lineSeparator);
                    int maxRow = model.getRowCount();
                    for(int r = 1; r <= maxRow; r++) {
                        for (int col = 0;col < model.getColumnCount();col++) {
                            if (col > 0) fw.write(delimiter);

                            K.KBase o = (K.KBase) model.getValueAt(r - 1,col);
                            if (!o.isNull())
                                fw.write(o.toString(KFormatContext.NO_TYPE));
                        }
                        fw.write(lineSeparator);
                        if (pm.isCanceled()) break;
                        int progress = (100 * r) / maxRow;
                        String note = "" + progress + "% complete";
                        SwingUtilities.invokeLater( () -> {
                            pm.setProgress(progress);
                            pm.setNote(note);
                        } );
                    }

                }
                catch (IOException e) {
                    log.error("Error in writing to file {}", filename, e);
                }
                finally {
                    pm.close();
                }
            }
        };

        Thread t = new Thread(runner,"Exporter");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private void exportAsXml(final TableModel model,final String filename) {
        UIManager.put("ProgressMonitor.progressText","Studio for kdb+");
        final ProgressMonitor pm = new ProgressMonitor(frame,"Exporting data to " + filename,
                "0% complete",0,100);
        pm.setMillisToDecideToPopup(100);
        pm.setMillisToPopup(100);
        pm.setProgress(0);

        Runnable runner = () -> {
            if (filename != null) {
                String lineSeparator = System.getProperty("line.separator");;

                try (BufferedWriter fw = new BufferedWriter(new FileWriter(filename))) {
                    fw.write("<R>");
                    int maxRow = model.getRowCount();
                    fw.write(lineSeparator);

                    String[] columns = new String[model.getColumnCount()];
                    for (int col = 0; col < model.getColumnCount(); col++)
                        columns[col] = model.getColumnName(col);

                    for (int r = 1; r <= maxRow; r++) {
                        fw.write("<r>");
                        for (int col = 0; col < columns.length; col++) {
                            fw.write("<" + columns[col] + ">");

                            K.KBase o = (K.KBase) model.getValueAt(r - 1,col);
                            if (!o.isNull())
                                fw.write(o.toString(KFormatContext.NO_TYPE));

                            fw.write("</" + columns[col] + ">");
                        }
                        fw.write("</r>");
                        fw.write(lineSeparator);

                        if (pm.isCanceled()) break;
                        int progress = (100 * r) / maxRow;
                        String note = "" + progress + "% complete";
                        SwingUtilities.invokeLater(() -> {
                            pm.setProgress(progress);
                            pm.setNote(note);
                        });
                    }
                    fw.write("</R>");
                }
                catch (IOException e) {
                    log.error("Error in writing to file {}", filename, e);
                }
                finally {
                    pm.close();
                }
            }
        };

        Thread t = new Thread(runner, "Exporter");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private void exportAsTxt(String filename) {
        exportAsDelimited(getSelectedTable().getModel(),filename,'\t');
    }

    private void exportAsCSV(String filename) {
        exportAsDelimited(getSelectedTable().getModel(),filename,',');
    }

    private void export() {
        if (getSelectedTable() == null) return;

        File file = StudioFileChooser.chooseFile(this, Config.EXPORT_FILE_CHOOSER, JFileChooser.SAVE_DIALOG, "Export result set as",
                null,
                new FileNameExtensionFilter("csv (Comma delimited)", "csv"),
                new FileNameExtensionFilter("txt (Tab delimited)", "txt"),
                new FileNameExtensionFilter("xml", "xml"),
                new FileNameExtensionFilter("xls (Microsoft Excel)", "xls"));

        if (file == null) return;

        try {
            String filename = file.getAbsolutePath();

            if (filename.endsWith(".xls"))
                exportAsExcel(filename);
            else if (filename.endsWith(".csv"))
                exportAsCSV(filename);
            else if (filename.endsWith(".txt"))
                exportAsTxt(filename);
            else if (filename.endsWith(".xml"))
                exportAsXml(getSelectedTable().getModel(),filename);
            else
                StudioOptionPane.showWarning(this,
                        "You did not specify what format to export the file as.\n Cancelling data export",
                        "Warning");
        } catch (Exception e) {
            StudioOptionPane.showError(this,
                    "An error occurred whilst writing the export file.\n Details are: " + e.getMessage(),
                    "Error");
        }
    }

    public void newFile() {
        if (!checkAndSaveTab(editor)) return;

        editor.setFilename(null);
        editor.init(Content.getEmpty());
    }

    private void openOrSwitchToFile(String filename) {
        if (!Files.exists(Paths.get(filename))) {
            StudioOptionPane.showError(this, "File not found: " + filename, "Error");
            return;
        }
        addToMruFiles(filename);
        for (int i=0; i<tabbedEditors.getTabCount(); ++i) {
            EditorTab tab = getEditor(i);
            String fileName = tab.getFilename();
            if (fileName != null && fileName.equals(filename)) {
                tabbedEditors.setSelectedIndex(i);
                return;
            }
        }
        addTab(editor.getServer(), filename);
    }

    private void openFile() {
        File currFile = null;
        if ( tabbedEditors.getSelectedIndex() != -1) {
            String filename = getEditor(tabbedEditors.getSelectedIndex()).getFilename();
            if (filename != null) currFile = new File(filename);
        }
        File file = StudioFileChooser.chooseFile(this, Config.OPEN_FILE_CHOOSER, JFileChooser.OPEN_DIALOG, null, currFile,
                new FileNameExtensionFilter("q script", "q"));

        if (file == null) return;
        String filename = file.getAbsolutePath();
        openOrSwitchToFile(filename);
    }

    public void loadMRUFile(String filename) {
        openOrSwitchToFile(filename);
    }

    public void addToMruFiles(String filename) {
        if (filename == null)
            return;

        Vector v = new Vector();
        v.add(filename);
        String[] mru = CONFIG.getMRUFiles();
        for (int i = 0;i < mru.length;i++)
            if (!v.contains(mru[i]))
                v.add(mru[i]);
        CONFIG.saveMRUFiles((String[]) v.toArray(new String[0]));
        rebuildMenuBar();
    }

    public boolean loadFile(String filename) {
        Content content = Content.getEmpty();
        try {
            content = FileReaderWriter.read(filename);
            if (content.hasMixedLineEnding()) {
                StudioOptionPane.showMessage(frame, "The file " + filename + " has mixed line endings. Mixed line endings are not supported.\n\n" +
                                "All line endings are set to " + content.getLineEnding() + " style.",
                        "Mixed Line Ending");
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to load file {}", filename, e);
            StudioOptionPane.showError(frame, "Failed to load file "+filename + ".\n" + e.getMessage(),
                    "Error in file load");
        } finally {
            editor.setFilename(filename);
            editor.init(content);
        }
        return false;
    }

    private static boolean saveAsFile(EditorTab editor) {
        String filename = editor.getFilename();
        File file = StudioFileChooser.chooseFile(editor.getPanel(), Config.SAVE_FILE_CHOOSER, JFileChooser.SAVE_DIALOG, "Save script as",
                filename == null ? null : new File(filename),
                new FileNameExtensionFilter("q script", "q"));

        if (file == null) {
            return false;
        }

        filename = file.getAbsolutePath();
        try {
            Paths.get(filename);
        } catch(Exception e) {
            log.error("Failed save file " + filename, e);
            StudioOptionPane.showError(editor.getPanel(), "Failed to save file "+filename + ".\n" + e.getMessage(),
                    "Error in file save");
            return false;
        }
        if (file.exists()) {
            int choice = StudioOptionPane.showYesNoDialog(editor.getPanel(),
                    filename + " already exists.\nOverwrite?",
                    "Overwrite?");

            if (choice != JOptionPane.YES_OPTION)
                return false;
        }

        editor.setFilename(filename);
        return editor.saveFileOnDisk(false);
    }

    private static boolean saveEditor(EditorTab editor) {
        if (editor.getFilename() == null) {
            return saveAsFile(editor);
        } else {
            return editor.saveFileOnDisk(false);
        }
    }

    private void saveAll() {
        for (StudioPanel panel: allPanels) {
            int count = panel.tabbedEditors.getTabCount();
            for (int index=0; index<count; index++) {
                panel.getEditor(index).saveFileOnDisk(false);
            }
        }
    }

    private void arrangeAll() {
        int noWins = allPanels.size();

        Iterator<StudioPanel> panelIterator = allPanels.iterator();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        int noRows = Math.min(noWins, 3);
        int height = screenSize.height / noRows;

        for (int row = 0;row < noRows;row++) {
            int noCols = (noWins / 3);

            if ((row == 0) && ((noWins % 3) > 0))
                noCols++;
            else if ((row == 1) && ((noWins % 3) > 1))
                noCols++;

            int width = screenSize.width / noCols;

            for (int col = 0;col < noCols;col++) {
                StudioPanel panel = panelIterator.next();
                JFrame frame = panel.frame;

                frame.setSize(width,height);
                frame.setLocation(col * width,((noRows - 1) - row) * height);
                ensureDeiconified(frame);
            }
        }
    }

    private void setServer(Server server) {
        if (server == null) return;
        editor.setServer(server);

        if (!loading) {
            CONFIG.addServerToHistory(server);
            serverHistory.add(server);

            refreshTitle();
            rebuildAll();
        }
    }

    private void initActions() {
        StudioPanel thePanel = this;

        cleanAction = UserAction.create("Clean", Util.NEW_DOCUMENT_ICON, "Clean editor script", KeyEvent.VK_N,
                KeyStroke.getKeyStroke(KeyEvent.VK_C, menuShortcutKeyMask | InputEvent.SHIFT_MASK),
                e -> newFile());

        arrangeAllAction = UserAction.create(I18n.getString("ArrangeAll"),
                Util.ARRANGE_WINDOWS_ICON,
                "Arrange all windows on screen",
                KeyEvent.VK_A, KeyStroke.getKeyStroke(KeyEvent.VK_A, menuShortcutKeyMask | InputEvent.SHIFT_MASK),
                e -> arrangeAll());

        minMaxDividerAction = UserAction.create(I18n.getString("MaximizeEditorPane"),
                Util.MAXIMIZE_EDITOR_ICON,
                "Maximize editor pane",
                KeyEvent.VK_M, KeyStroke.getKeyStroke(KeyEvent.VK_M, menuShortcutKeyMask),
                e -> minMaxDivider());

        toggleDividerOrientationAction = UserAction.create(I18n.getString("ToggleDividerOrientation"),
                Util.DIVIDER_ORIENTATION_ICON,
                "Toggle the window divider's orientation", KeyEvent.VK_C, KeyStroke.getKeyStroke(KeyEvent.VK_T, menuShortcutKeyMask),
                e -> toggleDividerOrientation());

        closeTabAction = UserAction.create("Close Tab",
                Util.CLOSE_ONE_ICON,
                "Close current tab", KeyEvent.VK_W,
                KeyStroke.getKeyStroke(KeyEvent.VK_W, menuShortcutKeyMask), e -> closeTab());

        closeFileAction = UserAction.create("Close Window",
                Util.CLOSE_ALL_ICON,
                "Close current window (close all tabs)",
                KeyEvent.VK_C, KeyStroke.getKeyStroke(KeyEvent.VK_W, menuShortcutKeyMask | InputEvent.SHIFT_MASK),
                e -> closePanel());

        openFileAction = UserAction.create(I18n.getString("Open"), Util.OPEN_ICON, "Open a script", KeyEvent.VK_O,
                KeyStroke.getKeyStroke(KeyEvent.VK_O, menuShortcutKeyMask), e -> openFile());

        newWindowAction = UserAction.create(I18n.getString("NewWindow"),
                Util.NEW_WINDOW_ICON,
                "Open a new window",
                KeyEvent.VK_N, KeyStroke.getKeyStroke(KeyEvent.VK_N, menuShortcutKeyMask | InputEvent.SHIFT_MASK),
                e -> new StudioPanel().addTab(editor.getServer(), null) );

        newTabAction = UserAction.create("New Tab", "Open a new tab", KeyEvent.VK_T,
                KeyStroke.getKeyStroke(KeyEvent.VK_N, menuShortcutKeyMask),
                e -> addTab(editor.getServer(), null));

        serverListAction = UserAction.create(I18n.getString("ServerList"), Util.SERVER_TREE_ICON, "Show server list",
                KeyEvent.VK_L, KeyStroke.getKeyStroke(KeyEvent.VK_L, menuShortcutKeyMask | InputEvent.SHIFT_MASK),
                e -> showServerList(false));

        serverHistoryAction = UserAction.create("Server History", null, "Recent selected servers", KeyEvent.VK_R,
                KeyStroke.getKeyStroke(KeyEvent.VK_R, menuShortcutKeyMask | InputEvent.SHIFT_MASK),
                e -> showServerList(true));

        saveFileAction = UserAction.create(I18n.getString("Save"), Util.SAVE_ICON, "Save the script",
                KeyEvent.VK_S, KeyStroke.getKeyStroke(KeyEvent.VK_S, menuShortcutKeyMask),
                e -> saveEditor(editor));

        saveAllFilesAction = UserAction.create("Save All...", "Save all files",
                KeyEvent.VK_L, KeyStroke.getKeyStroke(KeyEvent.VK_S, menuShortcutKeyMask | InputEvent.SHIFT_MASK),
                e -> saveAll());

        saveAsFileAction = UserAction.create(I18n.getString("SaveAs"), Util.SAVE_AS_ICON, "Save script as",
                KeyEvent.VK_A, KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.SHIFT_MASK | InputEvent.ALT_MASK),
                e -> saveAsFile(editor));

        exportAction = UserAction.create(I18n.getString("Export"), Util.EXPORT_ICON, "Export result set",
                KeyEvent.VK_E, KeyStroke.getKeyStroke(KeyEvent.VK_X, menuShortcutKeyMask | InputEvent.SHIFT_MASK),
                e -> export());

        chartAction = UserAction.create(I18n.getString("Chart"), Util.CHART_ICON, "Chart current data set",
                KeyEvent.VK_E, KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuShortcutKeyMask | InputEvent.SHIFT_MASK),
                e -> new Chart((KTableModel) getSelectedTable().getModel()));

        stopAction = UserAction.create(I18n.getString("Stop"), Util.STOP_ICON, "Stop the query",
                KeyEvent.VK_S, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menuShortcutKeyMask | InputEvent.SHIFT_MASK),
                e -> editor.getQueryExecutor().cancel());

        openInExcel = UserAction.create(I18n.getString("OpenInExcel"), Util.EXCEL_ICON, "Open in Excel",
                KeyEvent.VK_O, KeyStroke.getKeyStroke(KeyEvent.VK_O, menuShortcutKeyMask | InputEvent.SHIFT_MASK), e -> {
                    try {
                        File file = File.createTempFile("studioExport", ".xlsx");
                        new ExcelExporter().exportTableX(frame, getSelectedTable(), file, true);
                    } catch (IOException ex) {
                        log.error("Failed to create temporary file", ex);
                        StudioOptionPane.showError(frame, "Failed to Open in Excel " + ex.getMessage(),"Error");
                    }
                });

        executeAction = UserAction.create(I18n.getString("Execute"), Util.EXECUTE_ICON, "Execute the full or highlighted text as a query",
                KeyEvent.VK_E, KeyStroke.getKeyStroke(KeyEvent.VK_E, menuShortcutKeyMask), e -> executeQuery());

        executeCurrentLineAction = UserAction.create(I18n.getString("ExecuteCurrentLine"), Util.EXECUTE_LINE_ICON, "Execute the current line as a query",
                KeyEvent.VK_ENTER, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menuShortcutKeyMask), e -> executeQueryCurrentLine());

        refreshAction = UserAction.create(I18n.getString("Refresh"), Util.REFRESH_ICON, "Refresh the result set",
                KeyEvent.VK_R, KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuShortcutKeyMask | InputEvent.SHIFT_MASK), e -> refreshQuery());

        toggleCommaFormatAction = UserAction.create("Toggle Comma Format", Util. COMMA_ICON, "Add/remove thousands separator in selected result",
                KeyEvent.VK_F, KeyStroke.getKeyStroke(KeyEvent.VK_J, menuShortcutKeyMask),
                e -> {
                    TabPanel tab = (TabPanel) tabbedPane.getSelectedComponent();
                    if (tab != null) tab.toggleCommaFormatting();
                });

        aboutAction = UserAction.create(I18n.getString("About"), Util.ABOUT_ICON, "About Studio for kdb+",
                KeyEvent.VK_E, null, e -> about());

        exitAction = UserAction.create(I18n.getString("Exit"), "Close this window",
                KeyEvent.VK_X, KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.ALT_MASK | InputEvent.SHIFT_MASK),
                e -> quit());

        settingsAction = UserAction.create("Settings",
                Util.SETTINGS_ICON,
                "Settings",
                KeyEvent.VK_S, KeyStroke.getKeyStroke(KeyEvent.VK_S, menuShortcutKeyMask | InputEvent.ALT_MASK),
                e -> settings());

        themeAction = UserAction.create("Theme",
                null,
                "Theme",
                0, KeyStroke.getKeyStroke(KeyEvent.VK_T, menuShortcutKeyMask | InputEvent.SHIFT_MASK),
                e -> themeEditor());

        codeKxComAction = UserAction.create("code.kx.com", Util.CODE_KX_COM_ICON, "Open code.kx.com",
                KeyEvent.VK_C, null, e -> {
                    try {
                        BrowserLaunch.openURL("http://code.kx.com/q/");
                    } catch (Exception ex) {
                        StudioOptionPane.showError("Error attempting to launch web browser:\n" + ex.getLocalizedMessage(), "Error");
                    }
                });

        copyAction = UserAction.create(I18n.getString("Copy"), Util.COPY_ICON, "Copy the selected text to the clipboard",
                KeyEvent.VK_C, KeyStroke.getKeyStroke(KeyEvent.VK_C,menuShortcutKeyMask), editorCopyAction);

        cutAction = UserAction.create(I18n.getString("Cut"), Util.CUT_ICON, "Cut the selected text",
                KeyEvent.VK_T, KeyStroke.getKeyStroke(KeyEvent.VK_X,menuShortcutKeyMask), editorCutAction);

        pasteAction = UserAction.create(I18n.getString("Paste"), Util.PASTE_ICON, "Paste text from the clipboard",
                KeyEvent.VK_P, KeyStroke.getKeyStroke(KeyEvent.VK_V,menuShortcutKeyMask), editorPasteAction);

        findAction = UserAction.create(I18n.getString("Find"), Util.FIND_ICON, "Find text in the document",
                KeyEvent.VK_F, KeyStroke.getKeyStroke(KeyEvent.VK_F,menuShortcutKeyMask), editorFindAction);

        replaceAction = UserAction.create(I18n.getString("Replace"), Util.REPLACE_ICON, "Replace text in the document",
                KeyEvent.VK_R, KeyStroke.getKeyStroke(KeyEvent.VK_R,menuShortcutKeyMask), editorReplaceAction);

        selectAllAction = UserAction.create(I18n.getString("SelectAll"), "Select all text in the document",
                KeyEvent.VK_A, KeyStroke.getKeyStroke(KeyEvent.VK_A,menuShortcutKeyMask), editorSelectAllAction);

        undoAction = UserAction.create(I18n.getString("Undo"), Util.UNDO_ICON, "Undo the last change to the document",
                KeyEvent.VK_U, KeyStroke.getKeyStroke(KeyEvent.VK_Z,menuShortcutKeyMask), editorUndoAction);

        redoAction = UserAction.create(I18n.getString("Redo"), Util.REDO_ICON, "Redo the last change to the document",
                KeyEvent.VK_R, KeyStroke.getKeyStroke(KeyEvent.VK_Y,menuShortcutKeyMask), editorRedoAction);

        nextEditorTabAction = UserAction.create("Next tab",
                "Select next editor tab", KeyEvent.VK_N,
                    Util.MAC_OS_X ? KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, menuShortcutKeyMask | InputEvent.ALT_MASK ) :
                                    KeyStroke.getKeyStroke(KeyEvent.VK_TAB, menuShortcutKeyMask),
                e -> selectNextTab(true));

        prevEditorTabAction = UserAction.create("Previous tab",
                "Select previous editor tab", KeyEvent.VK_P,
                Util.MAC_OS_X ? KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, menuShortcutKeyMask | InputEvent.ALT_MASK ) :
                        KeyStroke.getKeyStroke(KeyEvent.VK_TAB, menuShortcutKeyMask | InputEvent.SHIFT_MASK),
                e -> selectNextTab(false));

        lineEndingActions = new UserAction[LineEnding.values().length];
        for(LineEnding lineEnding: LineEnding.values()) {
            lineEndingActions[lineEnding.ordinal()] = UserAction.create(lineEnding.getDescription(),
                e -> {
                    editor.setLineEnding(lineEnding);
                    refreshActionState();
                } );
        }

        wordWrapAction = UserAction.create("Word wrap", "Word wrap for all tabs",
                KeyEvent.VK_W, KeyStroke.getKeyStroke(KeyEvent.VK_W, menuShortcutKeyMask | InputEvent.SHIFT_MASK),
                e -> toggleWordWrap());

        autoCompleteAction = UserAction.create("Auto complete", "Auto complete suggestions pop up",
                KeyEvent.VK_A, KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.SHIFT_MASK | InputEvent.ALT_MASK),
                e -> toggleAutoComplete(editor));
    }

    private static StudioPanel getActivePanel() {
        Window window = FocusManager.getCurrentManager().getActiveWindow();
        for(StudioPanel panel: allPanels) {
            if (window == panel.frame) return panel;
        }
        return allPanels.get(0);
    }

    public static void settings() {
        StudioPanel activePanel = getActivePanel();
        SettingsDialog dialog = new SettingsDialog(activePanel.frame);
        dialog.alignAndShow();
        if (dialog.getResult() == CANCELLED) return;
        //@TODO Need rework - we are reading from Config inside SettingDialog; while saving happens outside
        String auth = dialog.getDefaultAuthenticationMechanism();
        CONFIG.setDefaultAuthMechanism(auth);
        CONFIG.setDefaultCredentials(auth, new Credentials(dialog.getUser(), dialog.getPassword()));
        CONFIG.setShowServerComboBox(dialog.isShowServerComboBox());
        CONFIG.setResultTabsCount(dialog.getResultTabsCount());
        CONFIG.setMaxCharsInResult(dialog.getMaxCharsInResult());
        CONFIG.setMaxCharsInTableCell(dialog.getMaxCharsInTableCell());
        CONFIG.setDouble(Config.CELL_RIGHT_PADDING, dialog.getCellRightPadding());
        CONFIG.setInt(Config.CELL_MAX_WIDTH, dialog.getCellMaxWidth());
        CONFIG.setExecAllOption(dialog.getExecAllOption());
        CONFIG.setBoolean(Config.SAVE_ON_EXIT, dialog.isSaveOnExit());
        CONFIG.setBoolean(Config.FILE_WATCHER_ENABLED, dialog.isFileWatcherEnabled());
        CONFIG.setBoolean(Config.ALLOW_PARALLEL_QUERY, dialog.isAllowParallelQuery());
        CONFIG.setBoolean(Config.AUTO_SAVE, dialog.isAutoSave());
        CONFIG.setEnum(Config.DEFAULT_LINE_ENDING, dialog.getDefaultLineEnding());

        int maxFractionDigits = dialog.getMaxFractionDigits();
        CONFIG.setInt(Config.MAX_FRACTION_DIGITS, maxFractionDigits);
        //Looks like a hack??
        KFormatContext.setMaxFractionDigits(maxFractionDigits);

        boolean changedEditor = CONFIG.setBoolean(Config.RSTA_ANIMATE_BRACKET_MATCHING, dialog.isAnimateBracketMatching());
        changedEditor |= CONFIG.setBoolean(Config.RSTA_HIGHLIGHT_CURRENT_LINE, dialog.isHighlightCurrentLine());
        changedEditor |= CONFIG.setBoolean(Config.RSTA_WORD_WRAP, dialog.isWordWrap());
        changedEditor |= CONFIG.setBoolean(Config.RSTA_AUTO_COMPLETE, dialog.isAutoComplete());
        changedEditor |= CONFIG.setInt(Config.RSTA_INDENT_SIZE, dialog.getRTSAIndentSize());
        changedEditor |= CONFIG.setBoolean(Config.RSTA_INDENT_USE_TAB, dialog.isRTSAIndentUseTab());
        changedEditor |= CONFIG.setBoolean(Config.RSTA_UNINDENT_CURLY_BRACES, dialog.isRTSAUnindentCurlyBraces());
        Font font = new Font(dialog.getFontName(), Font.PLAIN, dialog.getFontSize());
        changedEditor |= CONFIG.setFont(Config.FONT_EDITOR, font);

        if (changedEditor) {
            refreshEditorsSettings();
        }

        boolean changedResult = CONFIG.setInt(Config.EMULATED_DOUBLE_CLICK_TIMEOUT, dialog.getEmulatedDoubleClickTimeout());
        if (changedResult) {
            refreshResultSettings();
        }

        String lfClass = dialog.getLookAndFeelClassName();
        if (!lfClass.equals(UIManager.getLookAndFeel().getClass().getName())) {
            CONFIG.setLookAndFeel(lfClass);
            StudioOptionPane.showMessage(activePanel.frame, "Look and Feel was changed. " +
                    "New L&F will take effect on the next start up.", "Look and Feel Setting Changed");
        }
        activePanel.rebuildToolbar();
    }

    private static void themeEditor() {
        StudioPanel activePanel = getActivePanel();
        ThemeDialog dialog = new ThemeDialog(activePanel.frame);
        dialog.alignAndShow();
        if (dialog.getResult() == CANCELLED) return;
        boolean changedEditor = false;
        for (ThemeEntry entry : ThemeEntry.values()) {
            String name = CONFIG.getThemeEntry(entry);
            changedEditor |= CONFIG.setColor(name, dialog.getColor(name));
        }

        if (changedEditor) {
            refreshEditorsSettings();
        }

    }

    private void toggleWordWrap() {
        boolean value = CONFIG.getBoolean(Config.RSTA_WORD_WRAP);
        CONFIG.setBoolean(Config.RSTA_WORD_WRAP, !value);
        refreshEditorsSettings();
        refreshActionState();
        rebuildAll();
    }

    private void toggleAutoComplete(EditorTab editor) {
        RSyntaxTextArea textArea = editor.getPane().getTextArea();
        boolean value = CONFIG.getBoolean(Config.RSTA_AUTO_COMPLETE);

        CONFIG.setBoolean(Config.RSTA_AUTO_COMPLETE, !value);

        if(CONFIG.getBoolean(Config.RSTA_AUTO_COMPLETE))
        {
            editor.getPane().addDocumentListenersAndTriggerAutoComplete(textArea);
        }
        refreshEditorsSettings();
        refreshActionState();
        rebuildAll();
    }

    private static void refreshEditorsSettings() {
        Font font = CONFIG.getFont(Config.FONT_EDITOR);
        for (StudioPanel panel: allPanels) {
            int count = panel.tabbedEditors.getTabCount();
            for (int index=0; index<count; index++) {
                EditorTab editorTab = panel.getEditor(index);
                RSyntaxTextArea textArea = editorTab.getTextArea();
                textArea.setHighlightCurrentLine(CONFIG.getBoolean(Config.RSTA_HIGHLIGHT_CURRENT_LINE));
                textArea.setAnimateBracketMatching(CONFIG.getBoolean(Config.RSTA_ANIMATE_BRACKET_MATCHING));
                textArea.setLineWrap(CONFIG.getBoolean(Config.RSTA_WORD_WRAP));
                textArea.setTabSize(CONFIG.getInt(Config.RSTA_INDENT_SIZE));
                textArea.setTabsEmulated(!CONFIG.getBoolean(Config.RSTA_INDENT_USE_TAB));
                textArea.setSyntaxScheme(RSToken.getDefaulSyntaxScheme());
                editorTab.setTextAreaFont(font);
                if(CONFIG.getBoolean(Config.RSTA_AUTO_COMPLETE))
                {
                    editorTab.getPane().addDocumentListenersAndTriggerAutoComplete(editorTab.getPane().getTextArea());
                }
            }
        }
        RSTokenMaker.setUnindentCurlyBraces(CONFIG.getBoolean(Config.RSTA_UNINDENT_CURLY_BRACES));
    }

    private static void refreshResultSettings() {
        long doubleClickTimeout = CONFIG.getInt(Config.EMULATED_DOUBLE_CLICK_TIMEOUT);
        for (StudioPanel panel: allPanels) {
            int count = panel.tabbedPane.getTabCount();
            for (int index=0; index<count; index++) {
                panel.getResultPane(index).setDoubleClickTimeout(doubleClickTimeout);
            }
        }
    }

    public static StudioPanel[] getPanels() {
        return allPanels.toArray(new StudioPanel[0]);
    }

    public static void about() {
        StudioPanel activePanel = getActivePanel();
        HelpDialog help = new HelpDialog(activePanel.frame);
        Util.centerChildOnParent(help,activePanel.frame);
        // help.setTitle("About Studio for kdb+");
        help.pack();
        help.setVisible(true);
    }

    public static boolean quit() {
        WorkspaceSaver.setEnabled(false);
        try {
            if (CONFIG.getBoolean(Config.SAVE_ON_EXIT)) {
                for (StudioPanel panel : allPanels.toArray(new StudioPanel[0])) {
                    panel.getFrame().toFront();
                    JTabbedPane tabbedEditors = panel.tabbedEditors;
                    int selectedTab = tabbedEditors.getSelectedIndex();
                    try {
                        int count = tabbedEditors.getTabCount();
                        for (int index = 0; index < count; index++) {
                            EditorTab editor = panel.getEditor(index);
                            if (editor.isModified()) {
                                tabbedEditors.setSelectedIndex(index);
                                if (!checkAndSaveTab(editor)) {
                                    return false;
                                }
                            }
                        }
                    } finally {
                        tabbedEditors.setSelectedIndex(selectedTab);
                    }
                }
            }
        } finally {
            if (allPanels.size() > 0) {
                getActivePanel().frame.toFront();
            }
            WorkspaceSaver.setEnabled(true);
        }
        WorkspaceSaver.save(getWorkspace());
        log.info("Shutting down");
        System.exit(0);
        return true;
    }

    private boolean closePanel() {
        // If this is the last window, we need to properly persist workspace
        if (allPanels.size() == 1) return quit();

        while (tabbedEditors.getTabCount() > 0) {
            if (! closeTab()) return false;
        }
        //closing the last tab would dispose the frame
        return true;
    }

    private static boolean checkAndSaveTab(EditorTab editor) {
        if (! editor.isModified()) return true;

        int choice = StudioOptionPane.showYesNoCancelDialog(editor.getPane(),
                editor.getTitle() + " is changed. Save changes?","Save changes?");

        if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) return false;

        if (choice == JOptionPane.YES_OPTION) {
            return saveEditor(editor);
        }

        return true;
    }

    private void closeFrame() {
        frame.dispose();
        allPanels.remove(this);
        rebuildAll();
    }

    private void closeIfEmpty() {
        if (tabbedEditors.getTabCount()>0) return;
        closeFrame();
    }

    private boolean closeTab() {
        if (!checkAndSaveTab(editor)) return false;

        getEditor(tabbedEditors.getSelectedIndex()).stopFileWatching();

        if (tabbedEditors.getTabCount() == 1 && allPanels.size() == 1) {
            WorkspaceSaver.save(getWorkspace());
            log.info("Closed the last tab. Shutting down");
            System.exit(0);
            return true;
        }

        tabbedEditors.remove(tabbedEditors.getSelectedIndex());
        closeIfEmpty();
        return true;
    }

    public static void rebuildAll() {
        for (StudioPanel panel: allPanels) {
            panel.rebuildMenuAndTooblar();
        }
    }

    private void rebuildMenuAndTooblar() {
        rebuildMenuBar();
        rebuildToolbar();
    }

    private void rebuildMenuBar() {
        if (loading) return;

        JMenuBar menubar = createMenuBar();
        frame.setJMenuBar(menubar);
        menubar.validate();
        menubar.repaint();
        frame.validate();
        frame.repaint();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menubar = new JMenuBar();
        JMenu menu = new JMenu(I18n.getString("File"));
        menu.setMnemonic(KeyEvent.VK_F);
        menu.add(new JMenuItem(newWindowAction));
        menu.add(new JMenuItem(newTabAction));
        menu.add(new JMenuItem(openFileAction));
        menu.add(new JMenuItem(saveFileAction));
        menu.add(new JMenuItem(saveAsFileAction));
        menu.add(new JMenuItem(saveAllFilesAction));

        menu.add(new JMenuItem(closeTabAction));
        menu.add(new JMenuItem(closeFileAction));

        if (!Studio.hasMacOSSystemMenu()) {
            menu.add(new JMenuItem(settingsAction));
            menu.add(new JMenuItem(themeAction));
        }
        menu.addSeparator();
//        menu.add(new JMenuItem(importAction));
        menu.add(new JMenuItem(openInExcel));
        menu.addSeparator();
        menu.add(new JMenuItem(exportAction));
        menu.addSeparator();
        menu.add(new JMenuItem(chartAction));

        String[] mru = CONFIG.getMRUFiles();

        if (mru.length > 0) {
            menu.addSeparator();
            char[] mnems = "123456789".toCharArray();

            for (int i = 0;i < (mru.length > mnems.length ? mnems.length : mru.length);i++) {
                final String filename = mru[i];

                JMenuItem item = new JMenuItem("" + (i + 1) + " " + filename);
                item.setMnemonic(mnems[i]);
                item.addActionListener(new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        loadMRUFile(filename);
                    }
                });
                menu.add(item);
            }
        }

        if (!Studio.hasMacOSSystemMenu()) {
            menu.addSeparator();
            menu.add(new JMenuItem(exitAction));
        }
        menubar.add(menu);

        menu = new JMenu(I18n.getString("Edit"));
        menu.setMnemonic(KeyEvent.VK_E);
        menu.add(new JMenuItem(undoAction));
        menu.add(new JMenuItem(redoAction));
        menu.addSeparator();
        menu.add(new JMenuItem(cutAction));
        menu.add(new JMenuItem(copyAction));
        menu.add(new JMenuItem(pasteAction));
        menu.addSeparator();

        menu.add(new JCheckBoxMenuItem(wordWrapAction));
        menu.add(new JCheckBoxMenuItem(autoCompleteAction));

        JMenu lineEndingSubMenu = new JMenu("Line Ending");
        for (Action action: lineEndingActions) {
            lineEndingSubMenu.add(new JCheckBoxMenuItem(action));
        }
        menu.add(lineEndingSubMenu);

        menu.add(new JMenuItem(cleanAction));
        menu.add(new JMenuItem(selectAllAction));
        menu.addSeparator();
        menu.add(new JMenuItem(findAction));
        menu.add(new JMenuItem(replaceAction));
        menubar.add(menu);

        menu = new JMenu(I18n.getString("Server"));
        menu.setMnemonic(KeyEvent.VK_S);

        menu.add(new JMenuItem(serverListAction));
        menu.add(new JMenuItem(serverHistoryAction));

        menubar.add(menu);

        menu = new JMenu(I18n.getString("Query"));
        menu.setMnemonic(KeyEvent.VK_Q);
        menu.add(new JMenuItem(executeCurrentLineAction));
        menu.add(new JMenuItem(executeAction));
        menu.add(new JMenuItem(stopAction));
        menu.add(new JMenuItem(refreshAction));
        menu.add(new JMenuItem(toggleCommaFormatAction));
        menubar.add(menu);

        menu = new JMenu(I18n.getString("Window"));
        menu.setMnemonic(KeyEvent.VK_W);

        menu.add(new JMenuItem(minMaxDividerAction));
        menu.add(new JMenuItem(toggleDividerOrientationAction));
        menu.add(new JMenuItem(arrangeAllAction));
        menu.add(new JMenuItem(nextEditorTabAction));
        menu.add(new JMenuItem(prevEditorTabAction));

        if (allPanels.size() > 0) {
            menu.addSeparator();

            int i = 0;
            for (StudioPanel panel: allPanels) {
                EditorTab editor = panel.editor;
                String t = "unknown";
                String filename = editor.getFilename();

                if (filename != null)
                    t = filename.replace('\\','/');

                if (editor.getServer() != null)
                    t = t + "[" + editor.getServer().getFullName() + "]";
                else
                    t = t + "[no server]";

                JMenuItem item = new JMenuItem("" + (i + 1) + " " + t);
                item.addActionListener(new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        ensureDeiconified(panel.frame);
                    }
                });

                if (panel == this)
                    item.setIcon(Util.CHECK_ICON);
                else
                    item.setIcon(null);

                menu.add(item);
                i++;
            }
        }
        menubar.add(menu);
        menu = new JMenu(I18n.getString("Help"));
        menu.setMnemonic(KeyEvent.VK_H);
        menu.add(new JMenuItem(codeKxComAction));
        if (!Studio.hasMacOSSystemMenu())
            menu.add(new JMenuItem(aboutAction));
        menubar.add(menu);

        return menubar;
    }

    private void ensureDeiconified(JFrame f) {
        int state = f.getExtendedState();
        state = state & ~Frame.ICONIFIED;
        f.setExtendedState(state);
        f.show();
    }

    private void selectConnectionString() {
        String connection = txtServer.getText().trim();
        if (connection.length() == 0) return;
        Server server = editor.getServer();
        if (server != null && server.getConnectionString().equals(connection)) return;

        try {
            setServer(CONFIG.getServerByConnectionString(connection));

            rebuildToolbar();
            toolbar.validate();
            toolbar.repaint();
        } catch (IllegalArgumentException e) {
            refreshConnection();
        }
    }

    private void showServerList(boolean selectHistory) {
        if (serverList == null) {
            serverList = new ServerList(frame, this);
        }
        Rectangle bounds = Config.getInstance().getBounds(Config.SERVER_LIST_BOUNDS);
        serverList.setBounds(bounds);

        serverList.updateServerTree(CONFIG.getServerTree(), editor.getServer());
        serverList.updateServerHistory(serverHistory);
        serverList.selectHistoryTab(selectHistory);
        serverList.setVisible(true);

        bounds = serverList.getBounds();
        CONFIG.setBounds(Config.SERVER_LIST_BOUNDS, bounds);

        Server selectedServer = serverList.getSelectedServer();
        if (selectedServer == null || selectedServer.equals(editor.getServer())) return;

        setServer(selectedServer);
        rebuildToolbar();
    }


    private void selectServerName() {
        if (comboServer.getSelectedItem() == null) return;
        String selection = comboServer.getSelectedItem().toString();
        if(! CONFIG.getServerNames().contains(selection)) return;

        setServer(CONFIG.getServer(selection));
        rebuildToolbar();
        toolbar.validate();
        toolbar.repaint();
    }

    private void refreshConnection() {
        Server server = editor.getServer();
        if (server == null) {
            txtServer.setText("");
            txtServer.setToolTipText("Select connection details");
        } else {
            txtServer.setText(server.getConnectionString());
            txtServer.setToolTipText(server.getConnectionStringWithPwd());
        }
    }

    private void toolbarAddServerSelection() {
        Server server = editor.getServer();
        Collection<String> names = CONFIG.getServerNames();
        String name = server == null ? "" : server.getFullName();
        if (!names.contains(name)) {
            List<String> newNames = new ArrayList<>();
            newNames.add(name);
            newNames.addAll(names);
            names = newNames;
        }
        comboServer = new JComboBox<>(names.toArray(new String[0]));
        comboServer.setToolTipText("Select the server context");
        comboServer.setSelectedItem(name);
        comboServer.addActionListener(e->selectServerName());
        // Cut the width if it is too wide.
        comboServer.setMinimumSize(new Dimension(0, 0));
        comboServer.setVisible(CONFIG.isShowServerComboBox());

        txtServer = new JTextField(32);
        txtServer.addActionListener(e -> {
            selectConnectionString();
            editor.getTextArea().requestFocus();
        });
        txtServer.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                selectConnectionString();
            }
        });
        refreshConnection();

        toolbar.add(new JLabel(I18n.getString("Server")));
        toolbar.add(comboServer);
        toolbar.add(txtServer);
        toolbar.add(serverListAction);
        toolbar.addSeparator();
    }

    private void rebuildToolbar() {
        if (loading) return;

        if (toolbar != null) {
            toolbar.removeAll();
            toolbarAddServerSelection();

            toolbar.add(stopAction);
            toolbar.add(executeAction);
            toolbar.add(refreshAction);
            toolbar.addSeparator();

            toolbar.add(openFileAction);
            toolbar.add(saveFileAction);
            toolbar.add(saveAsFileAction);
            toolbar.addSeparator();
            toolbar.add(openInExcel);
            toolbar.addSeparator();
            toolbar.add(exportAction);
            toolbar.addSeparator();

            toolbar.add(chartAction);
            toolbar.addSeparator();

            toolbar.add(undoAction).setName("undo");
            toolbar.add(redoAction).setName("redo");
            toolbar.addSeparator();

            toolbar.add(cutAction);
            toolbar.add(copyAction);
            toolbar.add(pasteAction);

            toolbar.addSeparator();
            toolbar.add(findAction);

            toolbar.add(replaceAction);
            toolbar.addSeparator();
            toolbar.add(codeKxComAction);

            for (int j = 0;j < toolbar.getComponentCount();j++) {
                Component c = toolbar.getComponentAtIndex(j);

                if (c instanceof JButton) {
                    JButton btn = (JButton)c;
                    btn.setRequestFocusEnabled(false);
                    btn.setMnemonic(KeyEvent.VK_UNDEFINED);
                }
            }
            refreshActionState();
        }
    }

    private int dividerLastPosition; // updated from property change listener
    private void minMaxDivider(){
        //BasicSplitPaneDivider divider = ((BasicSplitPaneUI)splitpane.getUI()).getDivider();
        //((JButton)divider.getComponent(0)).doClick();
        //((JButton)divider.getComponent(1)).doClick();
        if(splitpane.getDividerLocation()>=splitpane.getMaximumDividerLocation()){
            // Minimize editor pane
            splitpane.getTopComponent().setMinimumSize(new Dimension());
            splitpane.getBottomComponent().setMinimumSize(null);
            splitpane.setDividerLocation(0.);
            splitpane.setResizeWeight(0.);
        }
        else if(splitpane.getDividerLocation()<=splitpane.getMinimumDividerLocation()){
            // Restore editor pane
            splitpane.getTopComponent().setMinimumSize(null);
            splitpane.getBottomComponent().setMinimumSize(null);
            splitpane.setResizeWeight(0.);
            // Could probably catch resize edge-cases etc in pce too
            if(dividerLastPosition>=splitpane.getMaximumDividerLocation()||dividerLastPosition<=splitpane.getMinimumDividerLocation())
                dividerLastPosition=splitpane.getMaximumDividerLocation()/2;
            splitpane.setDividerLocation(dividerLastPosition);
        }
        else{
            // Maximize editor pane
            splitpane.getBottomComponent().setMinimumSize(new Dimension());
            splitpane.getTopComponent().setMinimumSize(null);
            splitpane.setDividerLocation(splitpane.getOrientation()==VERTICAL_SPLIT?splitpane.getHeight()-splitpane.getDividerSize():splitpane.getWidth()-splitpane.getDividerSize());
            splitpane.setResizeWeight(1.);
        }
    }

    private void toggleDividerOrientation() {
        if (splitpane.getOrientation() == JSplitPane.VERTICAL_SPLIT) {
            splitpane.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
            tabbedPane.setTabPlacement(JTabbedPane.LEFT);
        } else {
            splitpane.setOrientation(JSplitPane.VERTICAL_SPLIT);
            tabbedPane.setTabPlacement(JTabbedPane.TOP);
        }

        int count = tabbedPane.getTabCount();
        for (int index = 0; index<count; index++) {
            ((TabPanel)tabbedPane.getComponent(index)).updateToolbarLocation(tabbedPane);
        }

        splitpane.setDividerLocation(0.5);
    }

    private void selectNextTab(boolean forward) {
        int index = tabbedEditors.getSelectedIndex();
        int count = tabbedEditors.getTabCount();
        if (forward) {
            index++;
            if (index == count) index = 0;
        } else {
            index--;
            if (index == -1) index = count-1;
        }
        tabbedEditors.setSelectedIndex(index);
    }

    public EditorTab addTab(Server server, String filename) {
        editor = new EditorTab(this);
        JComponent editorPane = editor.getPane();
        JTextComponent textArea = editor.getTextArea();
        removeFocusChangeKeysForWindows(textArea);

        overrideDefaultKeymap(textArea, toggleCommaFormatAction, newTabAction, closeTabAction, nextEditorTabAction, prevEditorTabAction);
        editorPane.putClientProperty(EditorTab.class, editor);
        tabbedEditors.add(editorPane);
        tabbedEditors.setSelectedIndex(tabbedEditors.getTabCount()-1);
        setServer(server);

        if (filename != null) {
            loadFile(filename);
        } else {
            editor.setFilename(null);
            editor.init(Content.getEmpty());
        }
        textArea.getDocument().addDocumentListener(new MarkingDocumentListener(editor));
        textArea.requestFocus();
        refreshActionState();
        return editor;
    }

    private void refreshEditor() {
        if ( tabbedEditors.getSelectedIndex() == -1) return;
        editor = getEditor(tabbedEditors.getSelectedIndex());
        editor.setPanel(this);
        setServer(editor.getServer());
        lastQuery = null;
        refreshTitle();
        refreshActionState();
        editor.getPane().getTextArea().requestFocus();  //without this, the search box would always get focus if visible
    }

    private void resultTabDragged(DragEvent event) {
        DraggableTabbedPane targetPane = event.getTargetPane();
        StudioPanel targetPanel = (StudioPanel) targetPane.getClientProperty(StudioPanel.class);
        ((TabPanel)targetPane.getComponentAt(event.getTargetIndex())).setPanel(targetPanel);
    }

    public StudioPanel() {
        initActions();
        serverHistory = new HistoricalList<>(CONFIG.getServerHistoryDepth(),
                CONFIG.getServerHistory());

        splitpane = new JSplitPane();
        frame = new JFrame();
        allPanels.add(this);

        tabbedEditors = new DraggableTabbedPane("Editor");
        tabbedEditors.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F,menuShortcutKeyMask), editorFindAction);
        removeFocusChangeKeysForWindows(tabbedEditors);
        ClosableTabbedPane.makeCloseable(tabbedEditors, index -> {
            tabbedEditors.setSelectedIndex(index);
            return closeTab();
        });
        tabbedEditors.addChangeListener(e -> refreshEditor() );
        tabbedEditors.addContainerListener(new ContainerListener() {
            @Override
            public void componentAdded(ContainerEvent e) {
                refreshEditor();
            }
            @Override
            public void componentRemoved(ContainerEvent e) {
                refreshEditor();
            }
        });
        tabbedEditors.addDragCompleteListener(success -> closeIfEmpty() );
        splitpane.setTopComponent(tabbedEditors);
        splitpane.setDividerLocation(0.5);

        toolbar = new JToolBar();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
        toolbar.setFloatable(false);


        tabbedPane = new DraggableTabbedPane("Result", JTabbedPane.TOP);
        ClosableTabbedPane.makeCloseable(tabbedPane, index -> {
            tabbedPane.removeTabAt(index);
            return true;
        });
        tabbedPane.addChangeListener(e->refreshActionState());
        tabbedPane.putClientProperty(StudioPanel.class, this);
        tabbedPane.addDragListener( evt -> resultTabDragged(evt));

        splitpane.setBottomComponent(tabbedPane);
        splitpane.setOneTouchExpandable(true);
        splitpane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        try {
            Component divider = ((BasicSplitPaneUI) splitpane.getUI()).getDivider();

            divider.addMouseListener(new MouseAdapter() {

                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() == 2)
                        toggleDividerOrientation();
                }
            });
        }
        catch (ClassCastException e) {
        }
        splitpane.setContinuousLayout(true);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        frame.getContentPane().add(toolbar,BorderLayout.NORTH);
        frame.getContentPane().add(splitpane,BorderLayout.CENTER);

        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(this);
        frame.setSize((int) (0.8 * screenSize.width),
                (int) (0.8 * screenSize.height));

        frame.setLocation(((int) Math.max(0,(screenSize.width - frame.getWidth()) / 2.0)),
                (int) (Math.max(0,(screenSize.height - frame.getHeight()) / 2.0)));

        if (Util.LOGO_ICON != null)
            frame.setIconImage(Util.LOGO_ICON.getImage());

        frame.setVisible(true);
        splitpane.setDividerLocation(0.5);

        splitpane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,new PropertyChangeListener(){
            public void propertyChange(PropertyChangeEvent pce){
                String s=splitpane.getDividerLocation()>=splitpane.getMaximumDividerLocation()?I18n.getString("MinimizeEditorPane"):splitpane.getDividerLocation()<=splitpane.getMinimumDividerLocation()?I18n.getString("RestoreEditorPane"):I18n.getString("MaximizeEditorPane");
                minMaxDividerAction.putValue(Action.SHORT_DESCRIPTION,s);
                minMaxDividerAction.putValue(Action.NAME,s);
                if(splitpane.getDividerLocation()<splitpane.getMaximumDividerLocation()&&splitpane.getDividerLocation()>splitpane.getMinimumDividerLocation())
                    dividerLastPosition=splitpane.getDividerLocation();
            }
        });
        dividerLastPosition=splitpane.getDividerLocation();
    }

    private static Server getServer(Workspace.Tab tab) {
        Server server = null;
        String serverFullname = tab.getServerFullName();
        if (serverFullname != null) {
            server = CONFIG.getServer(serverFullname);
        }
        if (server != null) return server;

        String connectionString = tab.getServerConnection();
        if (connectionString != null) {
            server = CONFIG.getServerByConnectionString(connectionString);
        }
        if (server == null) server = new Server();

        String auth = tab.getServerAuth();
        if (auth == null) return server;

        if (AuthenticationManager.getInstance().lookup(auth) != null) {
            server.setAuthenticationMechanism(auth);
        }
        return server;
    }

    public static void loadWorkspace(Workspace workspace) {
        loading = true;
        for (Workspace.Window window: workspace.getWindows()) {
            Workspace.Tab[] tabs = window.getTabs();
            if (tabs.length == 0) {
                log.info("Strange: a window has zero tabs. Skipping initialization");
                continue;
            }

            StudioPanel panel = new StudioPanel();
            for (Workspace.Tab tab: tabs) {
                try {
                    EditorTab editor = panel.addTab(getServer(tab), tab.getFilename());
                    editor.init(Content.newContent(tab.getContent(), tab.getLineEnding()));
                    editor.setModified(tab.isModified());
                    int caretPosition = tab.getCaret();
                    if (caretPosition >= 0 && caretPosition < editor.getTextArea().getDocument().getLength()) {
                        editor.getTextArea().setCaretPosition(caretPosition);
                    }
                    editor.getTextArea().discardAllEdits();
                } catch (RuntimeException e) {
                    log.error("Failed to init tab with filename {}", tab.getFilename(), e);
                }
            }
            if (window.getSelectedTab() != -1) {
                panel.tabbedEditors.setSelectedIndex(window.getSelectedTab());
            }
        }

        if (workspace.getSelectedWindow() != -1) {
            allPanels.get(workspace.getSelectedWindow()).frame.toFront();
        }

        loading = false;
        for (StudioPanel panel: allPanels) {
            panel.refreshTitle();
        }
        rebuildAll();
    }

    public void refreshQuery() {
        executeK4Query(lastQuery);
    }

    public void executeQueryCurrentLine() {
        executeQuery(getCurrentLineEditorText(editor.getTextArea()));
    }

    public void executeQuery() {
        executeQuery(getEditorText(editor.getTextArea()));
    }

    private void executeQuery(String text) {
        if (text == null) {
            return;
        }
        text = text.trim();
        if (text.length() == 0) {
            log.info("Nothing to execute - got empty string");
            return;
        }

        executeK4Query(text);
        lastQuery = text;
    }

    public JFrame getFrame() {
        return frame;
    }

    public Server getServer() {
        return editor.getServer();
    }

    private String getEditorText(JTextComponent editor) {
        String text = editor.getSelectedText();
        if (text != null) return text;

        Config.ExecAllOption option = CONFIG.getExecAllOption();
        if (option == Config.ExecAllOption.Ignore) {
            log.info("Nothing is selected. Ignore execution of the whole script");
            return null;
        }
        if (option == Config.ExecAllOption.Execute) {
            return editor.getText();
        }

        int result = StudioOptionPane.showYesNoDialog(frame, "Nothing is selected. Execute the whole script?",
                "Execute All?");

        if (result == JOptionPane.YES_OPTION ) {
            return editor.getText();
        }

        return null;
    }

    private String getCurrentLineEditorText(JTextComponent editor) {
        String newLine = "\n";
        String text = null;

        try {
            int pos = editor.getCaretPosition();
            int max = editor.getDocument().getLength();


            if ((max > pos) && (!editor.getText(pos,1).equals("\n"))) {
                String toeol = editor.getText(pos,max - pos);
                int eol = toeol.indexOf('\n');

                if (eol > 0)
                    pos = pos + eol;
                else
                    pos = max;
            }

            text = editor.getText(0,pos);

            int lrPos = text.lastIndexOf(newLine);

            if (lrPos >= 0) {
                lrPos += newLine.length(); // found it so skip it
                text = text.substring(lrPos,pos).trim();
            }
        }
        catch (BadLocationException e) {
        }

        if (text != null) {
            text = text.trim();

            if (text.length() == 0)
                text = null;
        }

        return text;
    }

    private JTable getSelectedTable() {
        TabPanel tab = (TabPanel) tabbedPane.getSelectedComponent();
        if (tab == null) return null;
        return tab.getTable();
    }

    private void executeK4Query(String text) {
        editor.getTextArea().setCursor(waitCursor);
        editor.setStatus("Executing: " + text);
        editor.getQueryExecutor().execute(text);
        refreshActionState();
    }

    void executeK4Query(K.KBase query) {
        editor.getTextArea().setCursor(waitCursor);
        editor.setStatus("Executing: " + query.toString());
        editor.getQueryExecutor().execute(query);
        refreshActionState();
    }

    private EditorTab getEditor(int index) {
        return (EditorTab) ((JComponent) tabbedEditors.getComponentAt(index)).getClientProperty(EditorTab.class);
    }

    private TabPanel getResultPane(int index) {
        return (TabPanel)tabbedPane.getComponentAt(index);
    }

    // if the query is cancelled execTime=-1, result and error are null's
    public static void queryExecutionComplete(EditorTab editor, QueryResult queryResult) {
        JTextComponent textArea = editor.getTextArea();
        textArea.setCursor(textCursor);
        Throwable error = queryResult.getError();
        if (queryResult.isComplete()) {
            long execTime = queryResult.getExecutionTime();
            editor.setStatus("Last execution time: " + (execTime > 0 ? "" + execTime : "<1") + " mS");
        } else {
            editor.setStatus("Last query was cancelled");
        }

        StudioPanel panel = editor.getPanel();
        if (error == null || error instanceof c.K4Exception) {
            try {
                if (queryResult.isComplete()) {
                    JTabbedPane tabbedPane = panel.tabbedPane;
                    KTableModel model = KTableModel.getModel(queryResult.getResult());
                    TabPanel modelTab = null;
                    if (model != null) {
                        modelTab = new TabPanel(panel, queryResult, model);
                        modelTab.addInto(tabbedPane);
                        modelTab.setToolTipText(editor.getServer().getConnectionString());
                    }
                    TabPanel tab = new TabPanel(panel, queryResult, null);
                    tab.addInto(tabbedPane);
                    tab.setToolTipText(editor.getServer().getConnectionString());
                    if (modelTab != null) {
                        tabbedPane.setSelectedComponent(modelTab);
                    }
                    while (tabbedPane.getTabCount() > CONFIG.getResultTabsCount()) {
                        tabbedPane.remove(0);
                    }
                }
                error = null;
            } catch (Throwable exc) {
                error = new RuntimeException("Error during result rendering", exc);
                log.error("Error during result rendering", exc);
            }
        }

        if (error != null) {
            String message = error.getMessage();
            if ((message == null) || (message.length() == 0))
                message = "No message with exception. Exception is " + error;
            StudioOptionPane.showError(editor.getPane(),
                    "\nAn unexpected error occurred whilst communicating with " +
                            editor.getServer().getConnectionString() +
                            "\n\nError detail is\n\n" + message + "\n\n",
                    "Studio for kdb+");
        }

        panel.refreshActionState();
    }

    public static Workspace getWorkspace() {
        Workspace workspace = new Workspace();
        Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();

        for (StudioPanel panel : allPanels) {
            Workspace.Window window = workspace.addWindow(panel.getFrame() == activeWindow);

            int count = panel.tabbedEditors.getTabCount();
            for (int index = 0; index < count; index++) {
                EditorTab editor = panel.getEditor(index);
                Server server = editor.getServer();
                String filename = editor.getFilename();
                boolean modified = editor.isModified();
                if (modified && CONFIG.getBoolean(Config.AUTO_SAVE)) {
                    editor.saveFileOnDisk(true);
                }
                JTextComponent textArea = editor.getTextArea();

                window.addTab(index == panel.tabbedEditors.getSelectedIndex())
                        .addFilename(filename)
                        .addServer(server)
                        .addContent(textArea.getText())
                        .setCaret(textArea.getCaretPosition())
                        .setModified(modified)
                        .setLineEnding(editor.getLineEnding());
            }

        }
        return workspace;
    }

    public void windowClosing(WindowEvent e) {
        closePanel();
    }


    public void windowClosed(WindowEvent e) {
    }


    public void windowOpened(WindowEvent e) {
    }
    // ctrl-alt spacebar to minimize window

    public void windowIconified(WindowEvent e) {
    }


    public void windowDeiconified(WindowEvent e) {
    }


    public void windowActivated(WindowEvent e) {
        this.invalidate();
        SwingUtilities.updateComponentTreeUI(this);
    }


    public void windowDeactivated(WindowEvent e) {
    }

    private class MarkingDocumentListener implements DocumentListener {
        private final EditorTab editor;
        public MarkingDocumentListener(EditorTab editor) {
            this.editor = editor;
        }
        private void update() {
            editor.setModified(true);
            refreshActionState();
        }
        public void changedUpdate(DocumentEvent evt) { update(); }
        public void insertUpdate(DocumentEvent evt) {
            update();
        }
        public void removeUpdate(DocumentEvent evt) {
            update();
        }
    }

    public JFrame frame() {
        return frame;
    }
}
