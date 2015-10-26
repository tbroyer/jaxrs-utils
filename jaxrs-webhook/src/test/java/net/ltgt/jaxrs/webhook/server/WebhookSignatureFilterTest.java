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
package net.ltgt.jaxrs.webhook.server;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Entity;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.junit.Rule;
import org.junit.Test;

import net.ltgt.jaxrs.webhook.Util;
import net.ltgt.resteasy.testing.InProcessResteasy;

public class WebhookSignatureFilterTest {

  private static final byte[] SECRET = "This is a secret".getBytes(StandardCharsets.UTF_8);
  private static final byte[] PAYLOAD = "This is the request payload".getBytes(StandardCharsets.UTF_8);
  private static final String SIGNATURE = "3daba1f18d85905076a8ed72caf13565ece571fb";

  @Rule public InProcessResteasy resteasy = new InProcessResteasy();

  @Test public void testValidSignatureWithSecretFromResource() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResourceWithWebhookSecret.class);
    resteasy.getDeployment().getProviderFactory().register(WebhookSignatureFilter.class);

    Response response = resteasy.getClient()
        .target(UriBuilder.fromResource(DummyResource.class))
        .request()
        .header(Util.HEADER, Util.PREFIX + SIGNATURE)
        .post(Entity.entity(PAYLOAD, MediaType.APPLICATION_OCTET_STREAM_TYPE));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    byte[] payload = response.readEntity(byte[].class);
    assertThat(payload).isEqualTo(PAYLOAD);
  }

  @Test public void testValidSignatureWithFixedSecret() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
    resteasy.getDeployment().getProviderFactory().register(new WebhookSignatureFilter(SECRET));

    Response response = resteasy.getClient()
        .target(UriBuilder.fromResource(DummyResource.class))
        .request()
        .header(Util.HEADER, Util.PREFIX + SIGNATURE)
        .post(Entity.entity(PAYLOAD, MediaType.APPLICATION_OCTET_STREAM_TYPE));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    byte[] payload = response.readEntity(byte[].class);
    assertThat(payload).isEqualTo(PAYLOAD);
  }

  @Test public void testValidSignatureWithGetSecretOverride() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
    resteasy.getDeployment().getProviderFactory().register(new WebhookSignatureFilter() {
      @Override
      protected byte[] getSecret(ContainerRequestContext requestContext) {
        return SECRET;
      }
    });

    Response response = resteasy.getClient()
        .target(UriBuilder.fromResource(DummyResource.class))
        .request()
        .header(Util.HEADER, Util.PREFIX + SIGNATURE)
        .post(Entity.entity(PAYLOAD, MediaType.APPLICATION_OCTET_STREAM_TYPE));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    byte[] payload = response.readEntity(byte[].class);
    assertThat(payload).isEqualTo(PAYLOAD);
  }

  @Test public void testMissingHeader() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
    resteasy.getDeployment().getProviderFactory().register(new WebhookSignatureFilter(SECRET));

    Response response = resteasy.getClient()
        .target(UriBuilder.fromResource(DummyResource.class))
        .request()
        .post(Entity.entity(PAYLOAD, MediaType.APPLICATION_OCTET_STREAM_TYPE));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
  }

  @Test public void testMultipleHeaders() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
    resteasy.getDeployment().getProviderFactory().register(new WebhookSignatureFilter(SECRET));

    Response response = resteasy.getClient()
        .target(UriBuilder.fromResource(DummyResource.class))
        .request()
        .header(Util.HEADER, Util.PREFIX + SIGNATURE)
        .header(Util.HEADER, Util.PREFIX + SIGNATURE)
        .post(Entity.entity(PAYLOAD, MediaType.APPLICATION_OCTET_STREAM_TYPE));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
  }

  @Test public void testInvalidHeader() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
    resteasy.getDeployment().getProviderFactory().register(new WebhookSignatureFilter(SECRET));

    Response response = resteasy.getClient()
        .target(UriBuilder.fromResource(DummyResource.class))
        .request()
        .header(Util.HEADER, "md5=" + SIGNATURE)
        .post(Entity.entity(PAYLOAD, MediaType.APPLICATION_OCTET_STREAM_TYPE));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
  }

  @Test public void testInvalidSignature() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
    resteasy.getDeployment().getProviderFactory().register(new WebhookSignatureFilter(SECRET));

    Response response = resteasy.getClient()
        .target(UriBuilder.fromResource(DummyResource.class))
        .request()
        .header(Util.HEADER, Util.PREFIX + "bad516")
        .post(Entity.entity(PAYLOAD, MediaType.APPLICATION_OCTET_STREAM_TYPE));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    byte[] payload = response.readEntity(byte[].class);
    assertThat(payload).isNullOrEmpty();
  }

  @Path("/")
  @Webhook
  public static class DummyResource {
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] echo(byte[] payload) {
      return payload;
    }
  }

  @Path("/")
  @Webhook
  public static class DummyResourceWithWebhookSecret extends DummyResource implements HasWebhookSecret {
    @Override
    public byte[] getWebhookSecret() {
      return SECRET;
    }
  }
}
