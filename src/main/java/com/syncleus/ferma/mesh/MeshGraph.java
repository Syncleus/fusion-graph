package com.syncleus.ferma.mesh;

import com.syncleus.ferma.ClassInitializer;
import com.syncleus.ferma.FramedTransactionalGraph;
import com.tinkerpop.blueprints.MetaGraph;
import com.tinkerpop.blueprints.Vertex;

import java.util.Iterator;

/**
 * Combines several graphs, represented as subgraphs, into one combined graph. Also allows for edges to connected
 * vertexes across the subgraphs.
 */
public interface MeshGraph extends MetaGraph<FramedTransactionalGraph> {
  /**
   * This retreives the underlieing metagraph describing the linkage between subgraphs. Each vertex of the metagraph
   * describes one of the subgraphs, and each edge is a link between the subgraphs. The returned graph is read-only.
   *
   * @return The metagraph describing the linkage between subgraphs.
   */
  @Override
  FramedTransactionalGraph getRawGraph();

  <G extends SubgraphVertex> Object addSubgraph(Class<G> subgraphType);
  <G extends SubgraphVertex> Object addSubgraph(ClassInitializer<G> subgraphInitializer);
  void removeSubgraph(Object subgraphId);
  Iterator<?> iterateSubgraphIds();
  void moveVertex(Vertex vertex, Object subgraphId);
  void reloadSubgraphs();
  boolean addReadSubgraph(Object subgraphId);
  boolean removeReadSubgraph(Object subgraphId);
  boolean isReadSubgraph(Object subgraphId);
  Iterator<Object> iterateReadSubgraphIds();

  /**
   * Sets the subgraph with the specified id to being the active subgraph written to when adding vertexes. This does not
   * apply to added edges. Also if the subgraph is not currently in the list of readable subgraphs it will automatically
   * be added.
   *
   * @param subgraphId The id of the graph to make the active subgraph vertexes are written to.
   */
  void setWriteSubgraph(Object subgraphId);
  Object getWriteSubgraphId();
}
