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
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

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
    //try { Files.write(Paths.get("test.mid"), result.array()); } catch (IOException e) {}
    return result.array();
  };

  public static byte[] midi707short(int... patterns) {
    int fullPattern[] = new int[KEY_NUMBERS.length];
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

  public int[] bossanoving() {
    //return new int[]{0x8888, 0, 0x0808, 0, 0, 0, 0, 0, 0, 0, 0, 0xAAAA};
    int[] bossanova = new int[16];
    Random random = ThreadLocalRandom.current();
    ExponentialFunction ef = new ExponentialFunction(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, 0x100, 0xFFFF);
    bossanova[0] = new LogDrum(ef.apply(random.nextInt()), 0).pattern;
    bossanova[2] = new LogDrum(ef.apply(random.nextInt()), 0).pattern;
    bossanova[11] = new LogDrum(ef.apply(random.nextInt()), 0).pattern;
    return bossanova;
  }

  // kick, snare, closed hh, open hh, clap/rim, ride, hi bell, low bell
  public static void main( String[] args ) throws Exception {
    Sequencer sequencer = MidiSystem.getSequencer(false);
    sequencer.getTransmitter().setReceiver(getTheBestMidiReceiver());
    sequencer.open();
    System.out.println("TNS Bossanover. Enter to bossanove, q to quit.");
    for (int c = System.in.read(); (c | 0x20) != 'q'; c = System.in.read()) {
      switch (c) {
        case '\n':
          int[] bossanova = new Bossanover().bossanoving();
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
