package com.syncleus.ferma.mesh;

import com.syncleus.ferma.ClassInitializer;
import com.syncleus.ferma.FramedTransactionalGraph;
import com.tinkerpop.blueprints.*;

import java.text.ParseException;
import java.util.*;

public class LinkedMeshGraph implements MeshGraph {
  private final FramedTransactionalGraph metagraph;
  private static final Integer DEFAULT_READ_SUBGRAPH_ACCESS_PER_CLEAN = null;
  private final Integer readSubgraphAccessPerClean;
  private int remainingReadSubgraphAccessCount;

  private Object writeSubgraphId = null;
  private final Set<Object> unreadableSubgraphIds = new HashSet<>();
  private final Map<Object, TransactionalGraph> cachedSubgraphs = new HashMap<>();

  public LinkedMeshGraph(final FramedTransactionalGraph metagraph) {
    if(metagraph == null)
      throw new IllegalArgumentException("metagraph can not be null");

    this.metagraph = metagraph;
    this.readSubgraphAccessPerClean = DEFAULT_READ_SUBGRAPH_ACCESS_PER_CLEAN;
    this.remainingReadSubgraphAccessCount = 0;
  }

  public LinkedMeshGraph(final FramedTransactionalGraph metagraph, final int readSubgraphAccessPerClean) {
    if(metagraph == null)
      throw new IllegalArgumentException("metagraph can not be null");
    if(readSubgraphAccessPerClean < 1 )
      throw new IllegalArgumentException("readSubgraphAccessPerClean must be 1 or greater");

    this.metagraph = metagraph;
    this.readSubgraphAccessPerClean = readSubgraphAccessPerClean;
    this.remainingReadSubgraphAccessCount = this.readSubgraphAccessPerClean;
  }

  @Override
  public <G extends SubgraphVertex> Object addSubgraph(Class<G> subgraphType) {
    try {
      final G subgraphVertex = this.getRawGraph().addFramedVertex(subgraphType);
      return subgraphVertex.getId();
    }
    finally {
      this.getRawGraph().commit();
    }
  }

  @Override
  public <G extends SubgraphVertex> Object addSubgraph(ClassInitializer<G> subgraphInitializer) {
    try {
      final G subgraphVertex = this.getRawGraph().addFramedVertex(subgraphInitializer);
      return subgraphVertex.getId();
    }
    finally {
      this.getRawGraph().commit();
    }
  }

  @Override
  public void removeSubgraph(final Object subgraphName) {
    try {
      //subgraphName should be unique, so this should only get called once, the loop is incase transactions aren't locking
      //as expected and as such the name was set multiple times.
      this.getRawGraph().getVertex(subgraphName).remove();
    }
    finally {
      this.getRawGraph().commit();
    }
  }

  @Override
  public Iterator<?> iterateSubgraphIds() {
    return new Iterator<Object>(){
      final Iterator<? extends SubgraphVertex> vertexIterator = getRawGraph().getFramedVertices(SubgraphVertex.class).iterator();

      @Override
      public boolean hasNext() {
        return this.vertexIterator.hasNext();
      }

      @Override
      public String next() {
        return this.vertexIterator.next().getId();
      }

      @Override
      public void remove() {
        this.vertexIterator.remove();
      }
    };
  }

  @Override
  public FramedTransactionalGraph getRawGraph() {
    return this.metagraph;
  }

  @Override
  public Features getFeatures() {
    return this.featureMerger();
  }

  @Override
  public void reloadSubgraphs() {
  }

  @Override
  public boolean addReadSubgraph(Object subgraphId) {
    return this.unreadableSubgraphIds.remove(subgraphId);
  }

  @Override
  public boolean removeReadSubgraph(Object subgraphId) {
    if( this.writeSubgraphId.equals(subgraphId) )
      throw new IllegalArgumentException("the write subgraph can not be removed from the list of readable subgraphs");
    try {
      if (!this.isSubgraphIdUsed(subgraphId))
        throw new IllegalArgumentException("subgraphId does not exist");

      this.cleanCheckReadSubgraph();

      return this.unreadableSubgraphIds.add(subgraphId);
    }
    finally {
      this.getRawGraph().commit();
    }
  }

