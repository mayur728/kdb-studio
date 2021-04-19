package studio.ui;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;

public class TabPanel extends JPanel {
    Icon icon;
    String title;
    JComponent component;

    public TabPanel(String title, Icon icon, JComponent component) {
        this.title = title;
        this.icon = icon;
        this.component = component;
    }

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon icon) {
        this.icon = icon;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public JComponent getComponent() {
        return component;
    }

    public void setComponent(JComponent component) {
        this.component = component;
    }
}

