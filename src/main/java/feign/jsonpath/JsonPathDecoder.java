/**
 * Copyright (C) 2016 Marvin Herman Froeder (marvin@marvinformatics.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.jsonpath;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import feign.FeignException;
import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import net.minidev.json.JSONArray;

public class JsonPathDecoder implements Decoder {

  private final Configuration configuration;

  public JsonPathDecoder(Configuration configuration) {
    super();
    this.configuration = configuration;
  }

  public JsonPathDecoder() {
    this(Configuration.defaultConfiguration());
  }

  @Override
  public Object decode(Response response, final Type type) throws IOException, DecodeException, FeignException {
    if (response.status() == 404)
      return Util.emptyValueOf(type);
    if (response.body() == null)
      return null;

    final DocumentContext document = JsonPath.parse(response.body().asInputStream(), configuration);
    if (document.read("$.length()") == null)
      return null;
    if (type instanceof ParameterizedType) {
      Class<?> rawType = (Class<?>) ((ParameterizedType) type).getRawType();

      Class<?> destinationClass;
      if (Collection.class.isAssignableFrom(rawType))
        destinationClass = (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0];
      else
        throw new IllegalStateException("Unable to decode " + type);

      JsonSplit jsonSplit = destinationClass.getAnnotation(JsonSplit.class);
      if (jsonSplit == null)
        throw new IllegalStateException("Missing @JsonSplit at " + destinationClass);

      JSONArray array = document.read(jsonSplit.value());
      Collection<Object> destination = createInstanceOf(rawType);
      for (Object jsonElement : array)
        destination.add(newJsonPathProxy(destinationClass, JsonPath.parse(jsonElement)));

      return destination;
    }
    return newJsonPathProxy(type, document);
  }

  private Object newJsonPathProxy(final Type type, final DocumentContext document) {
    return Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[] { (Class<?>) type },
        new JsonExprInvocationHandler(type, document));
  }

  @SuppressWarnings("unchecked")
  private Collection<Object> createInstanceOf(Class<?> rawType) {
    if (!rawType.isInterface() && !Modifier.isAbstract(rawType.getModifiers()))
      try {
      return (Collection<Object>) rawType.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      throw new IllegalStateException("Unable to create an instance of " + rawType);
    }

    if (Set.class.isAssignableFrom(rawType))
      return new HashSet<Object>();

    if (Queue.class.isAssignableFrom(rawType))
      return new LinkedList<Object>();

    return new ArrayList<Object>();
  }

}
