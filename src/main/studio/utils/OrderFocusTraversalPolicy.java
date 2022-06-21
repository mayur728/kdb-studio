package studio.utils;

import java.awt.Component;
import java.awt.Container;
import java.awt.FocusTraversalPolicy;
import java.util.Vector;

public class OrderFocusTraversalPolicy
              extends FocusTraversalPolicy
{
    Vector<Component> order;

    public OrderFocusTraversalPolicy(Vector<Component> order) {
        this.order = new Vector<Component>(order.size());
        this.order.addAll(order);
    }

    private int getComponentIndex(Component aComponent) {
        int compIdx = order.indexOf(aComponent);
        if (compIdx == -1) {
            //e.g. when the focused component is the edit box of a combobox
            aComponent = aComponent.getParent();
            compIdx = order.indexOf(aComponent);
        }
        return compIdx;
    }

    public Component getComponentAfter(Container focusCycleRoot,
                                       Component aComponent)
    {
        int idx = getComponentIndex(aComponent);
        idx = (idx + 1) % order.size();
        aComponent = order.get(idx);
        while (!aComponent.isVisible()) {
            idx = (idx + 1) % order.size();
            aComponent = order.get(idx);
        }
        return aComponent;
    }

    public Component getComponentBefore(Container focusCycleRoot,
                                        Component aComponent)
    {
        int idx = getComponentIndex(aComponent);
        idx = Math.floorMod(idx-1, order.size());
        aComponent = order.get(idx);
        while (!aComponent.isVisible()) {
            idx = Math.floorMod(idx-1, order.size());
            aComponent = order.get(idx);
        }
        return aComponent;
    }

    public Component getDefaultComponent(Container focusCycleRoot) {
        return order.get(0);
    }

    public Component getLastComponent(Container focusCycleRoot) {
        return order.lastElement();
    }

    public Component getFirstComponent(Container focusCycleRoot) {
        return order.get(0);
    }
}
