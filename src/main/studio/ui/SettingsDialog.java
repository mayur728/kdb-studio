package studio.ui;

import studio.core.AuthenticationManager;
import studio.core.Credentials;
import studio.kdb.Config;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static javax.swing.GroupLayout.PREFERRED_SIZE;

public class SettingsDialog extends EscapeDialog {
    private JComboBox comboBoxAuthMechanism;
    private JTextField txtUser;
    private JPasswordField txtPassword;
    private JCheckBox chBoxShowServerCombo;
    private JComboBox cbFontName;
    private JSpinner spnFontSize;
    private JRadioButton rbLineEndingCRLF;
    private JRadioButton rbLineEndingLF;
    private JComboBox comboBoxLookAndFeel;
    private JFormattedTextField txtTabsCount;
    private JButton btnOk;
    private JButton btnCancel;

    private final static int FIELD_SIZE = 150;

    public SettingsDialog(JFrame owner) {
        super(owner, "Settings");
        initComponents();
    }

    public String getDefaultAuthenticationMechanism() {
        return comboBoxAuthMechanism.getModel().getSelectedItem().toString();
    }

    public String getUser() {
        return txtUser.getText().trim();
    }

    public String getPassword() {
        return new String(txtPassword.getPassword());
    }

    public boolean isShowServerComboBox() {
        return chBoxShowServerCombo.isSelected();
    }

    public String getLookAndFeelClassName() {
        return ((CustomiszedLookAndFeelInfo)comboBoxLookAndFeel.getSelectedItem()).getClassName();
    }

    public int getResultTabsCount() {
        return (Integer) txtTabsCount.getValue();
    }

    private void refreshCredentials() {
        Credentials credentials = Config.getInstance().getDefaultCredentials(getDefaultAuthenticationMechanism());

        txtUser.setText(credentials.getUsername());
        txtPassword.setText(credentials.getPassword());
        chBoxShowServerCombo.setSelected(Config.getInstance().isShowServerComboBox());
    }

    public String getFontName() {
        return cbFontName.getSelectedItem().toString();
    }

    public int getFontSize() {
        return (Integer)spnFontSize.getValue();
    }

    public String getLineEnding() {
        if (rbLineEndingCRLF.isSelected()) return "CRLF";
        else if (rbLineEndingLF.isSelected()) return "LF";
        else return "";
    }

    @Override
    public void align() {
        super.align();
        btnOk.requestFocusInWindow();
    }

    private void initComponents() {
        JPanel root = new JPanel();

        txtUser = new JTextField();
        txtPassword = new JPasswordField();
        comboBoxAuthMechanism = new JComboBox(AuthenticationManager.getInstance().getAuthenticationMechanisms());
        comboBoxAuthMechanism.getModel().setSelectedItem(Config.getInstance().getDefaultAuthMechanism());
        comboBoxAuthMechanism.addItemListener(e -> refreshCredentials());

        JLabel lblLookAndFeel = new JLabel("Look and Feel:");

        LookAndFeels lookAndFeels = new LookAndFeels();
        comboBoxLookAndFeel = new JComboBox(lookAndFeels.getLookAndFeels());
        CustomiszedLookAndFeelInfo lf = lookAndFeels.getLookAndFeel(Config.getInstance().getLookAndFeel());
        if (lf == null) {
            lf = lookAndFeels.getLookAndFeel(UIManager.getLookAndFeel().getClass().getName());
        }
        comboBoxLookAndFeel.setSelectedItem(lf);
        JLabel lblResultTabsCount = new JLabel("Result tabs count:");
        NumberFormatter formatter = new NumberFormatter();
        formatter.setMinimum(1);
        formatter.setAllowsInvalid(false);
        txtTabsCount = new JFormattedTextField(formatter);
        txtTabsCount.setValue(Config.getInstance().getResultTabsCount());
        chBoxShowServerCombo = new JCheckBox("Show server drop down list in the toolbar");
        JLabel lblAuthMechanism = new JLabel("Authentication:");
        JLabel lblUser = new JLabel("  User:");
        JLabel lblPassword = new JLabel("  Password:");

        JLabel lblFontSize = new JLabel("Font:");
        spnFontSize = new JSpinner(new SpinnerNumberModel(Config.getInstance().getFontSize(), 8, 72, 1));

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        cbFontName = new JComboBox(ge.getAvailableFontFamilyNames());
        cbFontName.getModel().setSelectedItem(Config.getInstance().getFontName());

        JLabel lblLineEnding = new JLabel("Line ending:");
        rbLineEndingCRLF = new JRadioButton("Windows (CR+LF)");
        rbLineEndingLF = new JRadioButton("UNIX (LF)");
        ButtonGroup group = new ButtonGroup();
        group.add(rbLineEndingCRLF);
        group.add(rbLineEndingLF);
        String lineEnding = Config.getInstance().getLineEnding();
        if (lineEnding.equals("CRLF")) rbLineEndingCRLF.setSelected(true);
        else if (lineEnding.equals("LF")) rbLineEndingLF.setSelected(true);

        Component glue = Box.createGlue();
        Component glue1 = Box.createGlue();
        Component glue2 = Box.createGlue();
        Component glue3 = Box.createGlue();

        btnOk = new JButton("OK");
        btnCancel = new JButton("Cancel");

        btnOk.addActionListener(e->accept());
        btnCancel.addActionListener(e->cancel());

        refreshCredentials();

        GroupLayout layout = new GroupLayout(root);
        root.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
                layout.createParallelGroup()
                    .addGroup(
                        layout.createSequentialGroup()
                            .addComponent(lblLookAndFeel)
                            .addComponent(comboBoxLookAndFeel)
                            .addComponent(glue2))
                    .addGroup(
                        layout.createSequentialGroup()
                            .addComponent(lblResultTabsCount)
                            .addComponent(txtTabsCount))
                    .addGroup(
                        layout.createSequentialGroup()
                            .addComponent(chBoxShowServerCombo))
                            .addComponent(glue3)
                    .addGroup(
                        layout.createSequentialGroup()
                            .addComponent(lblAuthMechanism)
                            .addComponent(comboBoxAuthMechanism, PREFERRED_SIZE, PREFERRED_SIZE, PREFERRED_SIZE)
                            .addComponent(lblUser)
                            .addComponent(txtUser, FIELD_SIZE, FIELD_SIZE, FIELD_SIZE)
                            .addComponent(lblPassword)
                            .addComponent(txtPassword, FIELD_SIZE, FIELD_SIZE, FIELD_SIZE))
                    .addComponent(glue)
                    .addGroup(
                        layout.createSequentialGroup()
                            .addComponent(lblFontSize)
                            .addComponent(spnFontSize, FIELD_SIZE, FIELD_SIZE, FIELD_SIZE)
                            .addComponent(cbFontName, FIELD_SIZE, FIELD_SIZE, FIELD_SIZE))
                    .addGroup(
                        layout.createSequentialGroup()
                            .addComponent(lblLineEnding)
                            .addComponent(rbLineEndingCRLF, FIELD_SIZE, FIELD_SIZE, FIELD_SIZE)
                            .addComponent(rbLineEndingLF, FIELD_SIZE, FIELD_SIZE, FIELD_SIZE))
                    .addGroup(
                        layout.createSequentialGroup()
                            .addComponent(glue1)
                            .addComponent(btnOk)
                            .addComponent(btnCancel))
        );


