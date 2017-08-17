package com.sap.olingo.jpa.metadata.core.edm.mapper.api;

import java.util.List;

import org.apache.olingo.commons.api.edm.EdmFunction;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityContainer;
import org.apache.olingo.commons.api.edm.provider.CsdlSchema;
import org.apache.olingo.commons.api.edm.provider.CsdlTerm;
import org.apache.olingo.commons.api.edmx.EdmxReference;

import com.sap.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;

public interface JPAServiceDocument {

  CsdlEntityContainer getEdmEntityContainer() throws ODataJPAModelException;

  List<CsdlSchema> getEdmSchemas() throws ODataJPAModelException;

  List<CsdlSchema> getAllSchemas() throws ODataJPAModelException;

  /**
   * 
   * @param edmType
   * @return
   * @throws ODataJPAModelException
   */
  JPAEntityType getEntity(EdmType edmType) throws ODataJPAModelException;

  JPAEntityType getEntity(FullQualifiedName typeName);

  JPAEntityType getEntity(String edmEntitySetName) throws ODataJPAModelException;

  JPAFunction getFunction(EdmFunction function);

  JPAEntitySet getEntitySet(JPAEntityType entityType) throws ODataJPAModelException;

  List<EdmxReference> getReferences();

  CsdlTerm getTerm(FullQualifiedName termName);

}