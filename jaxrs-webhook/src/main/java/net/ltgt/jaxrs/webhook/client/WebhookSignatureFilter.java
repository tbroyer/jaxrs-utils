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

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.annotation.Priority;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.Priorities;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

import net.ltgt.jaxrs.webhook.Util;

/**
 * Compute an {@code X-Hub-Signature} request header from a shared secret and request body.
 *
 * <p>Usage:
 * <pre><code>
jaxrsClient.register(new WebhookSignatureFilter("secret"))
    .target(uri)
    .request()
    .post(payload);
 * </code></pre>
 *
 * @see <a href="https://pubsubhubbub.github.io/PubSubHubbub/pubsubhubbub-core-0.4.html#authednotify">
 *   PubSubHubbub's Authenticated Content Distribution</a>
 */
@ConstrainedTo(RuntimeType.CLIENT)
@Priority(Priorities.HEADER_DECORATOR)
public class WebhookSignatureFilter implements WriterInterceptor {

  private final byte[] secret;

  public WebhookSignatureFilter(String secret) {
    if (secret.isEmpty()) {
      throw new IllegalArgumentException("secret must not be empty");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  public WebhookSignatureFilter(byte[] secret) {
    if (secret.length == 0) {
      throw new IllegalArgumentException("secret must not be empty");
    }
    this.secret = secret;
  }

  protected WebhookSignatureFilter() {
    this.secret = null;
  }

  protected byte[] getSecret(WriterInterceptorContext context) {
    return secret;
  }

  @Override
  public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
    byte[] secret = getSecret(context);
    if (secret == null || secret.length == 0) {
      // TODO: log error?
      return;
    }

    final SecretKeySpec secretKeySpec = new SecretKeySpec(secret, Util.ALGORITHM);
    final Mac mac;
    try {
      mac = Mac.getInstance(Util.ALGORITHM);
      mac.init(secretKeySpec);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      // This shouldn't happen: HmacSHA1 is a mandatory-to-implement algorithm, and doesn't restrict its keys
      throw new AssertionError(e);
    }

    // We need to buffer all the output to be able to add the header
    OutputStream realOut = context.getOutputStream();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    context.setOutputStream(new FilterOutputStream(baos) {
      @Override
      public void write(int b) throws IOException {
        mac.update((byte) b);
        out.write(b);
      }

      @Override
      public void write(byte[] b) throws IOException {
        mac.update(b);
        out.write(b);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        mac.update(b, off, len);
        out.write(b, off, len);
      }
    });

    context.proceed();

    byte[] signature = mac.doFinal();
    context.getHeaders().putSingle(Util.HEADER, Util.PREFIX + Util.hex(signature));

    realOut.write(baos.toByteArray());

    baos = null;
  }
}
