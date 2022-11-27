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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Melody {
  public List<List<MelodicPattern>> patterns;

  public Melody() {
    this.patterns = new ArrayList<>();
    patterns.add(new ArrayList<>());
    patterns.add(new ArrayList<>());
  }

  public static Melody onePattern(MelodicPattern melodicPattern) {
    Melody melody = new Melody();
    melody.patterns.get(0).add(melodicPattern);
    melody.patterns.remove(1);
    return melody;
  }

  public void addDrums(int index, MelodicPattern melodicPattern) {
    patterns.get(0).add(index, melodicPattern);
  }

  public void addPiano(int index, MelodicPattern melodicPattern) {
    patterns.get(1).add(index, melodicPattern);
  }

  public byte[] toMidi() {
    List<byte[]> midiTracks = patterns.stream().map(instrumentPatterns -> {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      instrumentPatterns.forEach(melodicPattern -> {
        try {
          stream.write(melodicPattern.getMidi());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
      stream.write(0x00);
      stream.write(0xFF);
      stream.write(0x2F);
      stream.write(0x00);
      return stream.toByteArray();
    }).collect(Collectors.toList());

    ByteBuffer result = ByteBuffer.wrap(new byte[
        0x0E + 0x08 * midiTracks.size() + midiTracks.stream().mapToInt(b -> b.length).sum()]);
    result.putInt(0x4D546864).putInt(6).putShort((short) 1).putShort((short) midiTracks.size()).putShort((short) 0xC0);
    midiTracks.forEach(midiTrack -> result.putInt(0x4D54726B).putInt(midiTrack.length).put(midiTrack));
    try { Files.write(Paths.get("target/test.mid"), result.array()); } catch (IOException e) {}
    return result.array();
  }
}
