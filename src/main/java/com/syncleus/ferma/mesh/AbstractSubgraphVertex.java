package com.syncleus.ferma.mesh;

import com.syncleus.ferma.AbstractVertexFrame;

import java.util.Iterator;

public abstract class AbstractSubgraphVertex extends AbstractVertexFrame implements SubgraphVertex {
  @Override
  public Iterator<MeshEdge> getOutMeshEdges(Object inId) {
    return this.outE("links").has("inId", inId).frame(MeshEdge.class).iterator();
  }

  @Override
  public Iterator<MeshEdge> getInMeshEdges(Object outId) {
    return this.inE("links").has("outId", outId).frame(MeshEdge.class).iterator();
  }
}
