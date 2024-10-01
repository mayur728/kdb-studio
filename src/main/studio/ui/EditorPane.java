package studio.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import studio.kdb.Config;
import studio.ui.rstextarea.RSTextAreaFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorPane extends JPanel {

    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;
    private final MinSizeLabel lblRowCol;
    private final MinSizeLabel lblInsStatus;
    private final JLabel lblStatus;
    private final Box boxStatus;
    private final Box statusBar;

    public List<String> functionNames;
    private JPopupMenu suggestionMenu;
    private JList<String> suggestionList;
    private Timer updateTimer;
    private static final Config CONFIG = Config.getInstance();
    private boolean suppressPopup = false;

    private final SearchPanel searchPanel;

    private Timer tempStatusTimer = new Timer(3000, this::tempStatusTimerAction);
    private String oldStatus = "";

    private final int yGap;
    private final int xGap;


    public EditorPane(boolean editable) {
        super(new BorderLayout());
        FontMetrics fm = getFontMetrics(UIManager.getFont("Label.font"));
        yGap = Math.round(0.1f * fm.getHeight());
        xGap = Math.round(0.25f * SwingUtilities.computeStringWidth(fm, "x"));

        textArea = RSTextAreaFactory.newTextArea(editable);
        textArea.addCaretListener(e -> updateRowColStatus());
        textArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                updateTextModeStatus();
            }
        });

        lblRowCol = new MinSizeLabel("");
        lblRowCol.setHorizontalAlignment(JLabel.CENTER);
        lblRowCol.setMinimumWidth("9999:9999");
        setBorder(lblRowCol);

        lblInsStatus = new MinSizeLabel("INS");
        lblInsStatus.setHorizontalAlignment(JLabel.CENTER);
        lblInsStatus.setMinimumWidth("INS", "OVR");
        setBorder(lblInsStatus);
        lblStatus = new JLabel("Ready");
        boxStatus = Box.createHorizontalBox();
        boxStatus.add(lblStatus);
        boxStatus.add(Box.createHorizontalGlue());
        setBorder(boxStatus);

        statusBar = Box.createHorizontalBox();
        statusBar.add(boxStatus);
        statusBar.add(lblInsStatus);
        statusBar.add(lblRowCol);
        statusBar.setVisible(editable);

        Font font = Config.getInstance().getFont(Config.FONT_EDITOR);
        textArea.setFont(font);
        scrollPane = new RTextScrollPane(textArea);
        scrollPane.getGutter().setLineNumberFont(font);

        searchPanel = new SearchPanel(this);
        hideSearchPanel();

        add(searchPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        if(CONFIG.getBoolean(Config.RSTA_AUTO_COMPLETE))
        {
            addDocumentListenersAndTriggerAutoComplete(textArea);
        }
    }

    public void addDocumentListenersAndTriggerAutoComplete(RSyntaxTextArea textArea) {
        suggestionMenu = new JPopupMenu();
        suggestionList = new JList<>();
        suggestionMenu.add(new JScrollPane(suggestionList));

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateFunctionNames();
                if (isTyping(e)) {
                    updateSuggestions(e);
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                suggestionMenu.setVisible(false);
                updateFunctionNames();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                suggestionMenu.setVisible(false);
                updateFunctionNames();
            }

            private boolean isTyping(DocumentEvent e) {
                try {
                    String textChange = e.getDocument().getText(e.getOffset(), e.getLength());
                    return textChange.length() == 1 && !(Character.isWhitespace(textChange.charAt(0)));
                } catch (BadLocationException ex) {
                    ex.printStackTrace();
                    return false;
                }
            }

            private void updateFunctionNames() {
                String text = textArea.getText();
                functionNames = extractFunctionNames(text);
            }

            private void updateSuggestions(DocumentEvent e) {

                String text = textArea.getText();
                int caretPosition = textArea.getCaretPosition();
                int lastSpaceIndex = findLastNonWordChar(text, caretPosition - 1);
                if (lastSpaceIndex > 0 && !(Character.isWhitespace(text.charAt(caretPosition)))) {
                    String prefix = text.substring(lastSpaceIndex + 1, caretPosition);
                    if (caretPosition <= text.length()) {
                        prefix = prefix.concat(String.valueOf(textArea.getText().charAt(caretPosition)));
                    }
                    if (!CONFIG.getBoolean(Config.RSTA_AUTO_COMPLETE)) {
                        suggestionMenu.setVisible(false);
                    } else if (prefix.isEmpty()) {
                        suggestionMenu.setVisible(false);
                    } else {
                        List<String> suggestions = getSuggestions(prefix);
                        if (suggestions.isEmpty()) {
                            suggestionMenu.setVisible(false);
                        } else {
                            suggestionList.setListData(suggestions.toArray(new String[0]));
                            suggestionList.setSelectedIndex(0);
                            showSuggestionPopupWithTimer(textArea);
                        }
                    }
                }
            }
        });

        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    insertSelectedSuggestion(textArea);
                }
            }
        });

        suggestionList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();
                if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_TAB) {
                    insertSelectedSuggestion(textArea);
                } else if (keyCode == KeyEvent.VK_DOWN) {
                    int selectedIndex = suggestionList.getSelectedIndex();
                    if (selectedIndex < suggestionList.getModel().getSize() - 1) {
                        suggestionList.setSelectedIndex(selectedIndex);
                    }
                } else if (keyCode == KeyEvent.VK_UP) {
                    int selectedIndex = suggestionList.getSelectedIndex();
                    if (selectedIndex > 0) {
                        suggestionList.setSelectedIndex(selectedIndex);
                    }
                } else if (keyCode == KeyEvent.VK_ESCAPE) {
                    suggestionMenu.setVisible(false);
                }
            }
        });

        textArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (suggestionMenu.isVisible()) {
                    int keyCode = e.getKeyCode();
                    if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_UP) {
                        suggestionList.dispatchEvent(e);
                    } else if (keyCode == KeyEvent.VK_ESCAPE) {
                        suggestionMenu.setVisible(false);
                    } else if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_TAB) {
                        insertSelectedSuggestion(textArea);
                        e.consume();
                    }
                }
            }
        });
    }

    private List<String> extractFunctionNames(String text) {
        List<String> functionNames = new ArrayList<>();

        Pattern pattern = Pattern.compile("(?<dot>[.]*)\\b(?<name>[a-zA-Z_][a-zA-Z0-9_.]*)\\s*:\\s*\\{[^}]*\\}");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String dot = matcher.group("dot") == null ? "" : matcher.group("dot");
            String name = matcher.group("name");
            functionNames.add(dot + name);
        }
        return functionNames;
    }

    private List<String> getSuggestions(String prefix) {
        Set<String> suggestions = new HashSet<>();
        String suggestion = null;

        for (String functionName : functionNames) {
            if (functionName.startsWith(prefix)) {
                int startIndex = prefix.length();
                int endIndex = functionName.indexOf('.', startIndex);
                int lastDotIndexPrefix = prefix.lastIndexOf('.');
                if (lastDotIndexPrefix != -1 && endIndex != -1) {
                    suggestion = functionName.substring(lastDotIndexPrefix, endIndex);
                    if (!suggestion.equals(prefix) && !suggestion.equals(prefix.substring(lastDotIndexPrefix)))
                        suggestions.add(suggestion);
                } else if (lastDotIndexPrefix != -1) {
                    suggestion = functionName.substring(lastDotIndexPrefix);
                    if (!suggestion.equals(prefix) && !suggestion.equals(prefix.substring(lastDotIndexPrefix)))
                        suggestions.add(suggestion);
                } else if (endIndex != -1) {
                    suggestion = functionName.substring(0, endIndex);
                    if (!suggestion.equals(prefix))
                        suggestions.add(suggestion);
                } else {
                    if (!functionName.equals(prefix))
                        suggestions.add(functionName);
                }
            }
        }
        List<String> sortedSuggestions = new ArrayList<>(suggestions);
        Collections.sort(sortedSuggestions);

        return sortedSuggestions;
    }

    private int findLastNonWordChar(String text, int index) {
        if (index > (text.length() - 1)) {
            index--;
        }
        while (index >= 0) {
            char c = text.charAt(index);
            if (Character.isWhitespace(c)) {
                return index;
            }
            index--;
        }
        return -1;
    }

    private void showSuggestionPopup(RSyntaxTextArea textArea) {
        try {
            int caretPosition = textArea.getCaretPosition();
            Rectangle caretCoords = textArea.modelToView(caretPosition);
            suggestionMenu.show(textArea, caretCoords.x, caretCoords.y + caretCoords.height);
            textArea.requestFocusInWindow(); //Return focus to textArea
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showSuggestionPopupWithTimer(RSyntaxTextArea textArea) {
        if (suppressPopup) {
            return; // Don't show the popup if suppressed
        }
        if (updateTimer != null && updateTimer.isRunning()) {
            updateTimer.restart();
        } else {
            updateTimer = new Timer(200, e -> showSuggestionPopup(textArea));
            updateTimer.setRepeats(false);
            updateTimer.start();
        }
    }

    private void insertSelectedSuggestion(RSyntaxTextArea textArea) {
        String selectedValue = suggestionList.getSelectedValue();
        if (selectedValue != null) {
            try {
                suppressPopup = true; // suppress the popup
                int caretPosition = textArea.getCaretPosition();
                int lastSpaceIndex = findLastNonWordChar(textArea.getText(), caretPosition - 1);
                int start = lastSpaceIndex + 1;
                int end = caretPosition;

                String prefix = textArea.getText().substring(start, caretPosition);
                int lastDotIndexPrefix = prefix.lastIndexOf('.');

                Document doc = textArea.getDocument();
                if (start >= 0 && end >= start && end <= doc.getLength()) {
                    if (lastDotIndexPrefix != -1) {
                        String value = selectedValue.substring(((prefix.length() - 1) - lastDotIndexPrefix) + 1);
                        doc.insertString(caretPosition, value, null);
                        suggestionMenu.setVisible(false);
                    } else {
                        doc.insertString(caretPosition, selectedValue.substring(end - start), null);
                        suggestionMenu.setVisible(false);
                    }
                }
                suggestionMenu.setVisible(false);
                textArea.requestFocusInWindow(); //Return focus to textArea
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                suppressPopup = false; // Re-enable the popup after insertion
            }
        }
    }

    public void hideSearchPanel() {
        searchPanel.setVisible(false);
        textArea.setHighlighter(null); // workaround to clear all marks
    }

    public void showSearchPanel(boolean showReplace) {
        searchPanel.setReplaceVisible(showReplace);
        searchPanel.setVisible(true);
    }

    public void findNext(boolean forward) {
        searchPanel.find(forward);
    }

    public RSyntaxTextArea getTextArea() {
        return textArea;
    }

    public void setLineWrap(boolean value) {
        textArea.setLineWrap(value);
    }

    public void setStatus(String status, boolean isAlert) {
        lblStatus.setText(status);
        if (isAlert) {
            lblStatus.setForeground(Color.RED);
            //old netbeans editor style was white text on red background
            //lblStatus.setForeground(Color.WHITE);
            //boxStatus.setOpaque(true);
            //boxStatus.setBackground(Color.RED);
        } else {
            lblStatus.setForeground(null);
            //boxStatus.setOpaque(false);
            //boxStatus.setBackground(null);
        }
    }

    public void setStatus(String status) {
        setStatus(status, false);
    }

    public void setTemporaryStatus(String status, boolean isAlert) {
        if (!tempStatusTimer.isRunning()) {
            oldStatus = lblStatus.getText();
        }
        setStatus(status, isAlert);
        tempStatusTimer.restart();
    }

    public void setTemporaryStatus(String status) {
        setTemporaryStatus(status, false);
    }

    private void tempStatusTimerAction(ActionEvent event) {
        setStatus(oldStatus);
    }

    private void updateRowColStatus() {
        int row = textArea.getCaretLineNumber() + 1;
        int col = textArea.getCaretPosition() - textArea.getLineStartOffsetOfCurrentLine() + 1;
        lblRowCol.setText("" + row + ":" + col);
    }

    private void updateTextModeStatus() {
        String text = textArea.getTextMode() == RSyntaxTextArea.INSERT_MODE ? "INS" : "OVR";
        lblInsStatus.setText(text);
    }

    private void setBorder(JComponent component) {
        component.setBorder(
                BorderFactory.createCompoundBorder(
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createEmptyBorder(yGap,xGap,yGap,xGap),
                            BorderFactory.createLineBorder(Color.LIGHT_GRAY)
                    ),
                    BorderFactory.createEmptyBorder(2*yGap, 2*xGap, yGap, 2*xGap)
                )
        );
    }

    public void setTextAreaFont(Font font) { //don't call this setFont, it leads to an error
        //textArea.setFont(font);   //useless, now the equivalent operation is in RSToken.getStyle()
        scrollPane.getGutter().setLineNumberFont(font);
    }

}
