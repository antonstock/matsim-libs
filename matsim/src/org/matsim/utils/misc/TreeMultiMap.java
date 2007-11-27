/* *********************************************************************** *
 * project: org.matsim.*
 * TreeMultiMap.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.utils.misc;

import java.util.Comparator;
import java.util.TreeMap;
import java.util.Vector;

/**
 * @author mrieser
 *
 * A MultiMap implementation using internally a TreeMap
 * @param <K> The type of keys in the TreeMultiMap.
 * @param <V> The type of values in the TreeMultiMap.
 */
public class TreeMultiMap<K, V> implements MultiMap<K, V> {

	private final TreeMap<K, Entry<V>> map;

	public TreeMultiMap() {
		map = new TreeMap<K, Entry<V>>();
	}

	public TreeMultiMap(Comparator<K> c) {
		map = new TreeMap<K, Entry<V>>(c);
	}

	/**
	 * @param key
	 * @param value
	 * @return the inserted <pre>value</pre>
	 */
	public V put(K key, V value) {
		Entry<V> entry = map.get(key);
		if (entry == null) {
			// create a new entry and add the value to it
			entry = new Entry<V>();
			entry.data.add((V)value);
			map.put((K)key, entry);
		} else {
			// add the value to the existing entry
			entry.data.add((V)value);
		}
		return value;
	}

	public K firstKey() {
		return map.firstKey();
	}

	public V remove(K key) {
		Entry<V> entry = map.get(key);
		V value = entry.data.lastElement();
		entry.data.remove(entry.data.size() - 1);
		if (entry.data.isEmpty()) {
			map.remove(key);
		}
		return value;
	}

	static class Entry<VV> {
		public final Vector<VV> data = new Vector<VV>();
	}

}
