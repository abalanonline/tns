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

/**
 * Logarithmic drum pattern.
 * Probably reinventing the wheel, I didn't google.
 */
public class LogDrum {

  public static final int[] LOG_FRM = {0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15};

  public static int linToLog(int linPattern) {
    int logPattern = 0;
    for (int i = 0; i < LOG_FRM.length; i++) {
      if ((linPattern << i & 0x8000) != 0) logPattern |= 1 << LOG_FRM[i];
    }
    return logPattern;
  }

  public static int logToLin(int logPattern) {
    int linPattern = 0;
    for (int i = 0; i < LOG_FRM.length; i++) {
      if ((logPattern >> LOG_FRM[i] & 1) != 0) linPattern |= 0x8000 >> i;
    }
    return linPattern;
  }

  public static boolean isLogVerbose(int logPattern) {
    if (logPattern == 0) return false;
    return (logPattern & 0x5555) == (logPattern >> 1  & 0x5555);
  }

  public static int logShrink(int logPattern) {
    if ((logPattern | 0xFFFF) != 0xFFFF) throw new IllegalStateException();
    int result = 0;
    for (int i1 = 0x8000, i0 = 0x4000; i0 > 0; i1 >>= 2, i0 >>= 2) {
      boolean b0 = (logPattern & i0) != 0;
      boolean b1 = (logPattern & i1) != 0;
      if (b0 != b1) throw new IllegalStateException();
      result = result << 1 | (b0 ? 1 : 0);
    }
    return result;
  }

  public static int logExpand(int logPattern) {
    if ((logPattern | 0xFF) != 0xFF) throw new IllegalStateException();
    int result = 0;
    for (int i = 0x80; i > 0; i >>= 1) {
      result = result << 2 | ((logPattern & i) != 0 ? 3 : 0);
    }
    return result;
  }

  public final int pattern;
  public final int verbosity;
  public final int linear;

  public LogDrum(int pattern, int verbosity) {
    this.pattern = pattern;
    this.verbosity = verbosity;
    int result = pattern;
    for (int i = 0; i < verbosity; i++) {
      result = logExpand(result);
    }
    linear = logToLin(result);
  }

  public static LogDrum fromLin(int pattern) {
    pattern = linToLog(pattern);
    int verbosity = 0;
    while (isLogVerbose(pattern)) {
      pattern = logShrink(pattern);
      verbosity++;
    }
    return new LogDrum(pattern, verbosity);
  }

  public int toLin() {
    int result = pattern;
    for (int i = 0; i < verbosity; i++) {
      result = logExpand(result);
    }
    return logToLin(result);
  }

  @Override
  public String toString() {
    return String.format("LogDrum{%04X,%X,%d}", linear, pattern, verbosity);
  }
}
