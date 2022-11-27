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
import java.util.Arrays;

public class DrumPattern implements MelodicPattern {

  public static final String[] KEY_NAMES = {
      "BD1", "BD2", "SD1", "SD2", "L-T", "M-T", "H-T", "RIM", // TR-707
      "COW", "HCP", "TAMB", "CH", "CH", "OH", "CRASH", "RIDE", // TR-707
      "SHORT WHIS", "LONG WHIS"}; // TR-727 essentials
  public static final int[] KEY_NUMBERS = {
      35, 36, 38, 40, 41, 45, 48, 37,
      56, 39, 54, 42, 44, 46, 49, 51,
      71, 72
  };
  public static final int DRUM_NUMBER = KEY_NUMBERS.length;
  public static final DrumPattern EMPTY = new DrumPattern(new int[DRUM_NUMBER]);

  private int[] pattern;

  public DrumPattern(int[] pattern) {
    this.pattern = pattern;
    if (Arrays.stream(pattern).anyMatch(p -> (p | 0xFFFF) != 0xFFFF)) throw new IllegalStateException();
  }

  public static DrumPattern newShort(int... patterns) {
    int[] fullPattern = new int[DRUM_NUMBER];
    for (int i = 1; i < patterns.length; i += 2) {
      fullPattern[patterns[i - 1]] = patterns[i];
    }
    return new DrumPattern(fullPattern);
  }

  @Override
  public byte[] getMidi() {
    int[] patterns = this.pattern;
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    for (int step = 0; step < 16; step++) {
      for (int on = 1; on >= 0; on--) {
        for (int pattern = 0; pattern < patterns.length; pattern++) {
          if ((patterns[pattern] & 0x8000 >> step) != 0) {
            stream.write(on > 0 ? 0x99 : 0x89);
            stream.write(KEY_NUMBERS[pattern]);
            stream.write(TnsSound.MIDI_DEFAULT_VELOCITY);
            stream.write(0x00);
          }
        }
        stream.write(0xFF);
        stream.write(0x7F);
        stream.write(0x00);
        stream.write(on > 0 ? 0x2E : 0x02);
      }
    }
    return stream.toByteArray();
  }

  private static String patternToString(int pattern) {
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0, mask = 0x8000; mask != 0; i++, mask >>= 1) {
      if (i % 4 == 0) stringBuilder.append(' ');
      stringBuilder.append((pattern & mask) == 0 ? '-' : '#');
    }
    return stringBuilder.toString();
  }

  @Override
  public String toString() {
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i < pattern.length; i++) {
      if (pattern[i] != 0) {
        stringBuilder.append(String.format("%-5s%s%n", KEY_NAMES[i], patternToString(pattern[i])));
      }
    }
    return stringBuilder.toString().trim();
  }
}
