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
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ProgressionPattern implements MelodicPattern {

  public static final Pattern KEY_PATTERN = Pattern.compile("[A-G]m?");
  public static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};
  public static final int[] MINOR = {0, 2, 3, 5, 7, 8, 10};
  public static final int[][] SCALES = {MAJOR, MINOR};

  private int keyNote;
  private int keyScale;
  private int progressionLength;
  private int[] progressionDegree;
  private int[] progressionChord;
  private int[] pattern;

  public ProgressionPattern(String key, String progression) {
    if (!KEY_PATTERN.matcher(key).matches()) throw new IllegalStateException(key);
    keyNote = (12 + key.charAt(0) - 'C') % 12;
    keyScale = (key.length() > 1 && key.charAt(1) == 'm') ? 1 : 0;
    String[] p = progression.trim().split("-");
    progressionLength = p.length;
    if (progressionLength != 4) throw new IllegalStateException(progression);
    progressionDegree = new int[progressionLength];
    progressionChord = new int[progressionLength];
    for (int i = 0; i < progressionLength; i++) {
      if (p[i].equals(p[i].toLowerCase())) {
        progressionChord[i] = 1; // minor
      } else if (!p[i].equals(p[i].toUpperCase())) {
        throw new IllegalStateException(p[i]);
      }
      progressionDegree[i] = new LatinNumeral(p[i]).toInteger() - 1;
    }

    pattern = new int[progressionLength];
    for (int ip = 0; ip < progressionLength; ip++) {
      int chordNote = SCALES[keyScale][progressionDegree[ip]] + keyNote;
      int[] chord = SCALES[progressionChord[ip]];
      for (int i = 0; i < 3; i++) {
        pattern[ip] |= 1 << (chord[i * 2] + chordNote) % 12;
      }
    }
  }

  public ProgressionPattern() {
    this("C", "I-V-vi-IV"); // let it be
  }

  @Override
  public byte[] getMidi() {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    for (int step = 0; step < 64; step++) {
      if (step % 8 == 0 || step % 8 == 7) {
        int p = pattern[step / 16];
        for (int i = 0; i < 12; i++) {
          if ((1 << i & p) != 0) {
            stream.write(step % 8 == 0 ? 0x90 : 0x80);
            stream.write(60 + i);
            stream.write(TnsSound.MIDI_DEFAULT_VELOCITY);
            stream.write(0x00);
          }
        }
      }
      stream.write(0xFF);
      stream.write(0x7F);
      stream.write(0x00);
      stream.write(0x30); // delay
    }
    return stream.toByteArray();
  }

  @Override
  public String toString() {
    return (char) ((keyNote + 2) % 12 + 'A') + (keyScale == 0 ? "" : "m") + " " +
        IntStream.range(0, progressionChord.length).mapToObj(i -> {
          String s = new LatinNumeral(progressionDegree[i] + 1).toString();
          return progressionChord[i] == 0 ? s.toUpperCase() : s.toLowerCase();
        }).collect(Collectors.joining("-"));
  }

  public static class LatinNumeral {
    public static final String[] NUMERALS_ARRAY = {"I", "II", "III", "IV", "V", "VI"};
    public static final List<String> NUMERALS_LIST = Arrays.asList(NUMERALS_ARRAY);
    private int i;

    public LatinNumeral(int i) {
      this.i = i;
    }

    public LatinNumeral(String s) {
      i = NUMERALS_LIST.indexOf(s.trim().toUpperCase()) + 1;
      if (i < 1) throw new IllegalStateException(s);
    }

    public int toInteger() {
      return i;
    }

    @Override
    public String toString() {
      return NUMERALS_ARRAY[i - 1];
    }
  }
}
