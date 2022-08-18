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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class AmigaMod {

  public final ByteBuffer bytes;
  public final int patternSize;
  public int samplesSize = 0x20;
  public final int[] samples = new int[samplesSize];
  public final int orderPos;
  public final int patternPos;

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
      samplesSize = 0x10;
      throw new IllegalStateException("mod file error");
    }
    orderPos = samplesSize > 0x10 ? 0x3B6 : 0x1D6;
    patternPos = samplesSize > 0x10 ? 0x43C : 0x258;
    bytes.position(orderPos + 2);
    int maxPattern = 0;
    int maxOrder = 0;
    for (int i = 0; i < 0x80; i++) {
      byte b = bytes.get();
      maxPattern = Math.max(maxPattern, b);
      maxOrder = b == 0 ? maxOrder : i;
    }
    patternSize = maxPattern + 1;
    for (int i = 0, sampleStart = 0; i < samplesSize; i++) {
      samples[i] = sampleStart;
      sampleStart += i == 0 ? patternPos + patternSize * 0x400 : getSampleSize(i);
    }
  }

  public AmigaMod(InputStream stream) {
    this(readAllBytes(stream));
  }

  public int getSampleSize(int sample) {
    return sample == 0 ? samples[1] : bytes.getShort(sample * 0x1E + 0x0C) << 1 & 0x1FFFF;
  }

  public String getSongName() {
    return new String(Arrays.copyOfRange(bytes.array(), 0, 0x14), StandardCharsets.ISO_8859_1);
  }

  public String getSampleName(int sample) {
    if (sample == 0) return getSongName();
    return new String(Arrays.copyOfRange(bytes.array(), sample * 0x1E - 0x0A, sample * 0x1E + 0x0C),
        StandardCharsets.ISO_8859_1);
  }

  public int getSampleStart(int sample) {
    return samples[sample];
  }

  @Deprecated
  public int getSampleEnd(int sample) {
    return getSampleStart(sample) + getSampleSize(sample);
  }

  public int getOrderSize() {
    return bytes.get(orderPos);
  }

  /**
   * Definition of Loop.
   */
  public boolean isLoop(int sample) {
    // FIXME: 2022-08-18 this is wrong
    return sample == 0 ? true : getLoopStart(sample) != 0;
  }

  public int getLoopStart(int sample) {
    return sample == 0 ? 0 : bytes.getShort(sample * 0x1E + 0x10) << 1 & 0x1FFFF;
  }

  public int getLoopLength(int sample) {
    return sample == 0 ? samples[1] : bytes.getShort(sample * 0x1E + 0x12) << 1 & 0x1FFFF;
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
      return bytes.get(orderPos + 2 + getOrder());
    }

    public int getRow() {
      return row & 0x3F;
    }

    public int getLoop() {
      return getOrder() / getOrderSize();
    }

    public Note[] getNotes() {
      int position = ((getPattern() << 6) + getRow() << 4) + patternPos;
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
        double log2 = Math.log(428.0 / noteCode) / Math.log(2);
        midiNote = (int) Math.round(60 + log2 * 12);
      } else {
        midiNote = 0;
      }
    }

    public int getNoteCode() {
      return (data >> 16) & 0x0FFF;
    }

    /**
     * Definition of Note On.
     */
    public boolean isNoteOn() {
      return getNoteCode() != 0;
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
          !isNoteOn() ? "..." : String.format("%c%c%d",
              CDEFGAB0.charAt(midiNote % 12), CDEFGAB1.charAt(midiNote % 12), (midiNote / 12 - 1)),
          getSample() == 0 ? ".." : String.format("%02d", getSample()),
          FX.charAt(getFxCommand()), getFxData());
    }
  }

}
