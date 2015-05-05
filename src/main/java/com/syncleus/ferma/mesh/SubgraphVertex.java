package com.syncleus.ferma.mesh;

import com.syncleus.ferma.VertexFrame;
import com.syncleus.ferma.annotations.Incidence;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.util.wrappers.WrapperGraph;

import java.util.Iterator;

public interface SubgraphVertex extends VertexFrame, WrapperGraph<TransactionalGraph> {
  /**
   * This must be implemented by the consumer. This method should construct a new connection to the subgraph represented
   * by this vertex and return it as a TransactionalGraph.
   *
   * @return A new connection to a TransactionalGraph
   */
  @Override
  TransactionalGraph getBaseGraph();

  Iterator<MeshEdge> getOutMeshEdges(Object sourceId, String... labels);

  Iterator<MeshEdge> getInMeshEdges(Object targetId, String... labels);
}
