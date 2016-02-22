/*
 * Tigase XMPP Client Library
 * Copyright (C) 2006-2014 Tigase, Inc. <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.jaxmpp.j2se.connectors.socket;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import static tigase.jaxmpp.j2se.connectors.socket.SocketConnector.DEFAULT_SOCKET_BUFFER_SIZE;

/**
 * TextStreamReader class replaces standard InputStreamReader as it cannot read from
 * InflaterInputStream.
 *
 * @author andrzej
 */
public class TextStreamReader implements Reader {
  private final ByteBuffer buf = ByteBuffer.allocate(DEFAULT_SOCKET_BUFFER_SIZE);
  private final CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
  private final ReadableByteChannel inputStream;

  public TextStreamReader(InputStream inputStream) {
    this.inputStream = Channels.newChannel(inputStream);
  }

  @Override
  public int read(char[] cbuf) throws IOException {
    final int read = inputStream.read(buf);

    final CharBuffer cb = CharBuffer.wrap(cbuf);
    buf.flip();
    decoder.decode(buf, cb, false);
    buf.compact();
    cb.flip();
    return cb.remaining() > 0 ? cb.remaining() : (read < 0 ? -1 : 0);
  }
}