  @Override
  public boolean isReadSubgraph(Object subgraphId) {
    try {
      if (!this.isSubgraphIdUsed(subgraphId))
        throw new IllegalArgumentException("subgraphId does not exist");

      this.cleanCheckReadSubgraph();

      return (!this.unreadableSubgraphIds.contains(subgraphId));
    }
    finally {
      this.getRawGraph().commit();
    }
  }

  @Override
  public Iterator<Object> iterateReadSubgraphIds() {
    return new Iterator<Object>(){
      Iterator<? extends SubgraphVertex> vertexIterator = null;
      Object queuedSubgraphId = null;

      private void initializeIterator() {
        this.vertexIterator = getRawGraph().getFramedVertices(SubgraphVertex.class).iterator();
        this.advanceQueue();
      }

      private void advanceQueue() {
        while(this.vertexIterator.hasNext()) {
          final Object nextSubgraphId = this.vertexIterator.next().getId();
          if( !unreadableSubgraphIds.contains(nextSubgraphId) ) {
            this.queuedSubgraphId = nextSubgraphId;
            return;
          }
        }
        this.queuedSubgraphId = null;
      }

      @Override
      public boolean hasNext() {
        if( this.vertexIterator == null )
          this.initializeIterator();

        return (this.queuedSubgraphId != null);
      }

      @Override
      public Object next() {
        if( this.vertexIterator == null )
          this.initializeIterator();

        //if queuedSubgraph is null then we should throw an exception, we do this by calling next on the vertexIterator
        //this ensures the type and message of the exception is preserved.
        if(this.queuedSubgraphId == null) {
          this.vertexIterator.next();
          //this should never be reached
          assert false;
        }

        final Object nextId = this.queuedSubgraphId;
        this.advanceQueue();
        return nextId;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("Can not remove read subgraphs from the iterator directly");
      }
    };
  }

  @Override
  public void setWriteSubgraph(Object subgraphId) {
    try {
      if (!this.isSubgraphIdUsed(subgraphId))
        throw new IllegalArgumentException("subgraphId does not exist");

      this.unreadableSubgraphIds.remove(subgraphId);
      this.writeSubgraphId = subgraphId;
    }
    finally {
      this.getRawGraph().commit();
    }
  }

  @Override
  public Object getWriteSubgraphId() {
    return null;
  }

  @Override
  public void moveVertex(Vertex vertex, Object subgraphId) {
  }

  @Override
  public Vertex addVertex(Object id) {
    return null;
  }

  @Override
  public Vertex getVertex(Object id) {
    return null;
  }

  @Override
  public void removeVertex(Vertex vertex) {

  }

  @Override
  public Iterable<Vertex> getVertices() {
    return null;
  }

  @Override
  public Iterable<Vertex> getVertices(String key, Object value) {
    return null;
  }

  @Override
  public Edge addEdge(Object id, Vertex outVertex, Vertex inVertex, String label) {
    return null;
  }

  @Override
  public Edge getEdge(Object id) {
    return null;
  }

  @Override
  public void removeEdge(Edge edge) {

  }

  @Override
  public Iterable<Edge> getEdges() {
    return null;
  }

  @Override
  public Iterable<Edge> getEdges(String key, Object value) {
    return null;
  }

  @Override
  public GraphQuery query() {
    return null;
  }

  @Override
  public void shutdown() {

  }

  public void cleanReadableSubgraphs() {
    //make sure there arent any stale subgraphIds, they do any harm but they do take up memory
    final Iterator<Object> idIterator = this.unreadableSubgraphIds.iterator();
    while(idIterator.hasNext()) {
      final Object id = idIterator.next();
      if( ! this.isSubgraphIdUsed(id) )
        idIterator.remove();
    }
  }

  private void cleanCheckReadSubgraph() {
    if( this.readSubgraphAccessPerClean != null ) {
      this.remainingReadSubgraphAccessCount--;
      if (this.remainingReadSubgraphAccessCount <= 0) {
        this.cleanReadableSubgraphs();

        this.remainingReadSubgraphAccessCount = this.readSubgraphAccessPerClean;
      }
    }
  }

  private boolean isSubgraphIdUsed(final Object id) {
    return (this.getRawGraph().getVertex(id) != null);
  }

