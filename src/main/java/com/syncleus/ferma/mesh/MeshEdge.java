package com.syncleus.ferma.mesh;

import com.syncleus.ferma.annotations.InVertex;
import com.syncleus.ferma.annotations.OutVertex;
import com.syncleus.ferma.annotations.Property;

public interface MeshEdge {
  @Property("inId")
  Object getInId();

  @Property("outId")
  Object getOutId();

  @InVertex
  SubgraphVertex getInSubgraph();

  @OutVertex
  SubgraphVertex getOutSubgraph();
}
