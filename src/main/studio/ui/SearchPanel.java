package studio.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;
import org.fife.ui.rsyntaxtextarea.DocumentRange;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.Vector;

import studio.utils.OrderFocusTraversalPolicy;

public class SearchPanel extends JPanel {

    private final JLabel lblReplace;
    private final JButton btnReplace;
    private final JButton btnReplaceAll;
    private JToggleButton tglWholeWord;
    private JToggleButton tglRegex;
    private JToggleButton tglCaseSensitive;
    private JComboBox<String> txtFind;
    private JComboBox<String> txtReplace;

    private final RSyntaxTextArea textArea;
    private final EditorPane editorPane;

    private enum SearchAction {Find, Replace, ReplaceAll};

    private static final Border ICON_BORDER = BorderFactory.createCompoundBorder(
            BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
            BorderFactory.createEmptyBorder(1,1,1,1)

    );

    private JToggleButton getButton(Icon icon, Icon selectedIcon, String tooltip) {
        JToggleButton button = new JToggleButton(icon);
        button.setSelectedIcon(selectedIcon);
        button.setBorder(ICON_BORDER);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        return button;
    }

    public SearchPanel(EditorPane editorPane) {
        this.editorPane = editorPane;
        this.textArea = editorPane.getTextArea();

        tglWholeWord = getButton(Util.SEARCH_WHOLE_WORD_SHADED_ICON, Util.SEARCH_WHOLE_WORD_ICON,"Whole word");
        tglRegex = getButton(Util.SEARCH_REGEX_SHADED_ICON, Util.SEARCH_REGEX_ICON, "Regular expression");
        tglCaseSensitive = getButton(Util.SEARCH_CASE_SENSITIVE_SHADED_ICON, Util.SEARCH_CASE_SENSITIVE_ICON, "Case sensitive");

        txtFind = new JComboBox();
        txtFind.setName("txtFind");
        txtFind.setEditable(true);

        txtReplace = new JComboBox();
        txtReplace.setName("txtReplace");
        txtReplace.setEditable(true);

        JLabel lblFind = new JLabel("Find: ");
        lblReplace = new JLabel("Replace: " );

        Action findAction = UserAction.create("Find", e -> find(true));
        Action findBackAction = UserAction.create("Find Back", e -> find(false));
        Action markAllAction = UserAction.create("Mark All", e -> markAll());
        Action replaceAction = UserAction.create("Replace", "Replace", KeyEvent.VK_R, e -> replace());
        Action replaceAllAction = UserAction.create("Replace All", "Replace All", KeyEvent.VK_A, e -> replaceAll());
        Action closeAction = UserAction.create("Close", "Close", KeyEvent.VK_C, e -> close());

        JButton btnFind = new JButton(findAction);
        JButton btnFindBack = new JButton(findBackAction);
        JButton btnMarkAll = new JButton(markAllAction);
        btnReplace = new JButton(replaceAction);
        btnReplace.setName("btnReplace");
        btnReplaceAll = new JButton(replaceAllAction);
        btnReplaceAll.setName("btnReplaceAll");
        JButton btnClose = new JButton(closeAction);

        JComponent editor = (JComponent)txtFind.getEditor().getEditorComponent();
        ActionMap am = editor.getActionMap();
        InputMap im = editor.getInputMap();
        int shift = InputEvent.SHIFT_DOWN_MASK;
        am.put("findAction", findAction);
        am.put("findBackAction", findBackAction);
        am.put("closeAction", closeAction);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"findAction");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"closeAction");

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F3,0),"findAction");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F3,shift),"findBackAction");

        editor = (JComponent)txtReplace.getEditor().getEditorComponent();
        am = editor.getActionMap();
        im = editor.getInputMap();
        am.put("replaceAction", replaceAction);
        am.put("closeAction", closeAction);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"replaceAction");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"closeAction");

        GroupLayoutSimple layout = new GroupLayoutSimple(this);
        layout.setAutoCreateGaps(false);
        layout.setStacks(
                new GroupLayoutSimple.Stack()
                        .addLine(lblFind)
                        .addLine(lblReplace),
                new GroupLayoutSimple.Stack()
                        .addLine(txtFind)
                        .addLine(txtReplace),
                new GroupLayoutSimple.Stack()
                        .addLine(tglWholeWord, tglRegex, tglCaseSensitive, btnFind, btnFindBack, btnMarkAll, btnClose)
                        .addLine(btnReplace, btnReplaceAll)

        );

        Vector<Component> order = new Vector<Component>(8);
        order.add(txtFind);     //UX: replace should be focused if pressing tab after typing the text to find
        order.add(txtReplace);
        order.add(btnFind);
        order.add(btnFindBack);
        order.add(btnMarkAll);
        order.add(btnClose);
        order.add(btnReplace);
        order.add(btnReplaceAll);

        setFocusTraversalPolicyProvider(true);
        setFocusTraversalPolicy(new OrderFocusTraversalPolicy(order));
    }

    public void setReplaceVisible(boolean visible) {
        lblReplace.setVisible(visible);
        txtReplace.setVisible(visible);
        btnReplace.setVisible(visible);
        btnReplaceAll.setVisible(visible);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            txtFind.getEditor().selectAll();
            txtFind.requestFocus();
        }
    }

    private SearchContext buildSearchContext() {
        SearchContext context = new SearchContext();
        JTextField editor = (JTextField)txtFind.getEditor().getEditorComponent();
        String text = editor.getText();
        txtFind.removeItem(text);
        txtFind.insertItemAt(text, 0);
        txtFind.setSelectedIndex(0);
        editor.selectAll();
        context.setSearchFor(text);
        context.setMatchCase(tglCaseSensitive.isSelected());
        context.setRegularExpression(tglRegex.isSelected());
        context.setSearchForward(true);
        context.setWholeWord(tglWholeWord.isSelected());
        context.setMarkAll(false);
        context.setSearchWrap(true);
        return context;
    }

    private void doSearch(SearchContext context, SearchAction action) {
        if (context.isRegularExpression()) {
            try {
                Pattern.compile(context.getSearchFor());
            } catch (PatternSyntaxException e) {
                editorPane.setTemporaryStatus("Error in regular expression: " + e.getMessage(), true);
                return;
            }
        }
        boolean searchFromSelStart;
        if (context.getReplaceWith() != null) {
            searchFromSelStart = context.getSearchForward();
        } else {
            searchFromSelStart = ! context.getSearchForward();
        }
        int pos = searchFromSelStart ? textArea.getSelectionStart() : textArea.getSelectionEnd();
        textArea.setSelectionStart(pos);
        textArea.setSelectionEnd(pos);
        SearchResult result;
        if (action == SearchAction.Find) {
            result = SearchEngine.find(textArea, context);
        } else {
            try {
                if (action == SearchAction.Replace) {
                    result = SearchEngine.replace(textArea, context);
                } else { //ReplaceAll
                    result = SearchEngine.replaceAll(textArea, context);
                }
            } catch (IndexOutOfBoundsException e) {
                editorPane.setTemporaryStatus("Error during replacement: " + e.getMessage(), true);
                return;
            }
        }

        String status;
        boolean isAlert = false;
        if (! result.wasFound()) {
            status = "Nothing was found";
            isAlert = true;
        } else if (result.getMarkedCount() > 0) {
            status = "Marked " + result.getMarkedCount() + " occurrence(s)";
        } else if (action == SearchAction.Find) {
            DocumentRange range = result.getMatchRange();
            int startOffset = range.getStartOffset();
            try {
                int line = textArea.getLineOfOffset(startOffset);
                int charOffset = startOffset-textArea.getLineStartOffset(line);
                status = String.format("Text found at %d:%d", 1+line, 1+charOffset);
                if (context.getSearchForward() ? (startOffset < pos) : (startOffset > pos)) {
                    status = status + ". Reached the end of the document, wrapping around.";
                    isAlert = true;
                }
            } catch(BadLocationException e) {
                status = e.getMessage();
                isAlert = true;
            }
        } else {
            status = "Replaced " + result.getCount() + " occurrence(s)";
        }
        editorPane.setTemporaryStatus(status, isAlert);
    }

    public void find(boolean forward) {
        SearchContext context = buildSearchContext();
        context.setSearchForward(forward);
        doSearch(context, SearchAction.Find);
    }

    private void markAll() {
        SearchContext context = buildSearchContext();
        context.setMarkAll(true);
        doSearch(context, SearchAction.Find);
    }

    private void replaceGen(SearchAction action) {
        SearchContext context = buildSearchContext();
        JTextField editor = (JTextField)txtReplace.getEditor().getEditorComponent();
        String replaceWithText = editor.getText();
        txtReplace.removeItem(replaceWithText);
        txtReplace.insertItemAt(replaceWithText, 0);
        txtReplace.setSelectedIndex(0);
        editor.selectAll();
        context.setReplaceWith(replaceWithText);
        doSearch(context, action);
    }

    private void replace()  {
        replaceGen(SearchAction.Replace);
    }

    private void replaceAll() {
        replaceGen(SearchAction.ReplaceAll);
    }

    private void close() {
        editorPane.hideSearchPanel();
        editorPane.getTextArea().requestFocus();
    }
}
