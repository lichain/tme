package com.trendmicro.mist.console;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Tree of Objects of generic type T. The Tree is represented as
 * a single rootElement which points to a List<Node<T>> of children. There is
 * no restriction on the number of children that a particular node may have.
 * This Tree provides a method to serialize the Tree into a List by doing a
 * pre-order traversal. It has several methods to allow easy updation of Nodes
 * in the Tree.
 */
public class JGraph<T> {

    private JNode<T> rootElement;

    /**
     * Default ctor.
     */
    public JGraph() {
        super();
    }

    /**
     * Return the root Node of the tree.
     * @return the root element.
     */
    public JNode<T> getRootElement() {
        return this.rootElement;
    }

    /**
     * Set the root Element for the tree.
     * @param rootElement the root element to set.
     */
    public void setRootElement(JNode<T> rootElement) {
        this.rootElement = rootElement;
    }

    /**
     * Returns the Tree<T> as a List of Node<T> objects. The elements of the
     * List are generated from a pre-order traversal of the tree.
     * @return a List<Node<T>>.
     */
    public List<JNode<T>> toList() {
        List<JNode<T>> list = new ArrayList<JNode<T>>();
        walk(rootElement, list);
        return list;
    }

    public JNode<T> search(JNode<T> node) {
        return rootElement.search(node);
    }

    /**
     * Returns a String representation of the Tree. The elements are generated
     * from a pre-order traversal of the Tree.
     * @return the String representation of the Tree.
     */
    public String toString() {
        return toList().toString();
    }

    /**
     * Walks the Tree in pre-order style. This is a recursive method, and is
     * called from the toList() method with the root element as the first
     * argument. It appends to the second argument, which is passed by reference  * as it recurses down the tree.
     * @param element the starting element.
     * @param list the output of the walk.
     */
    private void walk(JNode<T> element, List<JNode<T>> list) {
        list.add(element);
        for (JNode<T> data : element.getChildren()) {
            walk(data, list);
        }
    }
}
