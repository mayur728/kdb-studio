package studio.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

public class StudioOptionPane {

    public static void showMessageDialog(Component parentComponent, Object message) {
        showOptionDialog(parentComponent, message, null, JOptionPane.DEFAULT_OPTION,
            JOptionPane.INFORMATION_MESSAGE, null, null, 0);
    }

    public static void showMessageDialog(Component parentComponent, Object message, String title,
                                         int messageType) {
        showOptionDialog(parentComponent, message, title, JOptionPane.DEFAULT_OPTION, messageType,
            null, null, 0);
    }

    public static void showMessageDialog(Component parentComponent, Object message, String title,
                                         int messageType, Icon icon) {
        showOptionDialog(parentComponent, message, title, JOptionPane.DEFAULT_OPTION, messageType,
            icon, null, 0);
    }

    private static void findButtons(ArrayList<JButton> buttons, Component c) {
        if (c instanceof JButton) {
            buttons.add((JButton) c);
        }
        if (c instanceof Container) {
            for (Component c2 : ((Container) c).getComponents()) {
                findButtons(buttons, c2);
            }
        }
    }

    private static void assignAction(JComponent c, char key, Action action) {
        c.getInputMap().put(KeyStroke.getKeyStroke(key), "press " + key);
        c.getInputMap().put(KeyStroke.getKeyStroke(Character.toLowerCase(key)), "press " + key);
        c.getActionMap().put("press " + key, action);
    }

    public static int showOptionDialog(Component parentComponent, Object message, String title,
                                       int optionType, int messageType, Icon icon, Object[] options,
                                       Object initialValue) {
        JOptionPane pane =
            new JOptionPane(message, messageType, optionType, icon, options, initialValue);
        ArrayList<JButton> buttons = new ArrayList();
        findButtons(buttons, pane);
        for (JButton b : buttons) {
            int mnemonic = b.getMnemonic();
            if (mnemonic > 0) {
                char key = (char) mnemonic;
                Action action = new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        b.doClick();
                    }
                };
                assignAction(pane, key, action);
                for (JButton b2 : buttons) {
                    assignAction(b2, key, action);
                }
            }
        }
        JDialog dialog = pane.createDialog(parentComponent, title);
        dialog.setVisible(true);
        Object result = pane.getValue();
        if (result == null) {
            return -1;
        }
        if (result instanceof Integer) {
            return (Integer) result;
        }
        return -1;
    }
}
