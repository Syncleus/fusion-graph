package com.syncleus.ferma.mesh;

import com.tinkerpop.blueprints.Graph;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class TreeGraphCache<K,G extends Graph> implements GraphCache<K,G> {
  private final Map<K,G> cachedMap = new LinkedHashMap<>();
  private final Integer maxGraphs;

  public TreeGraphCache() {
    this.maxGraphs = null;
  }

  public TreeGraphCache(Integer maxGraphs) {
    //get logic would break if maxGraphs were 1
    if( maxGraphs < 2 )
      throw new IllegalArgumentException("maxGraphs argument must be 2 or greater");

    this.maxGraphs = maxGraphs;
  }

  @Override
  public final G get(K key) {
    G graph = this.cachedMap.remove(key);
    if(graph == null) {
      graph = this.constructGraph(key);
      if( (this.maxGraphs != null) && (this.cachedMap.size() >= this.maxGraphs - 1) )
        this.removeOldestGraph();
    }
    this.cachedMap.put(key, graph);
    return graph;
  }

  @Override
  public final boolean refresh(K key) {
    final G graph = this.cachedMap.remove(key);
    if( graph == null )
      return false;
    this.cachedMap.put(key, graph);
    return true;
  }

  @Override
  public final void clear() {
    final Iterator<Map.Entry<K,G>> cachedIterator = this.cachedMap.entrySet().iterator();
    while(cachedIterator.hasNext()) {
      final Map.Entry<K,G> cachedEntry = cachedIterator.next();
      cachedIterator.remove();
      cachedEntry.getValue().shutdown();
    }
  }

  private final void removeOldestGraph() {
    final Iterator<Map.Entry<K,G>> cachedIterator = this.cachedMap.entrySet().iterator();
    final Map.Entry<K,G> cachedEntry = cachedIterator.next();
    cachedEntry.getValue().shutdown();
    cachedIterator.remove();
  }

  protected abstract G constructGraph(K key);
}
