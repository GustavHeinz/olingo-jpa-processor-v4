package org.apache.olingo.jpa.metadata.core.edm.mapper.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.CsdlTerm;
import org.apache.olingo.commons.api.edmx.EdmxReference;
import org.apache.olingo.commons.api.edmx.EdmxReferenceInclude;
import org.apache.olingo.jpa.metadata.core.edm.mapper.annotation.TermReader;
import org.apache.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;
import org.apache.olingo.jpa.metadata.core.edm.mapper.extention.IntermediateReferenceList;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

class IntermediateReferences implements IntermediateReferenceList {
  final List<IntermediateReference> references = new ArrayList<IntermediateReference>();
  final List<EdmxReference> edmxReferences = new ArrayList<EdmxReference>();
  final Map<String, Map<String, CsdlTerm>> schemas = new HashMap<String, Map<String, CsdlTerm>>();
  final Map<String, String> aliasDirectory = new HashMap<String, String>();

  @Override
  public IntermediateReferenceAccess addReference(final String uri) throws ODataJPAModelException {
    return addReference(uri, null);
  }

  @Override
  public IntermediateReferenceAccess addReference(final String uri, final String path) throws ODataJPAModelException {
    URI sourceURI;
    try {
      sourceURI = new URI(uri);
    } catch (URISyntaxException e) {
      throw new ODataJPAModelException(e);
    }
    IntermediateReference reference = new IntermediateReference(sourceURI, path);
    if (path != null && !path.isEmpty()) {
      try {
        schemas.putAll(new TermReader().getTerms(path));
      } catch (JsonParseException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (JsonMappingException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    references.add(reference);
    return reference;
  }

  public CsdlTerm getTerm(FullQualifiedName termName) {
    Map<String, CsdlTerm> schema = schemas.get(termName.getNamespace());
    if (schema == null) {
      for (IntermediateReference r : references) {
        String namespace = r.convertAlias(termName.getNamespace());
        if (namespace != null) {
          schema = schemas.get(namespace);
        }
      }
    }
    return schema.get(termName.getName());
  }

  List<EdmxReference> getEdmReferences() {
    if (references.size() != edmxReferences.size()) {
      edmxReferences.removeAll(edmxReferences);
      for (IntermediateReference r : references) {
        edmxReferences.add(r.getEdmReference());
      }
    }
    return edmxReferences;
  }

  private class IntermediateReference implements IntermediateReferenceList.IntermediateReferenceAccess {
    final private URI uri;
    final private String path;
    final EdmxReference edmxReference;
    final private List<IntermediateReferenceInclude> includes = new ArrayList<IntermediateReferenceInclude>();

    public IntermediateReference(final URI uri, final String path) {
      super();
      this.uri = uri;
      this.path = path;
      edmxReference = new EdmxReference(uri);
    }

    String convertAlias(String alias) {
      return aliasDirectory.get(alias);
    }

    @Override
    public void addInclude(String namespace) {
      addInclude(namespace, null);
    }

    @Override
    public void addInclude(final String namespace, final String alias) {
      IntermediateReferenceInclude include = new IntermediateReferenceInclude(namespace, alias);
      this.includes.add(include);
      edmxReference.addInclude(include.getEdmInclude());
      aliasDirectory.put(alias, namespace);
    }

    @Override
    public String getPath() {
      return path;
    }

    @Override
    public URI getURI() {
      return uri;
    }

    EdmxReference getEdmReference() {
      return edmxReference;
    }

    private class IntermediateReferenceInclude {
      private final String namespace;
      private final String alias;

      public IntermediateReferenceInclude(final String namespace, final String alias) {
        this.namespace = namespace;
        this.alias = alias;
      }

      EdmxReferenceInclude getEdmInclude() {
        return new EdmxReferenceInclude(namespace, alias);
      }
    }
  }
}
