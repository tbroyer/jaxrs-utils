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

import java.io.IOException;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;

import org.jboss.resteasy.plugins.server.embedded.SimplePrincipal;

public class TestSecurityFilter implements ContainerRequestFilter {
  private final Principal userPrincipal;
  private final Set<String> roles;
  private final boolean secure;
  private final String authenticationScheme;

  public TestSecurityFilter(boolean secure) {
    this((Principal) null, null, secure, null);
  }

  public TestSecurityFilter(String username, String authenticationScheme) {
    this(new SimplePrincipal(Objects.requireNonNull(username)), null, false, authenticationScheme);
  }

  public TestSecurityFilter(String username, Collection<String> roles, String authenticationScheme) {
    this(new SimplePrincipal(Objects.requireNonNull(username)), roles, false, authenticationScheme);
  }

  public TestSecurityFilter(String username, boolean secure, String authenticationScheme) {
    this(new SimplePrincipal(Objects.requireNonNull(username)), null, secure, authenticationScheme);
  }

  public TestSecurityFilter(String username, Collection<String> roles, boolean secure, String authenticationScheme) {
    this(new SimplePrincipal(Objects.requireNonNull(username)), roles, secure, authenticationScheme);
  }

  public TestSecurityFilter(Principal userPrincipal, String authenticationScheme) {
    this(userPrincipal, null, false, authenticationScheme);
  }

  public TestSecurityFilter(Principal userPrincipal, Collection<String> roles, String authenticationScheme) {
    this(userPrincipal, roles, false, authenticationScheme);
  }

  public TestSecurityFilter(Principal userPrincipal, boolean secure, String authenticationScheme) {
    this(userPrincipal, null, secure, authenticationScheme);
  }

  public TestSecurityFilter(Principal userPrincipal, Collection<String> roles, boolean secure, String authenticationScheme) {
    if (userPrincipal != null) {
      Objects.requireNonNull(authenticationScheme, "authenticationScheme may not be null if there's an authenticated user");
    } else {
      if (authenticationScheme != null) {
        throw new IllegalArgumentException(
            "authenticationScheme must be null if there's no authenticated user");
      }
      if (roles != null && !roles.isEmpty()) {
        throw new IllegalArgumentException(
            "roles must be null or empty if there's no authenticated user");
      }
    }
    this.userPrincipal = userPrincipal;
    this.roles = roles == null ? Collections.<String>emptySet() : new LinkedHashSet<>(roles);
    this.secure = secure;
    this.authenticationScheme = authenticationScheme;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    requestContext.setSecurityContext(new SecurityContext() {
      @Override
      public Principal getUserPrincipal() {
        return userPrincipal;
      }

      @Override
      public boolean isUserInRole(String role) {
        return roles.contains(role);
      }

      @Override
      public boolean isSecure() {
        return secure;
      }

      @Override
      public String getAuthenticationScheme() {
        return authenticationScheme;
      }
    });
  }
}
