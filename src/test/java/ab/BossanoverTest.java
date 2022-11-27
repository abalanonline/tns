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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

class BossanoverTest {

  public static final int[] DRUMS_4FL = {0, 0x8888, 2, 0x0808, 11, 0xAAAA}; // four on the floor
  public static final int[] DRUMS_MET = {14, 0x8888, 2, 0x0808, 0, 0xFFFF}; // metal, bass drum swapped with cymbals
  public static final int[] DRUMS_MTW = {2, 0x8888, 0, 0x8022, 11, 0xAAAA}; // motown beat
  public static final int[] DRUMS_JAZ = {15, 0x8989, 12, 0x0808}; // jazz
  public static final int[] DRUMS_BSN = {0, 0x9999, 7, 0x9224, 11, 0xFFFF}; // bossa nova

  // 60s britpop AC BD1, AC SD1, RIDE
  public static final int[] DRUMS_TNK = {0, 0x8280, 2, 0x0830, 15, 0xAAAA};
  public static final int[] DRUMS_WGO = {0, 0x8080, 2, 0x0808, 11, 0x9999}; // shuffle hihat
  // 90s britpop EL BD2, EL SD2, CH
  public static final int[] DRUMS_LFB1 = {1, 0x8220, 3, 0x082D, 11, 0xAAAA};
  public static final int[] DRUMS_LFB2 = {1, 0x8220, 3, 0x0822, 11, 0xAAAA};

  // TODO: 2022-10-16 make sure it's able to produce all of the Nyango Star patterns https://youtu.be/OnkTUKtxRic

  @Test
  void midi707short() throws IOException {
    Files.write(Paths.get("target/d_4fl.mid"), Melody.onePattern(DrumPattern.newShort(DRUMS_4FL)).toMidi());
    Files.write(Paths.get("target/d_met.mid"), Melody.onePattern(DrumPattern.newShort(DRUMS_MET)).toMidi());
    Files.write(Paths.get("target/d_mtw.mid"), Melody.onePattern(DrumPattern.newShort(DRUMS_MTW)).toMidi());
    Files.write(Paths.get("target/d_jaz.mid"), Melody.onePattern(DrumPattern.newShort(DRUMS_JAZ)).toMidi());
    Files.write(Paths.get("target/d_bsn.mid"), Melody.onePattern(DrumPattern.newShort(DRUMS_BSN)).toMidi());

    Files.write(Paths.get("target/d_tnn.mid"), Melody.onePattern(DrumPattern.newShort(DRUMS_TNK)).toMidi());
    Melody lfb = new Melody();
    lfb.addDrums(DrumPattern.newShort(DRUMS_LFB1));
    lfb.addDrums(DrumPattern.newShort(DRUMS_LFB2));
    Files.write(Paths.get("target/d_lfb.mid"), lfb.toMidi());
  }

  @Test
  void logTest() {
    System.out.println(LogDrum.fromLin(0x8888));
    System.out.println(LogDrum.fromLin(0x0808));
    System.out.println(LogDrum.fromLin(0x8080));
    System.out.println(LogDrum.fromLin(0xAAAA));
    System.out.println(LogDrum.fromLin(0xFFFF));
    System.out.println(LogDrum.fromLin(0x8022));
    System.out.println(LogDrum.fromLin(0x8989));
    System.out.println(LogDrum.fromLin(0x9999));
    System.out.println(LogDrum.fromLin(0x9224));

    System.out.println(LogDrum.fromLin(0x8280));
    System.out.println(LogDrum.fromLin(0x0830));
    System.out.println(LogDrum.fromLin(0x8220));
    System.out.println(LogDrum.fromLin(0x082D));
    System.out.println(LogDrum.fromLin(0x0822));
  }
}
