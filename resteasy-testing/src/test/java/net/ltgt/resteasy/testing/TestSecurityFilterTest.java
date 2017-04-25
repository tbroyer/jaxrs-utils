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
package net.ltgt.resteasy.testing;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.attribute.UserPrincipal;
import java.util.Arrays;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import org.junit.Rule;
import org.junit.Test;

public class TestSecurityFilterTest {
  @Rule public InProcessResteasy resteasy = new InProcessResteasy();

  @Test
  public void test() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(Resource.class);
    resteasy
        .getDeployment()
        .getProviderFactory()
        .register(
            new TestSecurityFilter(
                new CustomUserPrincipal(),
                Arrays.asList("roleA", "roleC"),
                true,
                "the authentication scheme"));

    Response response =
        resteasy
            .getClient()
            .target(resteasy.getBaseUriBuilder().path(Resource.class))
            .request()
            .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.readEntity(String.class)).isEqualTo("OK");
  }

  public static class CustomUserPrincipal implements UserPrincipal {
    @Override
    public String getName() {
      return "the user's name";
    }
  }

  @Path("/")
  public static class Resource {
    @Context SecurityContext securityContext;

    @GET
    public String get() {
      assertThat(securityContext.isSecure()).isTrue();

      assertThat(securityContext.isUserInRole("roleA")).isTrue();
      assertThat(securityContext.isUserInRole("roleB")).isFalse();
      assertThat(securityContext.isUserInRole("roleC")).isTrue();

      assertThat(securityContext.getAuthenticationScheme()).isEqualTo("the authentication scheme");

      assertThat(securityContext.getUserPrincipal()).isInstanceOf(CustomUserPrincipal.class);
      assertThat(securityContext.getUserPrincipal().getName()).isEqualTo("the user's name");

      return "OK";
    }
  }
}
