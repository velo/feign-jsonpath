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

import static feign.Util.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.jayway.jsonpath.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import feign.Response;

public class JsonPathDecoderTest {

  private String zonesJson = "[{\"name\":\"denominator.io.1\"},{\"name\":\"denominator.io.2\",\"id\":\"ABCD\"}]";

  @Test
  public void decodes() throws Exception {
    Response response = Response.create(200, "OK", Collections.<String, Collection<String>> emptyMap(), zonesJson, UTF_8);
    ZoneInfo zone = (ZoneInfo) new JsonPathDecoder().decode(response, ZoneInfo.class);
    assertNotNull(zone);
    assertEquals(Arrays.asList("denominator.io.1", "denominator.io.2"), zone.names());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void decodeSpliting() throws Exception {
    Response response = Response.create(200, "OK", Collections.<String, Collection<String>> emptyMap(), zonesJson, UTF_8);
    List<Zone> zones = (List<Zone>) new JsonPathDecoder().decode(response, new TypeRef<List<Zone>>() {
    }.getType());
    assertNotNull(zones);
    assertEquals(2, zones.size());
    Zone zone1 = zones.get(0);
    try {
      assertNull(zone1.id());
      fail();
    } catch (PathNotFoundException e) {
      assertEquals("No results for path: $['id']", e.getMessage());
    }
    assertEquals("denominator.io.1", zone1.names());
    Zone zone2 = zones.get(1);
    assertEquals("ABCD", zone2.id());
    assertEquals("denominator.io.2", zone2.names());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void withMissingField() throws Exception {
    Configuration config = Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);

    Response response = Response.create(200, "OK", Collections.<String, Collection<String>> emptyMap(), zonesJson, UTF_8);
    List<Zone> zones = (List<Zone>) new JsonPathDecoder(config).decode(response, new TypeRef<List<Zone>>() {
    }.getType());
    assertNotNull(zones);
    assertEquals(2, zones.size());
    Zone zone1 = zones.get(0);
    assertNull("Path $['id'] is expected to be null", zone1.id());
    assertEquals("denominator.io.1", zone1.names());
    Zone zone2 = zones.get(1);
    assertEquals("ABCD", zone2.id());
    assertEquals("denominator.io.2", zone2.names());
  }

  @Test
  public void nullBodyDecodesToNull() throws Exception {
    Response response = Response
        .create(204, "OK", Collections.<String, Collection<String>> emptyMap(), (byte[]) null);
    assertNull(new JsonPathDecoder().decode(response, ZoneInfo.class));
  }

  @Test
  public void invalidBodyDecodesToNull() throws Exception {
    Response response = Response
        .create(204, "OK", Collections.<String, Collection<String>> emptyMap(), "this is no json", UTF_8);
    assertNull(new JsonPathDecoder().decode(response, ZoneInfo.class));
  }

  @Test
  public void emptyBodyDecodesToNull() throws Exception {
    Response response = Response.create(204, "OK",
        Collections.<String, Collection<String>> emptyMap(),
        new byte[0]);
    assertNull(new JsonPathDecoder().decode(response, ZoneInfo.class));
  }

  @Test
  public void notFoundDecodesToNull() throws Exception {
    Response response = Response.create(404, "Not found", Collections.<String, Collection<String>> emptyMap(),
        zonesJson, UTF_8);
    assertNull(new JsonPathDecoder().decode(response, ZoneInfo.class));
  }

  @Test
  public void errorDecodeInvalidMethod() throws Exception {
    Response response = Response.create(200, "OK", Collections.<String, Collection<String>> emptyMap(), zonesJson,
        UTF_8);
    ZoneInfo zone = (ZoneInfo) new JsonPathDecoder().decode(response, ZoneInfo.class);
    try {
      zone.ids();
      fail();
    } catch (IllegalStateException e) {
      assertEquals("Method not annotated with @JsonExpr public abstract java.util.List feign.jsonpath.JsonPathDecoderTest$ZoneInfo.ids()", e.getMessage());
    }
  }

  static interface ZoneInfo {

    @JsonExpr("$..name")
    List<String> names();

    List<String> ids();

  }

  @JsonSplit("$[*]")
  static interface Zone {

    @JsonExpr("$.name")
    String names();

    @JsonExpr("$.id")
    String id();

  }

}
