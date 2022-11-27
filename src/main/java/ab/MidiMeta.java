/*
 * Copyright 2022 Aleksei Balan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ab;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;

public class MidiMeta implements MelodicPattern {

  ByteArrayOutputStream stream = new ByteArrayOutputStream();

  public MidiMeta instrument(int instrument) {
    stream.write(0xC0);
    stream.write(instrument - 1);
    stream.write(0);
    return this;
  }

  public MidiMeta tempo(double bpm) {
    stream.write(0xFF);
    stream.write(0x51);
    byte[] data = BigInteger.valueOf(Math.round(60_000_000 / bpm)).toByteArray();
    stream.write(data.length);
    try {
      stream.write(data);
      //stream.write(new MetaMessage(0x51, data, data.length).getMessage());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    stream.write(0);
    return this;
  }

  @Override
  public byte[] getMidi() {
    return stream.toByteArray();
  }

}
