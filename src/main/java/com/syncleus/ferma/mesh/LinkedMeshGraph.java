package com.syncleus.ferma.mesh;

import com.syncleus.ferma.ClassInitializer;
import com.syncleus.ferma.FramedTransactionalGraph;
import com.tinkerpop.blueprints.*;
import java.util.*;

public class LinkedMeshGraph implements MeshGraph {
  private final FramedTransactionalGraph metagraph;
  private final Set<TransactionalGraph> pendingTransactions = new HashSet<>();

  private Object writeSubgraphId = null;
  private final Set<Object> unreadableSubgraphIds = new HashSet<>();
  private final TreeGraphCache<Object,TransactionalGraph> cachedSubgraphs;

  public LinkedMeshGraph(final FramedTransactionalGraph metagraph) {
    if(metagraph == null)
      throw new IllegalArgumentException("metagraph can not be null");

    this.metagraph = metagraph;
    this.cachedSubgraphs = new MeshGraphCache();
  }

  public LinkedMeshGraph(final FramedTransactionalGraph metagraph, final int maxConnections) {
    if(metagraph == null)
      throw new IllegalArgumentException("metagraph can not be null");
    if(maxConnections < 2 )
      throw new IllegalArgumentException("maxConnections must be 2 or greater");

    this.metagraph = metagraph;
    this.cachedSubgraphs = new MeshGraphCache(maxConnections);
  }

  @Override
  public <G extends SubgraphVertex> Object addSubgraph(Class<G> subgraphType) {
    this.pendingTransactions.add(this.getRawGraph());
    final G subgraphVertex = this.getRawGraph().addFramedVertex(subgraphType);
    return subgraphVertex.getId();
  }

  @Override
  public <G extends SubgraphVertex> Object addSubgraph(ClassInitializer<G> subgraphInitializer) {
    this.pendingTransactions.add(this.getRawGraph());
    final G subgraphVertex = this.getRawGraph().addFramedVertex(subgraphInitializer);
    return subgraphVertex.getId();
  }

  @Override
  public void removeSubgraph(final Object subgraphName) {
    this.pendingTransactions.add(this.getRawGraph());
    //subgraphName should be unique, so this should only get called once, the loop is incase transactions aren't locking
    //as expected and as such the name was set multiple times.
    this.getRawGraph().getVertex(subgraphName).remove();
  }

