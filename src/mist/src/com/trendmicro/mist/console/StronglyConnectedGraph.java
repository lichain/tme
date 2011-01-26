package com.trendmicro.mist.console;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

/**
 * This class is used to detect whether a cycle is exists if bridge is used.
 * The algorithm refers to http://en.wikipedia.org/wiki/Tarjan%E2%80%99s_strongly_connected_components_algorithm
 */


public class StronglyConnectedGraph<T> extends JGraph<T> {

	public Stack<JNode<T>> m_stack = new Stack<JNode<T>>();
	private HashMap<JNode<T>, Integer> m_index_map = new HashMap<JNode<T>, Integer>();
	private List<Integer> m_low_links = new ArrayList<Integer>();
	private int m_index = 0;
	private boolean m_is_ssc_found = false;

	public StronglyConnectedGraph() {
		super();
	}

	public StronglyConnectedGraph(JNode<T> root) {
		setRootElement(root);
	}

	public final boolean isStronglyConnected() throws NullPointerException{
		tarjan(getRootElement());
		return m_is_ssc_found;
	}

	private void tarjan(JNode<T> node) throws NullPointerException {
		if (node == null) throw new NullPointerException();

		int t_idx = m_index;
		int low_link = m_index;
		m_index++;

		m_index_map.put(node, new Integer(t_idx));
		m_low_links.add(t_idx, new Integer(low_link));
		m_stack.push(node);

		Iterator<JNode<T>> iter = node.getChildren().iterator();
		while (iter.hasNext()) {
			JNode<T> child = iter.next();
			// Was child visited?
			if (m_index_map.get(child) == null){
				tarjan(child);
				int child_idx = m_index_map.get(child).intValue();
				low_link = java.lang.Math.min(low_link, m_low_links.get(child_idx).intValue());
			} else if (m_stack.contains(child)) {
				int child_idx = m_index_map.get(child).intValue();
				low_link = java.lang.Math.min(low_link, m_low_links.get(child_idx).intValue());
			}
		}

		m_low_links.set(t_idx, new Integer(low_link));

		if (t_idx == low_link) {
			int len = 0;
			JNode<T> v = null;
			//String loop = node.getData() + " <--- ";
			do {
				len++;
				v = m_stack.pop();
				//loop = loop + v.getData() + " <--- ";
			} while(v != node);
			//System.err.println(loop);
			//System.err.println("-----");

			if (len > 1) m_is_ssc_found = true;
		}
	}
}
