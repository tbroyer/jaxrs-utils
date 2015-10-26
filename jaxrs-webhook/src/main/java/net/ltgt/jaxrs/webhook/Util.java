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
package net.ltgt.jaxrs.webhook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Internal utilities, please do not use.
 */
public class Util {
  public static final String HEADER = "X-Hub-Signature";
  public static final String ALGORITHM = "HmacSHA1";
  public static final String PREFIX = "sha1=";

  private static final char[] HEX_DIGITS =
      { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
  private static final int BUF_SIZE = 8192;

  public static String hex(byte[] data) {
    char[] result = new char[data.length * 2];
    int c = 0;
    for (byte b : data) {
      result[c++] = HEX_DIGITS[(b >> 4) & 0xf];
      result[c++] = HEX_DIGITS[b & 0xf];
    }
    return new String(result);
  }

  public static byte[] toByteArray(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[BUF_SIZE];
    while (true) {
      int r = in.read(buf);
      if (r == -1) {
        break;
      }
      out.write(buf, 0, r);
    }
    return out.toByteArray();
  }
}