  @Override
  public Iterator<?> iterateSubgraphIds() {
    this.pendingTransactions.add(this.getRawGraph());
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
  public boolean addReadSubgraph(Object subgraphId) {
    return this.unreadableSubgraphIds.remove(subgraphId);
  }

  @Override
  public boolean removeReadSubgraph(Object subgraphId) {
    if( this.writeSubgraphId.equals(subgraphId) )
      throw new IllegalArgumentException("the write subgraph can not be removed from the list of readable subgraphs");

    this.pendingTransactions.add(this.getRawGraph());

    if (!this.isSubgraphIdUsed(subgraphId))
      throw new IllegalArgumentException("subgraphId does not exist");

    return this.unreadableSubgraphIds.add(subgraphId);
  }

  @Override
  public boolean isReadSubgraph(Object subgraphId) {
    this.pendingTransactions.add(this.getRawGraph());
    if (!this.isSubgraphIdUsed(subgraphId))
      throw new IllegalArgumentException("subgraphId does not exist");

    return (!this.unreadableSubgraphIds.contains(subgraphId));
  }

  @Override
  public Iterator<Object> iterateReadSubgraphIds() {
    this.pendingTransactions.add(this.getRawGraph());
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
    this.pendingTransactions.add(this.getRawGraph());

    if (!this.isSubgraphIdUsed(subgraphId))
      throw new IllegalArgumentException("subgraphId does not exist");

    this.unreadableSubgraphIds.remove(subgraphId);
    this.writeSubgraphId = subgraphId;
  }

  @Override
  public Object getWriteSubgraphId() {
    this.pendingTransactions.add(this.getRawGraph());

    if (!this.isSubgraphIdUsed(this.writeSubgraphId))
      this.writeSubgraphId = null;

    return this.writeSubgraphId;
  }

  @Override
  public void moveVertex(Vertex vertex, Object subgraphId) {
  }

  @Override
  public Vertex addVertex(Object id) {
    if( (id != null) && !(id instanceof MeshId) )
      throw new IllegalArgumentException("id must either be null or a MeshId");

    //this is safe even if id is null
    final MeshId meshId = (MeshId) id;
    Object subgraphVertexId;
    TransactionalGraph writeGraph;
    if(meshId == null) {
      if( this.writeSubgraphId == null )
        throw new IllegalStateException("No default wriatable graph is specified, either pass in a MeshId or set a writable graph.");

      subgraphVertexId = null;
      writeGraph = this.cachedSubgraphs.get(this.writeSubgraphId);
    }
    else {
      subgraphVertexId = meshId.getSubgraphVertexId();
      writeGraph = this.cachedSubgraphs.get(meshId.getSubgraphId());
    }

    this.pendingTransactions.add(writeGraph);
    return writeGraph.addVertex(subgraphVertexId);
  }

  @Override
  public Vertex getVertex(Object id) {
    if( !(id instanceof MeshId) )
      return null;

    final MeshId meshId = (MeshId) id;
    final TransactionalGraph readGraph = this.cachedSubgraphs.get(((MeshId) id).getSubgraphId());
    this.pendingTransactions.add(readGraph);
    return readGraph.getVertex(meshId.getSubgraphVertexId());
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

  @Override
  public void stopTransaction(Conclusion conclusion) {
    for(TransactionalGraph pendingTransaction : this.pendingTransactions)
      pendingTransaction.stopTransaction(conclusion);
    this.pendingTransactions.clear();
  }

  @Override
  public void commit() {
    //TODO : What happens when some commits succeeded and then one of the last ones throws an exception?
    for(TransactionalGraph pendingTransaction : this.pendingTransactions)
      pendingTransaction.commit();
    this.pendingTransactions.clear();
  }

  @Override
  public void rollback() {
    for(TransactionalGraph pendingTransaction : this.pendingTransactions)
      pendingTransaction.rollback();
    this.pendingTransactions.clear();
  }

  @Override
  public void resync() {
  }

  @Override
  public void clearConnectionCache() {
    this.cachedSubgraphs.clear();
  }

  @Override
  public void cleanStaleSubgraphIds() {
    //make sure there arent any stale subgraphIds, they do any harm but they do take up memory
    final Iterator<Object> idIterator = this.unreadableSubgraphIds.iterator();
    while(idIterator.hasNext()) {
      final Object id = idIterator.next();
      if( ! this.isSubgraphIdUsed(id) )
        idIterator.remove();
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

  private static class NestedVertex implements Vertex {
    private final Object parentId;
    private final Vertex delegate;

    public NestedVertex(final Vertex delegate, final Object parentId) {
      this.delegate = delegate;
      this.parentId = parentId;
    }

    @Override
    public Iterable<Edge> getEdges(Direction direction, String... labels) {
      return delegate.getEdges(direction, labels);
    }

    @Override
    public Iterable<Vertex> getVertices(Direction direction, String... labels) {
      return delegate.getVertices(direction, labels);
    }

    @Override
    public VertexQuery query() {
      return delegate.query();
    }

    @Override
    public Edge addEdge(String label, Vertex inVertex) {
      return delegate.addEdge(label, inVertex);
    }

    @Override
    public <T> T getProperty(String key) {
      return delegate.getProperty(key);
    }

    @Override
    public Set<String> getPropertyKeys() {
      return delegate.getPropertyKeys();
    }

    @Override
    public void setProperty(String key, Object value) {
      delegate.setProperty(key, value);
    }

    @Override
    public <T> T removeProperty(String key) {
      return delegate.removeProperty(key);
    }

    @Override
    public void remove() {
      delegate.remove();
    }

    @Override
    public MeshId getId() {
      // TODO: Do we want to cache this?
      return new NestedId(parentId, delegate.getId());
    }

    private static class NestedId implements MeshId {
      private final Object subgraphId;
      private final Object subgraphVertexId;

      public NestedId(final Object subgraphId, final Object subgraphVertexId) {
        this.subgraphId = subgraphId;
        this.subgraphVertexId = subgraphVertexId;
      }

      @Override
      public Object getSubgraphId() {
        return this.subgraphId;
      }

      @Override
      public Object getSubgraphVertexId() {
        return this.subgraphVertexId;
      }

      @Override
      public int hashCode() {
        return Objects.hash(subgraphId, subgraphVertexId);
      }

      @Override
      public boolean equals(Object o) {
        if( this == o )
          return true;

        if(!(o instanceof NestedId))
          return false;

        final NestedId otherId = (NestedId) o;
        if(! subgraphId.equals(otherId.subgraphId))
          return false;
        else if(! subgraphVertexId.equals(otherId.subgraphVertexId))
          return false;
        else
          return true;
      }

      @Override
      public String toString() {
        return new StringBuilder(subgraphId.toString()).append(".").append(subgraphVertexId).toString();
      }
    };
  };

  private class MeshGraphCache extends TreeGraphCache<Object,TransactionalGraph> {
    public MeshGraphCache() {
    }

    public MeshGraphCache(Integer maxGraphs) {
      super(maxGraphs);
    }

    @Override
    protected TransactionalGraph constructGraph(Object key) {
      pendingTransactions.add(getRawGraph());
      SubgraphVertex subgraphVertex = getRawGraph().getFramedVertices("id", key, SubgraphVertex.class).iterator().next();
      return subgraphVertex.getBaseGraph();
    }
  };
}
