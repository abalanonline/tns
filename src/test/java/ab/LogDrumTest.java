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

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

class LogDrumTest {

  @Test
  void linToLogToLin() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < 0x100; i++) {
      int pattern = random.nextInt(0x10000);
      assertEquals(pattern, LogDrum.linToLog(LogDrum.logToLin(pattern)));
      assertEquals(pattern, LogDrum.logToLin(LogDrum.linToLog(pattern)));
    }
    assertEquals(0, LogDrum.linToLog(0));
    assertEquals(1, LogDrum.linToLog(0x8000));
    assertEquals(2, LogDrum.linToLog(0x0080));
    assertEquals(3, LogDrum.linToLog(0x8080));
    assertEquals(0xFFFF, LogDrum.linToLog(0xFFFF));
  }

  @Test
  void verbosity() {
    assertEquals(1, LogDrum.fromLin(0x8080).verbosity);
    assertEquals(1, LogDrum.fromLin(0x7979).verbosity);
    assertEquals(2, LogDrum.fromLin(0x8888).verbosity);
    assertEquals(2, LogDrum.fromLin(0x2222).verbosity);
    assertEquals(3, LogDrum.fromLin(0xAAAA).verbosity);
    assertEquals(3, LogDrum.fromLin(0x5555).verbosity);
    assertEquals(4, LogDrum.fromLin(0xFFFF).verbosity);
  }

  @Test
  void fromLinToLin() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < 0x100; i++) {
      int pattern = random.nextInt(0x10000);
      assertEquals(pattern, LogDrum.fromLin(pattern).toLin());
    }
    assertEquals(0, LogDrum.fromLin(0).toLin());
    assertEquals(0x8000, LogDrum.fromLin(0x8000).toLin());
    assertEquals(0xAAAA, LogDrum.fromLin(0xAAAA).toLin());
    assertEquals(0xFFFF, LogDrum.fromLin(0xFFFF).toLin());
  }
}
