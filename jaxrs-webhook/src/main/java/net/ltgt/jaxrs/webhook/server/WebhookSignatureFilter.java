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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import javax.annotation.Priority;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;
import net.ltgt.jaxrs.webhook.Util;

/**
 * Validates an incoming {@code X-Hub-Signature} request header against the request body and a
 * shared secret.
 *
 * <p>The secret can either be given to the filter constructor, or retrieved from the {@link
 * UriInfo#getMatchedResources() matched resource} which must then implement {@link
 * HasWebhookSecret}. Alternatively, the filter can be subclassed and the {@link
 * #getSecret(ContainerRequestContext)} method overridden.
 *
 * <p>The filter will only apply to resources annotated with {@link Webhook}.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
@Webhook
public class WebhookSignatureFilter implements ContainerRequestFilter {

  private final byte[] secret;

  /**
   * Constructs a {@link WebhookSignatureFilter} that will ask the {@link
   * UriInfo#getMatchedResources() matched resource} for the secret.
   *
   * <p>Matched resources <strong>MUST</strong> implement {@link HasWebhookSecret} or the filter
   * will error out with a {@link ClassCastException} (leading to an internal server error
   * response).
   */
  public WebhookSignatureFilter() {
    this.secret = null;
  }

  /**
   * Constructs a {@link WebhookSignatureFilter} with a fixed secret.
   *
   * <p>The secret's UTF-8 bytes will actually be used as the secret. This is equivalent to calling:
   *
   * <pre><code>
   *   new WebhookSignatureFilter(secret.getBytes(StandardCharsets.UTF-8))
   * </code></pre>
   */
  public WebhookSignatureFilter(String secret) {
    if (secret.isEmpty()) {
      throw new IllegalArgumentException("secret must not be empty");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  /** Constructs a {@link WebhookSignatureFilter} with a fixed secret. */
  public WebhookSignatureFilter(byte[] secret) {
    if (secret.length == 0) {
      throw new IllegalArgumentException("secret must not be empty");
    }
    this.secret = secret;
  }

  /**
   * Returns the secret to use for computing the signature of the request body (before comparing it
   * with the one sent in the {@code X-Hub-Signature} request header).
   *
   * <p>The default implementation will either return the fixed secret if one has been set through
   * the constructor, or cast the {@link UriInfo#getMatchedResources() matched resource} to {@link
   * HasWebhookSecret} and call {@link HasWebhookSecret#getWebhookSecret()}.
   */
  protected byte[] getSecret(ContainerRequestContext requestContext) {
    if (secret != null) {
      return secret;
    }
    return ((HasWebhookSecret) requestContext.getUriInfo().getMatchedResources().get(0))
        .getWebhookSecret();
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    byte[] secret = getSecret(requestContext);
    if (secret == null || secret.length == 0) {
      throw new IllegalStateException("Webhook secret may not be null or empty");
    }

    List<String> expectedSignatures = requestContext.getHeaders().get(Util.HEADER);
    if (expectedSignatures == null || expectedSignatures.size() != 1) {
      requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST).build());
      return;
    }
    String expectedSignature = expectedSignatures.get(0);
    if (!expectedSignature.startsWith(Util.PREFIX)) {
      requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST).build());
      return;
    }
    expectedSignature = expectedSignature.substring(Util.PREFIX.length());

    final SecretKeySpec secretKeySpec = new SecretKeySpec(secret, Util.ALGORITHM);
    final Mac mac;
    try {
      mac = Mac.getInstance(Util.ALGORITHM);
      mac.init(secretKeySpec);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      // This shouldn't happen: HmacSHA1 is a mandatory-to-implement algorithm, and doesn't restrict its keys
      throw new AssertionError(e);
    }

    byte[] bytes = Util.toByteArray(requestContext.getEntityStream());

    byte[] actualSignature = mac.doFinal(bytes);
    if (!expectedSignature.equalsIgnoreCase(Util.hex(actualSignature))) {
      // Return a 200 (OK) per spec.
      requestContext.abortWith(Response.ok().build());
      return;
    }

    requestContext.setEntityStream(new ByteArrayInputStream(bytes));
  }
}
