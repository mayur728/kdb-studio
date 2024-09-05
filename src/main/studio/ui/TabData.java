package studio.ui;

import javax.swing.*;

public class TabData {
    TabPanel tabPanel;
    String title;
    Icon icon;  // Optional, in case you have icons for your tabs

    public TabData(TabPanel tabPanel, String title, Icon icon) {
        this.tabPanel = tabPanel;
        this.title = title;
        this.icon = icon;
    }
}
