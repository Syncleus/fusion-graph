package com.syncleus.ferma.mesh;

import com.syncleus.ferma.VertexFrame;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.util.wrappers.WrapperGraph;

public interface SubgraphVertex extends VertexFrame, WrapperGraph<TransactionalGraph> {
}
