package studio.ui;

import studio.kdb.ListModel;
import studio.kdb.*;
import studio.ui.action.QueryResult;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class TabPanel extends JPanel {
    private StudioPanel panel;

    private JToolBar toolbar = null;
    private JToggleButton tglBtnComma;
    private JButton uploadBtn = null;
    private JToggleButton tglBtnView;
    private QueryResult queryResult;
    private K.KBase result;
    private JTextComponent textArea = null;
    private QGrid grid = null;
    private KFormatContext formatContext = new KFormatContext(KFormatContext.DEFAULT);
    private ResultType type;

    private CardLayout cardLayout;
    private JPanel cardPanel;

    private final static Set<TabData> hiddenTabs = new HashSet<>();

    public TabPanel(StudioPanel panel, QueryResult queryResult, KTableModel model) {
        this.panel = panel;
        this.queryResult = queryResult;
        this.result = queryResult.getResult();
        this.cardLayout = new CardLayout();
        this.cardPanel = new JPanel(cardLayout);
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

            // Add components to cardPanel
            cardPanel.add(component, type.name());

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

            tglBtnView = new JToggleButton(Util.TABLE_ICON);
            tglBtnView.setSelectedIcon(Util.CONSOLE_ICON);
            tglBtnView.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            tglBtnView.setToolTipText("Table View / Console View Switch");
            tglBtnView.setFocusable(false);
            tglBtnView.addActionListener(e -> toggleView(tglBtnView.isSelected()));


            toolbar = new JToolBar();
            toolbar.setFloatable(false);
            toolbar.add(tglBtnComma);
            toolbar.add(Box.createRigidArea(new Dimension(16, 16)));
            toolbar.add(uploadBtn);
            toolbar.add(Box.createRigidArea(new Dimension(16, 16)));
            toolbar.add(tglBtnView);
            updateFormatting();
            // toggleView(tglBtnView.isSelected());
        } else {
            textArea = new JTextPane();
            String hint = QErrors.lookup(queryResult.getError().getMessage());
            hint = hint == null ? "" : "\nStudio Hint: Possibly this error refers to " + hint;
            textArea.setText("An error occurred during execution of the query.\nThe server sent the response:\n" + queryResult.getError().getMessage() + hint);
            textArea.setForeground(Color.RED);
            textArea.setEditable(false);
            component = new JScrollPane(textArea);
            type = ResultType.ERROR;

            // Add error component to cardPanel
            cardPanel.add(component, type.name());
        }

        setLayout(new BorderLayout());
        add(component, BorderLayout.CENTER);

    }

    public void toggleView(boolean showTableView) {
        JTabbedPane tabbedPane = (JTabbedPane) this.getParent();

        // Iterate through all tabs in the tabbedPane and manage their visibility
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component tabComponent = tabbedPane.getComponentAt(i);
            if (tabComponent instanceof TabPanel) {
                TabPanel tabPanel = (TabPanel) tabComponent;
                boolean isTable = tabPanel.getType() == ResultType.TABLE;

                // Get the title and icon (optional) of the tab
                String title = tabbedPane.getTitleAt(i);
                Icon icon = tabbedPane.getIconAt(i);

                // Check if the current tab matches the view (table or toggle)
                if (showTableView == isTable) {
                    // If not already in hiddenTabs, add it
                    if (!hiddenTabs.contains(new TabData(tabPanel, title, icon))) {
                        hiddenTabs.add(new TabData(tabPanel, title, icon));
                    }
                } else {
                    // Add tab to hiddenTabs before removing it
                    hiddenTabs.add(new TabData(tabPanel, title, icon));
                    tabbedPane.remove(tabPanel);  // Remove the tab from the tabbedPane
                    i--;  // Adjust index since we removed an item
                }
            }
        }
        // Re-add the hidden tabs that match the view
        for (TabData tabData : hiddenTabs) {
            boolean isTable = tabData.tabPanel.getType() == ResultType.TABLE;
            if (showTableView == isTable && tabbedPane.indexOfComponent(tabData.tabPanel) == -1) {
                // Re-add tab with its title and icon
                tabbedPane.addTab(tabData.title, tabData.icon, tabData.tabPanel);
            }
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

    private void updateFormatting() {
        formatContext.setShowThousandsComma(tglBtnComma.isSelected());
        if (grid != null) {
            grid.setFormatContext(formatContext);
        }
        if (type == ResultType.TEXT) {
            String text;
            if ((result instanceof K.UnaryPrimitive) && ((K.UnaryPrimitive) result).isIdentity()) text = "";
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
    }
}
