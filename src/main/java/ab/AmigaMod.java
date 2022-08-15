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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

public class AmigaMod {

  public final ByteBuffer bytes;
  public final int patternSize;
  public final int orderSize;

  private static byte[] readAllBytes(InputStream stream) {
    try (stream) {
      return stream.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public AmigaMod(byte[] content) {
    bytes = ByteBuffer.wrap(content);
    if (bytes.getInt(0x438) != 0x4D2E4B2E) {
      throw new IllegalStateException("mod file error");
    }
    bytes.position(0x3B8);
    int maxPattern = 0;
    int maxOrder = 0;
    for (int i = 0; i < 0x80; i++) {
      byte b = bytes.get();
      maxPattern = Math.max(maxPattern, b);
      maxOrder = b == 0 ? maxOrder : i;
    }
    patternSize = maxPattern + 1;
    orderSize = maxOrder + 1;
  }

  public AmigaMod(InputStream stream) {
    this(readAllBytes(stream));
  }

  public Sequencer newSequencer() {
    return new Sequencer();
  }

  public class Sequencer {
    private int row;

    public void inc() {
      this.row++;
    }

    public int getOrder() {
      return row >> 6;
    }

    public int getPattern() {
      return bytes.get(getOrder() + 0x3B8);
    }

    public int getRow() {
      return row & 0x3F;
    }

    public int getLoop() {
      return getOrder() / orderSize;
    }

    public Note[] getNotes() {
      int position = ((getPattern() << 6) + getRow() << 4) + 0x43C;
      bytes.position(position);
      Note[] result = new Note[4];
      for (int c = 0; c < 4; c++) {
        result[c] = new Note(bytes.getInt());
      }
      return result;
    }
  }

  public static class Note {
    private final int data;

    public Note(int data) {
      this.data = data;
    }

    public int getNoteCode() {
      return (data >> 16) & 0x0FFF;
    }

    public boolean isNoteEmpty() {
      return getNoteCode() == 0;
    }

    public int getSampleRate() {
      return 8363 * 428 / getNoteCode();
    }

    public int getFrequency() {
      return 440 * 254 / getNoteCode();
    }
  }

}
