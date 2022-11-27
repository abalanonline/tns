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

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * And over and over.
 */
public class Bossanover {

  public static final String[] KEY_NAMES = {
      "BD1", "BD2", "SD1", "SD2", "L-T", "M-T", "H-T", "RIM",
      "COW", "HCP", "TAMB", "CH", "CH", "OH", "CRASH", "RIDE",
      "SHORT WHIS", "LONG WHIS"}; // TR-727 essentials
  public static final int[] KEY_NUMBERS = {
      35, 36, 38, 40, 41, 45, 48, 37,
      56, 39, 54, 42, 44, 46, 49, 51,
      71, 72
  };
  // loud and rare - put any numbers that make sense - decibels, percents, ratings, etc
  public static final int[] DRUM_LOUD = {
      2, 2, 3, 3, 1, 1, 1, 2,
      1, 1, 1, 0, 0, 1, 3, 2,
      2, 2
  };
  public static final int[] DRUM_RARE = {
      -1, 0, -1, 0, 1, 1, 1, 2,
      3, 3, 3, -1, 1, 0, 2, 2,
      3, 3
  };

  public static byte[] midi707full(int... patterns) {
    if (Arrays.stream(patterns).anyMatch(p -> (p | 0xFFFF) != 0xFFFF)) throw new IllegalStateException();

    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    stream.write(0x00);
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
    stream.write(0xFF);
    stream.write(0x2F);
    stream.write(0x00);

    ByteBuffer result = ByteBuffer.wrap(new byte[stream.size() + 0x16]);
    result.putInt(0x4D546864).putInt(6).putShort((short) 1).putShort((short) 1).putShort((short) 0xC0);
    result.putInt(0x4D54726B).putInt(stream.size()).put(stream.toByteArray());
    try { Files.write(Paths.get("target/test.mid"), result.array()); } catch (IOException e) {}
    return result.array();
  }

  public static byte[] midi707short(int... patterns) {
    int[] fullPattern = new int[KEY_NUMBERS.length];
    for (int i = 1; i < patterns.length; i += 2) {
      fullPattern[patterns[i - 1]] = patterns[i];
    }
    return midi707full(fullPattern);
  }

  private static String patternToString(int pattern) {
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0, mask = 0x8000; mask != 0; i++, mask >>= 1) {
      if (i % 4 == 0) stringBuilder.append(' ');
      stringBuilder.append((pattern & mask) == 0 ? '-' : '#');
    }
    return stringBuilder.toString();
  }

  public static Receiver getTheBestMidiReceiver() {
    MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
    for (int i = devices.length - 1; i >= 0; i--) {
      try {
        MidiDevice midiDevice = MidiSystem.getMidiDevice(devices[i]);
        Receiver receiver = midiDevice.getReceiver();
        midiDevice.open();
        return receiver;
      } catch (MidiUnavailableException e) {
      }
    }
    return null;
  }

  ExponentialFunction.RandomInt rPattern;
  ExponentialFunction.RandomInt rVerbosity;
  ExponentialFunction.RandomInt rInstrument;
  int[] aInstrument;
  int[] aVolume;
  public static final int INT_VOLUMES = 5;
  public Bossanover() {
    Random random = ThreadLocalRandom.current();
    rPattern = new ExponentialFunction.RandomInt(random, 1, 0x40, 0xFFFF);
    rVerbosity = new ExponentialFunction.RandomInt(random, 0, 1.2, 3);
    rInstrument = new ExponentialFunction.RandomInt(random, 0, 4, KEY_NUMBERS.length - 1);
    int[] loud = IntStream.range(0, KEY_NUMBERS.length)
        .boxed().sorted(Comparator.comparingInt(i -> DRUM_LOUD[i])).mapToInt(i -> i).toArray();
    aVolume = new int[loud.length];
    for (int i = 0; i < loud.length; i++) {
      aVolume[loud[i]] = i * INT_VOLUMES / loud.length;
    }
    aInstrument = IntStream.range(0, KEY_NUMBERS.length)
        .boxed().sorted(Comparator.comparingInt(i -> DRUM_RARE[i])).mapToInt(i -> i).toArray();
  }

  public int patternVolume(int pattern) {
    int v = 0;
    for (int b = 1; b < 0x10000; b <<= 1) {
      v += (pattern & b) == 0 ? 0 : 1;
    }
    return v;
  }

  public int randomPattern() {
    int result = 0;
    for (int i = 0; i < 3; i++) {
      int v = rVerbosity.nextInt(); // 10000, 100, 10, 4 // 16, 8, 4, 2 // 4, 3, 2, 1 // 0, 1, 2, 3
      int p = rPattern.nextInt() & ((1 << (1 << 4 - v)) - 1);
      if (v == 3) p = ((p - 1) & 1) + 1; // for v3 the only two patterns make sense 01 and 10
      result = new LogDrum(p, v).linear;
      if (result != 0) break; // two retries if empty pattern is generated
    }
    return result;
  }

  public int randomPattern(int instrument) {
    int[] patterns = IntStream.range(0, INT_VOLUMES).mapToObj(a -> randomPattern())
        .sorted(Comparator.comparingInt(this::patternVolume)).mapToInt(p -> p).toArray();
    int i = INT_VOLUMES - 1 - aVolume[instrument];
    return patterns[i];
  }

  public int randomInstrument() {
    return aInstrument[rInstrument.nextInt()];
  }

  public int[] bossanoving() {
    //return new int[]{0x8888, 0, 0x0808, 0, 0, 0, 0, 0, 0, 0, 0, 0xAAAA};
    int[] bossanova = new int[KEY_NUMBERS.length];
    for (int i = 0; i < 4; i++) {
      int instrument = randomInstrument();
      bossanova[instrument] = randomPattern(instrument);
    }
    return bossanova;
  }

  // kick, snare, closed hh, open hh, clap/rim, ride, hi bell, low bell
  public static void main( String[] args ) throws Exception {
    Sequencer sequencer = MidiSystem.getSequencer(false);
    sequencer.getTransmitter().setReceiver(getTheBestMidiReceiver());
    sequencer.open();
    Bossanover bossanover = new Bossanover();
    System.out.println("TNS Bossanover. Enter to bossanove, q to quit.");
    for (int c = System.in.read(); (c | 0x20) != 'q'; c = System.in.read()) {
      switch (c) {
        case '\n':
          int[] bossanova = bossanover.bossanoving();
          sequencer.setLoopCount(0);
          while (sequencer.isRunning()) Thread.sleep(10);
          for (int pattern = 0; pattern < bossanova.length; pattern++) {
            if (bossanova[pattern] != 0) {
              System.out.println(String.format("%-5s%s", KEY_NAMES[pattern], patternToString(bossanova[pattern])));
            }
          }
          sequencer.setSequence(MidiSystem.getSequence(new ByteArrayInputStream(midi707full(bossanova))));
          sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
          sequencer.start();
          break;
        default:
      }
    }
    sequencer.stop();
    sequencer.close();
  }

}
