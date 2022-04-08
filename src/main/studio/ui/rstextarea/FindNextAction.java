package studio.ui.rstextarea;

import studio.ui.EditorPane;

import java.awt.event.ActionEvent;

public class FindNextAction extends EditorPaneAction {

    public static final String findNextAction = "kdbStudio.FindNextAction";
    public static final String findPreviousAction = "kdbStudio.FindPreviousAction";

    private final boolean forward;

    public FindNextAction(boolean forward) {
        super(forward ? findNextAction : findPreviousAction);
        this.forward = forward;
    }

    @Override
    protected void actionPerformed(ActionEvent e, EditorPane pane) {
        pane.findNext(forward);
    }
}
