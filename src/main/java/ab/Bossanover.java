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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * And over and over.
 */
public class Bossanover {

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
    rInstrument = new ExponentialFunction.RandomInt(random, 0, 4, DrumPattern.DRUM_NUMBER - 1);
    int[] loud = IntStream.range(0, DrumPattern.DRUM_NUMBER)
        .boxed().sorted(Comparator.comparingInt(i -> DRUM_LOUD[i])).mapToInt(i -> i).toArray();
    aVolume = new int[loud.length];
    for (int i = 0; i < loud.length; i++) {
      aVolume[loud[i]] = i * INT_VOLUMES / loud.length;
    }
    aInstrument = IntStream.range(0, DrumPattern.DRUM_NUMBER)
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

  public MelodicPattern getDrums() {
    //return new int[]{0x8888, 0, 0x0808, 0, 0, 0, 0, 0, 0, 0, 0, 0xAAAA};
    int[] fullDrums = new int[DrumPattern.DRUM_NUMBER];
    for (int i = 0; i < 4; i++) {
      int instrument = randomInstrument();
      fullDrums[instrument] = randomPattern(instrument);
    }
    return new DrumPattern(fullDrums);
  }

  public MelodicPattern getSchwifty() {
    Random random = ThreadLocalRandom.current();
    ArrayList<Integer> progressionPool = new ArrayList<>();
    IntStream.range(0, 6).forEach(i -> {
      progressionPool.add(i);
      progressionPool.add(i);
    });
    String key = String.valueOf((char) (random.nextInt(7) + 'A'));
    String progression = IntStream.range(0, 4)
        .mapToObj(i -> {
          int id = random.nextInt(progressionPool.size());
          String s = new ProgressionPattern.LatinNumeral(progressionPool.get(id) + 1).toString();
          progressionPool.remove(id);
          return random.nextInt(4) == 0 ? s.toLowerCase() : s.toUpperCase();
        })
        .collect(Collectors.joining("-"));
    return new ProgressionPattern(key, progression);
  }


  public static final int[] PIANO_INSTRUMENT = new int[]{
      1, 2, 3, 5, 6, 8,
      25, 26, 27, 28, 29, 32,
      33, 34, 35, 37, 38,
      41, 42, 43,
      57, 60,
  };
  public Melody bossanoving() {
    Random random = ThreadLocalRandom.current();
    //return new int[]{0x8888, 0, 0x0808, 0, 0, 0, 0, 0, 0, 0, 0, 0xAAAA};
    int nDrums = 4;
    int[] shortDrums = new int[nDrums * 2];
    for (int i = 0; i < nDrums; i++) {
      int instrument = randomInstrument();
      shortDrums[i * 2] = instrument;
      shortDrums[i * 2 + 1] = randomPattern(instrument);
    }
    DrumPattern drumPattern = DrumPattern.newShort(shortDrums);
    Melody melody = new Melody();
    int[] tc = new int[]{2, 8, 4, 4, 4, 2}; // time code

    melody.addMeta(new MidiMeta().tempo(random.nextInt(8) * 10 + 70)); // 70-140
    melody.addDrums(drumPattern, tc[0] + tc[1]);
    melody.addDrums(getDrums(), tc[2] + tc[3]);
    melody.addDrums(drumPattern, tc[4] + tc[5]);
    melody.addPiano(new MidiMeta().instrument(PIANO_INSTRUMENT[random.nextInt(PIANO_INSTRUMENT.length)]));
    melody.addPiano(DrumPattern.EMPTY, tc[0]);
    melody.addPiano(getSchwifty(), (tc[1] + tc[2]) / 4);
    melody.addPiano(getSchwifty(), (tc[3] + tc[4]) / 4);
    melody.addPiano(DrumPattern.EMPTY, tc[5]);

    return melody;
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
          Melody bossanova = bossanover.bossanoving();
          sequencer.setLoopCount(0);
          while (sequencer.isRunning()) Thread.sleep(10);
          System.out.println(bossanova);
          sequencer.setSequence(MidiSystem.getSequence(new ByteArrayInputStream(bossanova.toMidi())));
          //sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
          sequencer.start();
          break;
        default:
      }
    }
    sequencer.stop();
    sequencer.close();
  }

}
