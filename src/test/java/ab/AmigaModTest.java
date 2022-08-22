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

import static org.junit.jupiter.api.Assertions.*;

class AmigaModTest {

  @Test
  void testNoteCode() {
    int[] codes = {
        856, 808, 762, 720, 678, 640, 604, 570, 538, 508, 480, 453,
        428, 404, 381, 360, 339, 320, 302, 285, 269, 254, 240, 226,
        214, 202, 190, 180, 170, 160, 151, 143, 135, 127, 120, 113,
    };
    for (int i = 12; i < codes.length; i++) {
      assertEquals(i + 48, AmigaMod.Note.noteCodeToMidi(codes[i]));
      assertEquals(codes[i], AmigaMod.Note.noteMidiToCode(i + 48));
    }
  }
}
