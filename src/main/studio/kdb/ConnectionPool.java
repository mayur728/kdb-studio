package studio.kdb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import studio.core.AuthenticationManager;
import studio.core.Credentials;
import studio.core.IAuthenticationMechanism;
import java.util.*;
import java.io.IOException;
import kx.c.K4Exception;

public class ConnectionPool {

    private static final Logger log = LogManager.getLogger();

    private final static ConnectionPool instance = new ConnectionPool();
    private final Map<Server,List<kx.c>> freeMap = new HashMap<>();
    private final Map<Server,List<kx.c>> busyMap = new HashMap<>();

    public static ConnectionPool getInstance() {
        return instance;
    }

    private ConnectionPool() {}

    public synchronized void purge(Server s) {
        List<kx.c> list = freeMap.computeIfAbsent(s, k -> new LinkedList<>());
        for (kx.c c: list) {
            c.close();
        }
        list.clear();
        busyMap.put(s,new LinkedList<>());
    }

    public synchronized kx.c leaseConnection(Server s) throws IOException, K4Exception {
        List<kx.c> list =  freeMap.computeIfAbsent(s, k -> new LinkedList<>());
        List<kx.c> dead = new LinkedList<>();

        kx.c c = null;
        for (kx.c value : list) {
            if (!value.isClosed()) {
                c = value;
                break;
            }
            dead.add(c);
        }

        list.removeAll(dead);

        if (c == null) {
            try {
                String mech = s.getAuthenticationMechanism();
                Class<?> clazz = AuthenticationManager.getInstance().lookup(mech);
                if(clazz == null) throw new RuntimeException("Can't find authentication mechanism: "+mech);
                IAuthenticationMechanism authenticationMechanism = (IAuthenticationMechanism) clazz.newInstance();

                authenticationMechanism.setProperties(s.getAsProperties());
                Credentials credentials = authenticationMechanism.getCredentials();
                if (credentials.getUsername().length() > 0) {
                    String p = credentials.getPassword();
                    c = new kx.c(s.getHost(), s.getPort(), credentials.getUsername() + ((p.length() == 0) ? "" : ":" + p), s.getUseTLS());
                } else {
                    c = new kx.c(s.getHost(), s.getPort(), "", s.getUseTLS());
                }
                c.setEncoding(Config.getInstance().getEncoding());
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException ex) {
                log.error("Failed to initialize connection", ex);
                return null;
            }
        } else {
            list.remove(c);
        }

        list = busyMap.computeIfAbsent(s, k -> new LinkedList<>());
        list.add(c);

        return c;
    }

    public synchronized void freeConnection(Server s,kx.c c) {
        if (c == null) return;

        List<kx.c> list = busyMap.computeIfAbsent(s, k -> new LinkedList<>());

        // If c not in our busy list it has been purged, so close it
        if (!list.remove(c))
            c.close();

        if (!c.isClosed()) {
            list = freeMap.get(s);
            if (list == null)
                c.close();
            else
                list.add(c);
        }
    }

}
