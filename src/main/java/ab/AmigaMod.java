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
  public final int[] samples = new int[0x20];

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
    for (int i = 0, sampleStart = 0; i < 0x20; i++) {
      samples[i] = sampleStart;
      sampleStart += i == 0 ? 0x43C + patternSize * 0x400 : getSampleSize(i);
    }
  }

  public AmigaMod(InputStream stream) {
    this(readAllBytes(stream));
  }

  public int getSampleSize(int sample) {
    return bytes.getShort(sample * 0x1E + 0x0C) << 1 & 0x1FFFF;
  }

  public int getSampleStart(int sample) {
    return samples[sample];
  }

  public int getSampleEnd(int sample) {
    return getSampleStart(sample) + getSampleSize(sample);
  }

  public int getOrderSize() {
    return bytes.get(0x3B6);
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
      return getOrder() / getOrderSize();
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
    public static final String CDEFGAB0 = "CCDDEFFGGAAB";
    public static final String CDEFGAB1 = "-#-#--#-#-#-";
    public static final String FX = ".123456789ABCDEF";
    private final int data;
    private final int midiNote;

    public Note(int data) {
      this.data = data;
      int noteCode = getNoteCode();
      if (noteCode > 0) {
        double log2 = Math.log(254.0 / noteCode) / Math.log(2);
        midiNote = (int) Math.round(69 + log2 * 12);
      } else {
        midiNote = 0;
      }
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

    public int getMidiNote() {
      return midiNote;
    }

    public int getSample() {
      return data >> 24 & 0xF0 | data >> 12 & 0x0F;
    }

    public int getFxCommand() {
      return data >> 8 & 0x0F;
    }

    public int getFxData() {
      return data & 0xFF;
    }

    @Override
    public String toString() {
      return String.format("%s %s %c%02X",
          isNoteEmpty() ? "..." : String.format("%c%c%d",
              CDEFGAB0.charAt(midiNote % 12), CDEFGAB1.charAt(midiNote % 12), (midiNote / 12 - 1)),
          getSample() == 0 ? ".." : String.format("%02d", getSample()),
          FX.charAt(getFxCommand()), getFxData());
    }
  }

}
