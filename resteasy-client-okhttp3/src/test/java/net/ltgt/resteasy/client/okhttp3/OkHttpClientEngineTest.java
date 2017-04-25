/*
 * Copyright (C) 2015 Thomas Broyer (t.broyer@ltgt.net)
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
package net.ltgt.resteasy.client.okhttp3;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class OkHttpClientEngineTest {

  private static final byte[] PAYLOAD = "This is the request payload".getBytes(StandardCharsets.UTF_8);
  static final String HEADER_NAME = "X-Whatever";
  static final String INJECTED_HEADER_NAME = "X-Injected";
  static final String HEADER_VALUE = "some header";

  // In case an error is thrown in the MockWebServer, so clients don't block infinitely.
  @Rule public Timeout timeout = Timeout.seconds(10);

  @Rule public MockWebServer mockServer = new MockWebServer();

  @Before public void configureServer() {
    mockServer.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        assertThat(request.getHeader(INJECTED_HEADER_NAME)).isEqualTo(HEADER_VALUE);
        switch (request.getPath()) {
          case "/simple":
            return new MockResponse()
                .setResponseCode(Response.Status.NOT_FOUND.getStatusCode())
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                .setHeader(HEADER_NAME, HEADER_VALUE)
                .setBody("Not found");
          case "/writerInterceptor":
            assertThat(request.getBody().readByteArray()).isEqualTo(PAYLOAD);
            // fall-through
          case "/requestHeader":
            assertThat(request.getHeader(HEADER_NAME)).isEqualTo(HEADER_VALUE);
            return new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                .setBody(request.getHeader(HEADER_NAME));
        }
        throw new AssertionError("Unexpected request: " + request);
      }
    });
  }

  private OkHttpClient okHttpClient;
  private Client client;

  @Before public void configureClient() {
    okHttpClient = new OkHttpClient.Builder()
        .addInterceptor(
            // Ensures that this OkHttpClient is correctly used by the tests
            new Interceptor() {
              @Override
              public okhttp3.Response intercept(Chain chain) throws IOException {
                return chain.proceed(chain.request().newBuilder().addHeader(INJECTED_HEADER_NAME, HEADER_VALUE).build());
              }
            })
        .build();
    client = new ResteasyClientBuilder()
        .httpEngine(new OkHttpClientEngine(okHttpClient))
        .build();
  }
  @After public void closeClient() {
    client.close();
    okHttpClient.dispatcher().cancelAll();
  }

  @Test public void simpleRequest() {
    Response response = client.target(mockServer.url("/simple").uri()).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.NOT_FOUND);
    assertThat(response.getMediaType()).isEqualTo(MediaType.TEXT_PLAIN_TYPE);
    assertThat(response.getHeaderString(HEADER_NAME)).isEqualTo(HEADER_VALUE);
    assertThat(response.readEntity(String.class)).isEqualTo("Not found");
  }

  @Test public void requestHeader() {
    Response response = client.target(mockServer.url("/requestHeader").uri()).request()
        .header(HEADER_NAME, HEADER_VALUE)
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.readEntity(String.class)).isEqualTo(HEADER_VALUE);
  }

  @Test public void simpleWriterInterceptors() {
    Response response = client.target(mockServer.url("/writerInterceptor").uri())
        .register(new WriterInterceptor() {
          @Override
          public void aroundWriteTo(WriterInterceptorContext context)
              throws IOException, WebApplicationException {
            context.getHeaders().putSingle(HEADER_NAME, HEADER_VALUE);
            context.proceed();
          }
        })
        .request()
        .post(Entity.json(PAYLOAD));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.readEntity(String.class)).isEqualTo(HEADER_VALUE);
  }
}
