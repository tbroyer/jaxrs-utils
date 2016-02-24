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

import java.net.URI;
import java.security.Principal;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.SecurityContext;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.junit.rules.ExternalResource;

/**
 * Creates an in-process Resteasy container and client.
 *
 * <p>Usage:
 * <pre><code>
&#064;Rule public InProcessResteasy resteasy = new InProcessResteasy();

&#064;Before public void setup() {
  resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
}

&#064;Test public void testMethod() {
  // Setup authenticated user as "username"
  resteasy.getDeployment().getProviderFactory().register(
      new TestSecurityFilter("username", SecurityContext.FORM_AUTH));

  Response response = resteasy.getClient()
      .target(UriBuilder.fromUri(resteasy.getBaseUri()).path(DummyResource.class))
      .request().get();
  // ...
}
 * </code></pre>
 */
public class InProcessResteasy extends ExternalResource {

  private static final URI DEFAULT_BASE_URI = URI.create("http://localhost/");

  private final URI baseUri;

  private ResteasyDeployment deployment;
  private Client client;

  public InProcessResteasy() {
    this(DEFAULT_BASE_URI);
  }

  public InProcessResteasy(URI baseUri) {
    this.baseUri = baseUri;
  }

  public final URI getBaseUri() {
    return baseUri;
  }

  public final ResteasyDeployment getDeployment() {
    return deployment;
  }

  public final Client getClient() {
    return client;
  }

  @Override
  protected final void before() throws Throwable {
    deployment = new ResteasyDeployment();
    deployment.getDefaultContextObjects().put(SecurityContext.class, new DummySecurityContext());
    configureDeployment(deployment);

    deployment.start();

    ResteasyClientBuilder builder = new ResteasyClientBuilder()
        .httpEngine(new InProcessClientHttpEngine(deployment.getDispatcher(), baseUri));
    configureClient(builder);
    client = builder.build();
  }

  @Override
  protected final void after() {
    deployment.stop();
    client.close();
  }

  protected void configureDeployment(ResteasyDeployment deployment) {
    // no-op
  }

  protected void configureClient(ResteasyClientBuilder builder) {
    // no-op
  }

  private static class DummySecurityContext implements SecurityContext {
    @Override
    public Principal getUserPrincipal() {
      return null;
    }

    @Override
    public boolean isUserInRole(String role) {
      return false;
    }

    @Override
    public boolean isSecure() {
      return false;
    }

    @Override
    public String getAuthenticationScheme() {
      return null;
    }
  }
}
