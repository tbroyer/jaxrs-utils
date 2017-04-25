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
package net.ltgt.jaxrs.webhook.client;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import net.ltgt.jaxrs.webhook.Util;
import net.ltgt.resteasy.testing.InProcessResteasy;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WebhookSignatureFilterTest {

  private static final String SECRET = "This is a secret";
  private static final byte[] PAYLOAD =
      "This is the request payload".getBytes(StandardCharsets.UTF_8);
  private static final String SIGNATURE = "3daba1f18d85905076a8ed72caf13565ece571fb";

  @Rule public InProcessResteasy resteasy = new InProcessResteasy();

  @Before
  public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test
  public void testWebhookSignature() {
    Response response =
        resteasy
            .getClient()
            .register(new WebhookSignatureFilter(SECRET))
            .target(resteasy.getBaseUriBuilder().path(DummyResource.class))
            .request()
            .post(Entity.entity(PAYLOAD, MediaType.APPLICATION_OCTET_STREAM_TYPE));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    String signature = response.readEntity(String.class);
    assertThat(signature)
        .startsWith(Util.PREFIX) // the prefix is case-sensitive
        .isEqualToIgnoringCase(Util.PREFIX + SIGNATURE);
  }

  @Path("/")
  public static class DummyResource {
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_PLAIN)
    public String echoSignature(@Context HttpHeaders headers, byte[] payload) {
      assertThat(payload).isEqualTo(PAYLOAD);
      return headers.getHeaderString(Util.HEADER);
    }
  }
}
