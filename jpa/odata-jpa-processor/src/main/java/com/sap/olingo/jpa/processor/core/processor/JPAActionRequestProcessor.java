package com.sap.olingo.jpa.processor.core.processor;

import com.sap.olingo.jpa.metadata.core.edm.mapper.api.JPAAction;
import com.sap.olingo.jpa.metadata.core.edm.mapper.api.JPAParameter;
import com.sap.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;
import com.sap.olingo.jpa.processor.core.api.JPAODataCRUDContextAccess;
import com.sap.olingo.jpa.processor.core.api.JPAODataRequestContextAccess;
import com.sap.olingo.jpa.processor.core.exception.ODataJPAProcessorException;
import com.sap.olingo.jpa.processor.core.modify.JPAConversionHelper;
import org.apache.olingo.commons.api.data.Annotatable;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceAction;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * We have overwritten this class to implement new features. Currently, the following changes exist:
 * BAO-609: implement bindRequestQueryParameters to bind URL query parameters to @EdmParameter parameters
 */

public class JPAActionRequestProcessor extends JPAOperationRequestProcessor {
  private static Logger log = Logger.getLogger(JPAActionRequestProcessor.class.getName());

  public JPAActionRequestProcessor(final OData odata, final JPAODataCRUDContextAccess sessionContext,
      final JPAODataRequestContextAccess requestContext) throws ODataException {
    super(odata, sessionContext, requestContext);
  }

  private Map<String, org.apache.olingo.commons.api.data.Parameter> bindRequestQueryParameters(final ODataRequest request, Map<String, org.apache.olingo.commons.api.data.Parameter> actionParameter) {
    Map<String, org.apache.olingo.commons.api.data.Parameter> resultParameters = new HashMap<>(actionParameter);
    String rawQueryPath = request.getRawQueryPath();
    if (rawQueryPath == null) {
      return resultParameters;
    }
    String[] queryParameters = rawQueryPath.split("&");
    for (String queryParameter : queryParameters) {
      String[] parameterParts = queryParameter.split("=");

      String key = "";
      String value = "";
      try {
        key = URLDecoder.decode(parameterParts[0], StandardCharsets.UTF_8.name());
        value = URLDecoder.decode(parameterParts[1], StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
        log.log(Level.WARNING, "Unable to decode query parameters: {}", e);
      }

      org.apache.olingo.commons.api.data.Parameter parameter = new org.apache.olingo.commons.api.data.Parameter();
      parameter.setName(key);
      parameter.setValue(ValueType.PRIMITIVE, value);
      resultParameters.put(key, parameter);
    }
    return resultParameters;
  }

  public void performAction(final ODataRequest request, final ODataResponse response, final ContentType requestFormat)
      throws ODataApplicationException {

    final List<UriResource> resourceList = uriInfo.getUriResourceParts();
    final UriResourceAction resource = (UriResourceAction) resourceList.get(resourceList.size() - 1);
    try {
      final JPAAction jpaAction = sd.getAction(resource.getAction());

      final Object instance = createInstanze(jpaAction.getConstructor());

      final List<Object> parameter = new ArrayList<>();
      final Parameter[] methodParameter = jpaAction.getMethod().getParameters();

      final ODataDeserializer deserializer = odata.createDeserializer(requestFormat);
      Map<String, org.apache.olingo.commons.api.data.Parameter> actionParameter =
          deserializer.actionParameters(request.getBody(), resource.getAction()).getActionParameters();

      actionParameter = bindRequestQueryParameters(request, actionParameter);

      for (int i = 0; i < methodParameter.length; i++) {
        final Parameter declairedParameter = methodParameter[i];
        if (i == 0 && resource.getAction().isBound()) {
          parameter.add(createBindingParameter((UriResourceEntitySet) resourceList.get(resourceList.size() - 2),
              jpaAction.getParameter(declairedParameter)));
        } else {
          // Any nullable parameter values not specified in the request MUST be assumed to have the null value.
          // This is guaranteed by Olingo => no code needed
          final String externalName = jpaAction.getParameter(declairedParameter).getName();
          final org.apache.olingo.commons.api.data.Parameter param = actionParameter.get(externalName);
          if (param != null)
            parameter.add(JPAConversionHelper.convertParameter(param, sd));
          else
            parameter.add(null);
        }
      }
      Annotatable r = null;
      EdmType returnType = null;
      if (resource.getAction().getReturnType() != null) {
        returnType = resource.getAction().getReturnType().getType();
        final Object result = jpaAction.getMethod().invoke(instance, parameter.toArray());
        r = convertResult(result, returnType, jpaAction);
      } else
        jpaAction.getMethod().invoke(instance, parameter.toArray());
      if (serializer != null)
        serializeResult(returnType, response, serializer.getContentType(), r);
      else
        response.setStatusCode(successStatusCode);

    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
      throw new ODataJPAProcessorException(e, HttpStatusCode.INTERNAL_SERVER_ERROR);
    } catch (InvocationTargetException | ODataException e) {
      final Throwable cause = e.getCause();
      if (cause != null && cause instanceof ODataApplicationException) {
        throw (ODataApplicationException) cause;
      } else {
        throw new ODataJPAProcessorException(e, HttpStatusCode.INTERNAL_SERVER_ERROR);
      }
    }
  }

  private Object createBindingParameter(UriResourceEntitySet entitySet, JPAParameter parameter)
      throws ODataJPAModelException, ODataApplicationException {
    try {

      final JPAConversionHelper helper = new JPAConversionHelper();
      final JPAModifyUtil util = new JPAModifyUtil();
      final Constructor<?> c = parameter.getType().getConstructor();
      final Map<String, Object> jpaAttributes = helper.convertUriKeys(odata, sd.getEntity(entitySet.getEntityType()),
          entitySet.getKeyPredicates());
      if (c != null) {
        final Object param = c.newInstance();
        util.setAttributesDeep(jpaAttributes, param, sd.getEntity(entitySet.getEntityType()));
        return param;
      }
    } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
        | IllegalArgumentException e) {
      throw new ODataJPAProcessorException(e, HttpStatusCode.INTERNAL_SERVER_ERROR);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause != null && cause instanceof ODataApplicationException) {
        throw (ODataApplicationException) cause;
      } else {
        throw new ODataJPAProcessorException(e, HttpStatusCode.INTERNAL_SERVER_ERROR);
      }
    }
    return null;
  }

  protected Object createInstanze(final Constructor<?> c) throws InstantiationException, IllegalAccessException,
      InvocationTargetException {
    Object instance;
    if (c.getParameterCount() == 1)
      instance = c.newInstance(em);
    else
      instance = c.newInstance();
    return instance;
  }

}
