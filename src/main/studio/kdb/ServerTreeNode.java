package studio.kdb;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Collections;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ServerTreeNode extends DefaultMutableTreeNode {

    public ServerTreeNode() {
        this("");
    }

    public ServerTreeNode(Server server) {
        super(server, false);
    }

    public ServerTreeNode(String folder) {
        super(folder, true);
    }

    public ServerTreeNode copy() {
        if (isFolder()) {
            ServerTreeNode parent = new ServerTreeNode(getFolder());
            for (ServerTreeNode child: childNodes()) {
                parent.add(child.copy());
            }
            return parent;
        } else {
            return new ServerTreeNode(getServer());
        }
    }

    public boolean isFolder() {
        return allowsChildren;
    }

    public Server getServer() {
        if (isFolder()) {
            throw new IllegalArgumentException("This node is not server");
        }
        return (Server)userObject;
    }

    public String getFolder() {
        if (!isFolder()) {
            throw new IllegalArgumentException("This node is server");
        }
        return (String)userObject;
    }

    public ServerTreeNode add(Server server) {
        return add(getChildCount(), server);
    }

    public ServerTreeNode add(String folder) {
        return add(getChildCount(), folder);
    }

    public ServerTreeNode add(int index, Server server) {
        if (!isFolder()) {
            throw new IllegalArgumentException("Server node can be added only to folders");
        }
        ServerTreeNode serverTreeNode = new ServerTreeNode(server);
        insert(serverTreeNode, index);
        return serverTreeNode;
    }

    public ServerTreeNode add(int index, String folder) {
        if (!isFolder()) {
            throw new IllegalArgumentException("Folder node can be added only to folders");
        }
        ServerTreeNode serverTreeNode = new ServerTreeNode(folder);
        insert(serverTreeNode, index);
        return serverTreeNode;
    }

    // Remove direct child
    public boolean remove(Server server) {
        if (!isFolder()) {
            throw new IllegalArgumentException("This is not folder");
        }
        int count = getChildCount();
        for (int index=0; index<count; index++) {
            ServerTreeNode treeNode = getChild(index);
            if (treeNode.isFolder()) continue;
            if (treeNode.getServer().equals(server)) {
                remove(index);
                return true;
            }
        }
        return false;
    }

    public void renameFolder(String newFolderName) {
        if (!isFolder()) {
            throw new IllegalArgumentException("This node is not a folder");
        }

        ServerTreeNode parentNode = (ServerTreeNode) getParent();
        if (parentNode == null) {
            throw new IllegalStateException("Root folder cannot be renamed");
        }

        int index = parentNode.getIndex(this);
        ServerTreeNode newFolderNode = parentNode.add(index, newFolderName);

        for (ServerTreeNode child : childNodes()) {
            newFolderNode.add(child.copy());
        }

        removeFromParent();
    }

    public ServerTreeNode getChild(String folder) {
        for(ServerTreeNode child: childNodes()) {
            if (child.isFolder() && child.getFolder().equals(folder)) return child;
        }
        return null;
    }

    public ServerTreeNode getChild(Server server) {
        for(ServerTreeNode child: childNodes()) {
            if (!child.isFolder() && child.getServer().equals(server)) return child;
        }
        return null;
    }

    public ServerTreeNode getChild(int index) {
        return (ServerTreeNode) getChildAt(index);
    }

    @SuppressWarnings("unchecked")
    public Iterable<ServerTreeNode> childNodes() {
        if (children != null) return (Vector) children;
        return Collections.emptyList();
    }

    // Recursively find ServerTreeNode with passed Server
    public ServerTreeNode findServerNode(Server server) {
        if (isFolder()) {
            for(ServerTreeNode child: childNodes()) {
                ServerTreeNode node = child.findServerNode(server);
                if (node != null) return node;
            }
        } else {
            if (getServer().equals(server)) return this;
        }

        return null;
    }

    // Find in current tree the path from potentially a different tree
    public ServerTreeNode findPath(TreeNode[] nodes) {
        return findPath(nodes, false);
    }

    public ServerTreeNode findPath(TreeNode[] nodes, boolean create) {
        if (nodes.length == 0) return this;

        return findPath(nodes, 0, create);
    }

    private ServerTreeNode findPath(TreeNode[] nodes, int head, boolean create) {
        if (head == nodes.length-1) return this;

        for (ServerTreeNode child: childNodes()) {
            if (! child.theSame((ServerTreeNode)nodes[head+1]) ) continue;
            ServerTreeNode node = child.findPath(nodes, head+1, create);
            if (node != null) return node;
        }
        if (! create) return null;

        ServerTreeNode childToCreate = (ServerTreeNode) nodes[head+1];
        ServerTreeNode child = childToCreate.isFolder() ? add(childToCreate.getFolder()) : add(childToCreate.getServer());
        return child.findPath(nodes, head+1, true);
    }


    public boolean theSame(ServerTreeNode that) {
        if (isFolder()) {
            if (! that.isFolder()) return false;
            return getFolder().equals(that.getFolder());
        } else {
            if (that.isFolder()) return false;
            return getServer().equals(that.getServer());
        }
    }

    public boolean isNodeDescendant(ServerTreeNode node) {
        if (node == null) {
            return false;
        }

        if (node == this) {
            return true;
        }

        return isNodeDescendant((ServerTreeNode) node.getParent());
    }

    @Override
    public String toString() {
        return isFolder() ? getFolder() : getServer().toString();
    }

    public String fullPath() {
        return Stream.of(getPath()).skip(1).map(n->n.toString()).collect(Collectors.joining("/"));
    }

    public String getName() {
        return isFolder() ? getFolder() : getServer().getName();
    }
}
