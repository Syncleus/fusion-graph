package com.syncleus.ferma.mesh;

import com.tinkerpop.blueprints.Graph;

public interface GraphCache<K,G extends Graph> {
  G get(K key);
  boolean refresh(K key);
  void clear();
}