  private Features featureMerger() {
    final Features features = new Features();

    features.isWrapper = true;

    features.hasImplicitElements = false;
    features.ignoresSuppliedIds = true;
    features.isPersistent = false;
    features.supportsBooleanProperty = false;
    features.supportsDoubleProperty = false;
    features.supportsDuplicateEdges = false;
    features.supportsEdgeIndex = false;
    features.supportsEdgeIteration = false;
    features.supportsEdgeKeyIndex = false;
    features.supportsEdgeProperties = false;
    features.supportsEdgeRetrieval = false;
    features.supportsFloatProperty = false;
    features.supportsIndices = false;
    features.supportsIntegerProperty = false;
    features.supportsKeyIndices = false;
    features.supportsLongProperty = false;
    features.supportsMapProperty = false;
    features.supportsMixedListProperty = false;
    features.supportsPrimitiveArrayProperty = false;
    features.supportsSelfLoops = false;
    features.supportsSerializableObjectProperty = false;
    features.supportsStringProperty = false;
    features.supportsThreadedTransactions = false;
    features.supportsThreadIsolatedTransactions = false;
    features.supportsTransactions = false;
    features.supportsUniformListProperty = false;
    features.supportsVertexIndex = false;
    features.supportsVertexIteration = false;
    features.supportsVertexKeyIndex = false;
    features.supportsVertexProperties = false;

    final Iterator<? extends SubgraphVertex> subgraphIterator = this.getRawGraph().getFramedVertices(SubgraphVertex.class).iterator();

    while(subgraphIterator.hasNext()) {
      final Features subfeatures = subgraphIterator.next().getBaseGraph().getFeatures();
      if(subfeatures.hasImplicitElements == true )
        features.hasImplicitElements = true;
      if(subfeatures.ignoresSuppliedIds == false )
        features.ignoresSuppliedIds = false;
      if(subfeatures.isPersistent == true )
        features.isPersistent = true;
      if(subfeatures.supportsBooleanProperty == true )
        features.supportsBooleanProperty = true;
      if(subfeatures.supportsDoubleProperty == true )
        features.supportsDoubleProperty = true;
      if(subfeatures.supportsDuplicateEdges == true )
        features.supportsDuplicateEdges = true;
      if(subfeatures.supportsEdgeIndex == true )
        features.supportsEdgeIndex = true;
      if(subfeatures.supportsEdgeIteration == true )
        features.supportsEdgeIteration = true;
      if(subfeatures.supportsEdgeKeyIndex == true )
        features.supportsEdgeKeyIndex = true;
      if(subfeatures.supportsEdgeProperties == true )
        features.supportsEdgeProperties = true;
      if(subfeatures.supportsEdgeRetrieval == true )
        features.supportsEdgeRetrieval = true;
      if(subfeatures.supportsFloatProperty == true )
        features.supportsFloatProperty = true;
      if(subfeatures.supportsIndices == true )
        features.supportsIndices = true;
      if(subfeatures.supportsIntegerProperty == true )
        features.supportsIntegerProperty = true;
      if(subfeatures.supportsKeyIndices == true )
        features.supportsKeyIndices = true;
      if(subfeatures.supportsLongProperty == true )
        features.supportsLongProperty = true;
      if(subfeatures.supportsMapProperty == true )
        features.supportsMapProperty = true;
      if(subfeatures.supportsMixedListProperty == true )
        features.supportsMixedListProperty = true;
      if(subfeatures.supportsPrimitiveArrayProperty == true )
        features.supportsPrimitiveArrayProperty = true;
      if(subfeatures.supportsSelfLoops == true )
        features.supportsSelfLoops = true;
      if(subfeatures.supportsSerializableObjectProperty == true )
        features.supportsSerializableObjectProperty = true;
      if(subfeatures.supportsStringProperty == true )
        features.supportsStringProperty = true;
      if(subfeatures.supportsThreadedTransactions == true )
        features.supportsThreadedTransactions = true;
      if(subfeatures.supportsThreadIsolatedTransactions == true )
        features.supportsThreadIsolatedTransactions = true;
      if(subfeatures.supportsTransactions == true )
        features.supportsTransactions = true;
      if(subfeatures.supportsUniformListProperty == true )
        features.supportsUniformListProperty = true;
      if(subfeatures.supportsVertexIndex == true )
        features.supportsVertexIndex = true;
      if(subfeatures.supportsVertexIteration == true )
        features.supportsVertexIteration = true;
      if(subfeatures.supportsVertexKeyIndex == true )
        features.supportsVertexKeyIndex = true;
      if(subfeatures.supportsVertexProperties == true )
        features.supportsVertexProperties = true;
    }

    return features;
  }
}
