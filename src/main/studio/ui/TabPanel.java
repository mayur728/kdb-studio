package studio.ui;

import studio.kdb.*;
import studio.kdb.ListModel;
import studio.ui.action.QueryResult;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class TabPanel extends JPanel {
    private StudioPanel panel;

    private JToolBar toolbar = null;
    private JToolBar filterToolbar = null;
    private JToggleButton tglBtnComma;
    private JButton uploadBtn = null;
    private JButton enableFilterBtn = null;
    private JButton caseSensitiveFilterBtn = null;
    private QueryResult queryResult;
    private K.KBase result;
    private JTextComponent textArea = null;
    private QGrid grid = null;
    private KFormatContext formatContext = new KFormatContext(KFormatContext.DEFAULT);
    private ResultType type;
    private Map<Integer, Integer> hashCodeIndexMap = null;
    private JScrollPane scrollPane;
    private boolean caseSensitiveFilter = true;

    public TabPanel(StudioPanel panel, QueryResult queryResult, KTableModel model) {
        this.panel = panel;
        this.queryResult = queryResult;
        this.result = queryResult.getResult();
        initComponents(model);
    }

    public void setPanel(StudioPanel panel) {
        this.panel = panel;
        if (grid != null) {
            grid.setPanel(panel);
        }
    }

    public ResultType getType() {
        return type;
    }

    public void refreshActionState(boolean queryRunning) {
        if (uploadBtn != null) {
            uploadBtn.setEnabled(result != null && !queryRunning);
        }
    }

    private void upload() {
        String varName = StudioOptionPane.showInputDialog(panel, "Enter variable name", "Upload to Server");
        if (varName == null) return;
        panel.executeK4Query(new K.KList(new K.Function("{x set y}"), new K.KSymbol(varName), result));
    }

    private void initComponents(KTableModel model) {
        JComponent component;
        if (result != null) {
            if (model != null) {
                grid = new QGrid(panel, model);
                component = grid;
                if (model instanceof ListModel) {
                    type = ResultType.LIST;
                } else {
                    type = ResultType.TABLE;
                }
            } else {
                EditorPane editor = new EditorPane(false);
                editor.setLineWrap(true);
                textArea = editor.getTextArea();
                component = editor;
                type = ResultType.TEXT;
            }

            tglBtnComma = new JToggleButton(Util.COMMA_CROSSED_ICON);
            tglBtnComma.setSelectedIcon(Util.COMMA_ICON);

            tglBtnComma.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            tglBtnComma.setToolTipText("Add comma as thousands separators for numbers");
            tglBtnComma.setFocusable(false);
            tglBtnComma.addActionListener(e -> {
                updateFormatting();
            });

            uploadBtn = new JButton(Util.UPLOAD_ICON);
            uploadBtn.setToolTipText("Upload to server");
            uploadBtn.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            uploadBtn.setFocusable(false);
            uploadBtn.addActionListener(e -> upload());

            enableFilterBtn = new JButton(Util.FIND_ICON);
            enableFilterBtn.setToolTipText("Show hide filter toolbar");
            enableFilterBtn.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            enableFilterBtn.setFocusable(false);
            enableFilterBtn.addActionListener(e -> {scrollPane.setVisible(!scrollPane.isVisible());
                                                    this.revalidate();
                                                    this.repaint();});

            caseSensitiveFilterBtn = new JButton(Util.SEARCH_CASE_SENSITIVE_ICON);
            caseSensitiveFilterBtn.setToolTipText("Case sensitive toggle");
            caseSensitiveFilterBtn.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            caseSensitiveFilterBtn.setFocusable(false);
            caseSensitiveFilterBtn.addActionListener(e -> {
              caseSensitiveFilter = !caseSensitiveFilter;
                if (caseSensitiveFilter) {
                    caseSensitiveFilterBtn.setIcon(Util.SEARCH_CASE_SENSITIVE_ICON);
                } else {
                    caseSensitiveFilterBtn.setIcon(Util.SEARCH_CASE_SENSITIVE_SHADED_ICON);
                }
            });

            toolbar = new JToolBar();
            toolbar.setFloatable(false);
            toolbar.add(tglBtnComma);
            toolbar.add(Box.createRigidArea(new Dimension(16,16)));
            toolbar.add(uploadBtn);
            toolbar.add(enableFilterBtn);
            toolbar.add(caseSensitiveFilterBtn);

            if(ResultType.TABLE == type) {
                hashCodeIndexMap = new HashMap<Integer, Integer>();
                populateFilterToolbar();
            }

            updateFormatting();
        } else {
            textArea = new JTextPane();
            String hint = QErrors.lookup(queryResult.getError().getMessage());
            hint = hint == null ? "" : "\nStudio Hint: Possibly this error refers to " + hint;
            textArea.setText("An error occurred during execution of the query.\nThe server sent the response:\n" + queryResult.getError().getMessage() + hint);
            textArea.setForeground(Color.RED);
            textArea.setEditable(false);
            component = new JScrollPane(textArea);
            type = ResultType.ERROR;
        }

        setLayout(new BorderLayout());
        add(component, BorderLayout.CENTER);
    }

    protected void populateFilterToolbar() {
        filterToolbar = new JToolBar();
        filterToolbar.setFloatable(false);
        filterToolbar.add(Box.createRigidArea(new Dimension(16,16)));
        filterToolbar.add(new JLabel("Filters: "));
        String[] columns = (String[]) ((K.Flip) result).x.getArray();
        for (int i = 0; i< columns.length; i++) {
            JLabel filterLable = new JLabel(columns[i] +": ");
            filterToolbar.add(filterLable);
            int colWithFromModel = getTable().getColumnModel().getColumn(i).getPreferredWidth();
            JTextField textfieldFilter = new JTextField();
            textfieldFilter.setPreferredSize(new Dimension((int) (colWithFromModel - filterLable.getPreferredSize().getWidth()), 0));
            hashCodeIndexMap.put(textfieldFilter.getDocument().hashCode(), i);
            textfieldFilter.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    applyFilter(e);
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    grid.getTable().setRowSorter(null);

                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    applyFilter(e);
                }
            });
            filterToolbar.add(textfieldFilter);
        }
    }

    private void applyFilter(DocumentEvent e) {
        try {
            int index = hashCodeIndexMap.get(e.getDocument().hashCode());
            String filterInput = e.getDocument().getText(0, e.getDocument().getLength());
            TableRowSorter<KTableModel> tableRowSorter = new TableRowSorter<>(((KTableModel)grid.getTable().getModel()));
            grid.getTable().setRowSorter(tableRowSorter);
            String regex = "";
            if (caseSensitiveFilter) {
                regex = "(?i).*" + filterInput +".*";
            } else {
                regex = ".*" + filterInput +".*";
            }
            tableRowSorter.setRowFilter(RowFilter.regexFilter(regex, index));
        } catch (BadLocationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void addInto(JTabbedPane tabbedPane) {
        String title = type.title;
        if (isTable()) {
            title = title + " [" + grid.getRowCount() + " rows] ";
        }
        tabbedPane.addTab(title, type.icon, this);
        int tabIndex = tabbedPane.getTabCount() - 1;
        tabbedPane.setSelectedIndex(tabIndex);
        tabbedPane.setToolTipTextAt(tabIndex, "Executed at server: " + queryResult.getServer().getDescription(true));
        updateToolbarLocation(tabbedPane);
        if (isTable()) {
            updateFilterToolbarLocation(tabbedPane);
        }
    }

    public void updateToolbarLocation(JTabbedPane tabbedPane) {
        if (toolbar == null) return;

        remove(toolbar);
        if (tabbedPane.getTabPlacement() == JTabbedPane.TOP) {
            toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
            add(toolbar, BorderLayout.WEST);
        } else {
            toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
            add(toolbar, BorderLayout.NORTH);
        }
    }

    public void updateFilterToolbarLocation(JTabbedPane tabbedPane) {
        if (filterToolbar == null) return;

        remove(filterToolbar);
        if (tabbedPane.getTabPlacement() == JTabbedPane.TOP) {
            filterToolbarScrollPaneNesting();
        }
    }

    private void filterToolbarScrollPaneNesting() {
        filterToolbar.setLayout(new BoxLayout(filterToolbar, BoxLayout.X_AXIS));
        scrollPane = new JScrollPane(filterToolbar, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        JScrollBar toolBarHorizontalScrollBar = scrollPane.getHorizontalScrollBar();
        JScrollBar tableHorizontalScrollBar = grid.getScrollPane().getHorizontalScrollBar();
        toolBarHorizontalScrollBar.addAdjustmentListener(e -> tableHorizontalScrollBar.setValue(e.getValue()));
        tableHorizontalScrollBar.addAdjustmentListener(e -> toolBarHorizontalScrollBar.setValue(e.getValue()));
        scrollPane.setVisible(false);
        add(scrollPane, BorderLayout.NORTH);
    }

    private void updateFormatting() {
        formatContext.setShowThousandsComma(tglBtnComma.isSelected());
        if (grid != null) {
            grid.setFormatContext(formatContext);
        }
        if (type == ResultType.TEXT) {
            String text;
            if ((result instanceof K.UnaryPrimitive) && ((K.UnaryPrimitive)result).isIdentity() ) text = "";
            else {
                text = Util.limitString(result.toString(formatContext), Config.getInstance().getMaxCharsInResult());
            }
            textArea.setText(text);
        }
    }

    public void toggleCommaFormatting() {
        if (tglBtnComma == null) return;
        tglBtnComma.doClick();
    }

    public void setDoubleClickTimeout(long doubleClickTimeout) {
        if (grid == null) return;
        grid.setDoubleClickTimeout(doubleClickTimeout);
    }

    public JTable getTable() {
        if (grid == null) return null;
        return grid.getTable();
    }

    public boolean isTable() {
        return grid != null;
    }

    public enum ResultType {
        ERROR("Error Details ", Util.ERROR_SMALL_ICON),
        TEXT(I18n.getString("ConsoleView"), Util.CONSOLE_ICON),
        LIST("List", Util.TABLE_ICON),
        TABLE("Table", Util.TABLE_ICON);

        private final String title;
        private final Icon icon;
        ResultType(String title, Icon icon) {
            this.title = title;
            this.icon = icon;
        }
    };
}
