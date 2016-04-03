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

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidJsonException;

public class JsonExprInvocationHandler implements InvocationHandler {

  private final Type type;
  private final DocumentContext document;

  public JsonExprInvocationHandler(Type type, DocumentContext document) {
    this.type = type;
    this.document = document;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if ("equals".equals(method.getName())) {
      return equal(args);
    } else if ("hashCode".equals(method.getName())) {
      return document.hashCode() + type.hashCode();
    } else if ("toString".equals(method.getName())) {
      return toString();
    }

    if ("context".equals(method.getName()) && method.getReturnType().equals(DocumentContext.class))
      return document;

    method.setAccessible(true);
    if (isDefaultMethod(method)) {
      return invokeJava8DefaultMethod(proxy, method, args);
    }

    JsonExpr path = method.getAnnotation(JsonExpr.class);
    if (path == null)
      throw new IllegalStateException("Method not annotated with @JsonExpr " + method);

    return document.read(path.value());
  }

  public String toString() {
    try {
      return type + ": " + document.jsonString();
    } catch (InvalidJsonException e) {
      return type + ": " + document.toString() + " " + e;
    }
  }

  public Object invokeJava8DefaultMethod(Object proxy, Method method, Object[] args) throws NoSuchMethodException,
      Throwable, IllegalAccessException, InstantiationException, InvocationTargetException {
    Class<?> declaringClass = method.getDeclaringClass();
    final Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class
        .getDeclaredConstructor(Class.class, int.class);
    if (!constructor.isAccessible()) {
      constructor.setAccessible(true);
    }

    return constructor.newInstance(declaringClass, MethodHandles.Lookup.PRIVATE)
        .unreflectSpecial(method, declaringClass)
        .bindTo(proxy)
        .invokeWithArguments(args);
  }

  private boolean isDefaultMethod(Method method) {
    return ((method.getModifiers() & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) == Modifier.PUBLIC) &&
        method.getDeclaringClass().isInterface();
  }

  private Object equal(Object[] args) {
    try {
      Object otherHandler = args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
      if (otherHandler instanceof JsonExprInvocationHandler) {
        JsonExprInvocationHandler other = (JsonExprInvocationHandler) otherHandler;
        return document.equals(other.document);
      }
      return false;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

}
