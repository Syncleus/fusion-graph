package com.syncleus.ferma.mesh;

import com.syncleus.ferma.AbstractVertexFrame;
import com.tinkerpop.gremlin.Tokens;

import java.util.Iterator;

public abstract class AbstractSubgraphVertex extends AbstractVertexFrame implements SubgraphVertex {
  @Override
  public Iterator<MeshEdge> getOutMeshEdges(Object inId, String... labels) {
    return this.outE("links").has("inId", inId).has("sublabel", Tokens.T.in, labels).frame(MeshEdge.class).iterator();
  }

  @Override
  public Iterator<MeshEdge> getInMeshEdges(Object outId, String... labels) {
    return this.inE("links").has("outId", outId).has("sublabel", Tokens.T.in, labels).frame(MeshEdge.class).iterator();
  }
}