        layout.setVerticalGroup(
                layout.createSequentialGroup()
                    .addGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                            .addComponent(lblLookAndFeel)
                            .addComponent(comboBoxLookAndFeel)
                            .addComponent(glue2))
                    .addGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                            .addComponent(lblResultTabsCount)
                            .addComponent(txtTabsCount))
                    .addGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                            .addComponent(chBoxShowServerCombo)
                            .addComponent(glue3)
                    ).addGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                            .addComponent(lblAuthMechanism)
                            .addComponent(comboBoxAuthMechanism)
                            .addComponent(lblUser)
                            .addComponent(txtUser)
                            .addComponent(lblPassword)
                            .addComponent(txtPassword))
                    .addComponent(glue)
                    .addGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                            .addComponent(lblFontSize)
                            .addComponent(spnFontSize)
                            .addComponent(cbFontName))
                    .addGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                            .addComponent(lblLineEnding)
                            .addComponent(rbLineEndingCRLF)
                            .addComponent(rbLineEndingLF))
                    .addGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                            .addComponent(glue1)
                            .addComponent(btnOk)
                            .addComponent(btnCancel))
        );
        layout.linkSize(SwingConstants.HORIZONTAL, txtUser, txtPassword, txtTabsCount);
        layout.linkSize(SwingConstants.HORIZONTAL, btnOk, btnCancel);
        setContentPane(root);
    }

    private static class LookAndFeels {
        private final Map<String, CustomiszedLookAndFeelInfo> mapLookAndFeels;

        public LookAndFeels() {
            mapLookAndFeels = new HashMap<>();
            for (UIManager.LookAndFeelInfo lf: UIManager.getInstalledLookAndFeels()) {
                mapLookAndFeels.put(lf.getClassName(), new CustomiszedLookAndFeelInfo(lf));
            }
        }
        public CustomiszedLookAndFeelInfo[] getLookAndFeels() {
            return mapLookAndFeels.values().toArray(new CustomiszedLookAndFeelInfo[0]);
        }
        public CustomiszedLookAndFeelInfo getLookAndFeel(String className) {
            return mapLookAndFeels.get(className);
        }
    }

    private static class CustomiszedLookAndFeelInfo extends UIManager.LookAndFeelInfo {
        public CustomiszedLookAndFeelInfo(UIManager.LookAndFeelInfo lfInfo) {
            super(lfInfo.getName(), lfInfo.getClassName());
        }

        @Override
        public String toString() {
            return getName();
        }
    }
}
