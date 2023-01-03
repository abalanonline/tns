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

class ExponentialFunctionTest {

  @Test
  void apply() {
    ExponentialFunction ef = new ExponentialFunction(132, 664, -201, -661, -1085);
    assertEquals(-201, ef.apply(132));
    assertEquals(-661, ef.apply(398));
    assertEquals(-1085, ef.apply(664));
    ExponentialFunction ef2 = new ExponentialFunction(0, 0xFFFFFF, 1, 0x10, 0xFFFF);
    assertEquals(1, ef2.apply(0));
    assertEquals(0x10, ef2.apply(0x7FFFFF));
    assertEquals(0xFFFF, ef2.apply(0xFFFFFF));
    ExponentialFunction linear = new ExponentialFunction(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, 5, 9);
    assertEquals(5, linear.apply(0));
  }
}
