package studio.ui;

import java.awt.Window;
import studio.kdb.Server;

public class EditServerForm extends ServerForm {
    public EditServerForm(Window owner, Server server) {
        super(owner,"Edit Server Details",server);
    }
}
