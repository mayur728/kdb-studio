package studio.ui;

import java.awt.Window;
import studio.kdb.Config;
import studio.kdb.Server;

public class AddServerForm extends ServerForm {

    private static Server newServer() {
        Server server = new Server();
        server.setBackgroundColor(Config.getInstance().getColor(Config.getThemeEntry(Config.ThemeEntry.BACKGROUND)));
        return server;
    }

    public AddServerForm(Window owner) {
        super(owner, "Add a new server", newServer());
    }
}
