package org.apache.olingo.jpa.processor.core.query;

import java.util.List;

import javax.persistence.Tuple;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAElement;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAEntityType;
import org.apache.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;
import org.apache.olingo.jpa.metadata.core.edm.mapper.impl.ServicDocument;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.uri.UriHelper;

public class JPATupleResultConverter extends JPATupleAbstractConverter {

  public JPATupleResultConverter(final ServicDocument sd, final JPAExpandResult jpaQueryResult,
      final UriHelper uriHelper, final ServiceMetadata serviceMetadata) throws ODataJPAModelException,
      ODataApplicationException {
    super(jpaQueryResult, uriHelper, sd, serviceMetadata);
  }

  public EntityCollection getResult() throws ODataApplicationException {
    final EntityCollection odataEntityCollection = new EntityCollection();
    final List<Entity> odataResults = odataEntityCollection.getEntities();

    for (final Tuple row : jpaQueryResult.getResult("root")) {
      final Entity odataEntity = convertRow(jpaConversionTargetEntity, row);
      try {
        if (jpaConversionTargetEntity.hasStream())
          odataEntity.setMediaContentType(determineContentType(jpaConversionTargetEntity, row));
      } catch (ODataJPAModelException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      odataResults.add(odataEntity);
    }
    return odataEntityCollection;
  }

  private String determineContentType(JPAEntityType jpaEntity, Tuple row) throws ODataJPAModelException {
    if (jpaEntity.getContentType() != null && !jpaEntity.getContentType().isEmpty())
      return jpaEntity.getContentType();
    else {
      Object rowElement = null;
      for (final JPAElement element : jpaEntity.getContentTypeAttributePath().getPath()) {
        rowElement = row.get(element.getExternalName());
      }

      return rowElement.toString();
    }
  }
}
