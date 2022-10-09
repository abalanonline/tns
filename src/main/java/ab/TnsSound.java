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

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class TnsSound implements AutoCloseable, Receiver {

  public static final int C4_MIDI = 60;
  public static final AudioFormat AUDIO_CD = new AudioFormat(44_100, 16, 2, true, false);
  public static final int CHANNELS = 0x40;
  public static final int NOTE_OFF_VELOCITY = 0x40;
  public static final int MIDI_DEFAULT_VELOCITY = 0x60;

  private final AudioFormat audioFormat;
  private final SourceDataLine line;
  public final TsClip[] ch = new TsClip[CHANNELS];
  public Receiver midiReceiver;
  Font soundFont;
  public int midiOutput = 1;

  /**
   * Start sound system. The output line will be open and ready for pcm output and wavetable music synthesis.
   * For pcm: sound.getWav(), write to array, sound.putWav()
   * For music: sound.loadAllInstruments(), sound.noteOffOn(), sound.putWav(sound.getWav());
   * @param audioFormat the desired audio format
   * @param latencyMs the latency in ms, must be longer than maximum estimated time between sound.putWav()
   */
  public TnsSound(AudioFormat audioFormat, int latencyMs) {
    System.out.println("    ____                                ");
    System.out.println("     /        __  /__   __   __   __    ");
    System.out.println("    /  /__/ /__/ /  / /__/ /__/ /  /    ");
    System.out.println("       __/ /    tracker                 ");
    System.out.println();

    try {
      MidiDevice midiDevice = MidiSystem.getMidiDevice(MidiSystem.getMidiDeviceInfo()[0]);
      midiDevice.open();
      this.midiReceiver = midiDevice.getReceiver();
    } catch (MidiUnavailableException e) {
      throw new IllegalStateException(e);
    }

    this.audioFormat = audioFormat;
    try {
      line = AudioSystem.getSourceDataLine(audioFormat);
      if (latencyMs > 0) {
        line.open(audioFormat, (int) (audioFormat.getFrameRate() * latencyMs / 1000) * audioFormat.getFrameSize());
      } else {
        line.open(audioFormat);
      }
    } catch (LineUnavailableException e) {
      throw new IllegalStateException(e);
    }
    line.start();
  }

  public TnsSound(AudioFormat audioFormat) {
    this(audioFormat, 0);
  }

  public TnsSound() {
    this(AUDIO_CD);
  }

  @Override
  public void close() {
    line.stop();
    line.close();
  }

  public int getRate() {
    return (int) audioFormat.getSampleRate();
  }

  public int[] getWav() {
    return new int[line.available() / audioFormat.getFrameSize()];
  }

  public void putWav(int[] wav) {
    for (TsClip clip : ch) {
      if (clip == null || clip.instrument == null) continue;
      byte[] pcm = soundFont.pcm;
      int sampleEnd = clip.instrument.sampleStart + clip.instrument.sampleSize;
      int d0 = clip.sampleRate;
      int d1 = (int) (audioFormat.getSampleRate());
      int v = clip.volume * 2 / 3;
      for (int i = 0; i < wav.length; i++) {
        clip.r += d0;
        clip.framePosition += clip.r / d1;
        clip.r %= d1;
        if (clip.framePosition >= sampleEnd) {
          clip.instrument = null; // drop
          break;
        } else {
          wav[i] += pcm[clip.framePosition * 2 + 1] * v;
        }
      }
    }

    byte[] bytes = new byte[wav.length * audioFormat.getFrameSize()];
    for (int wi = 0, i = 0; wi < wav.length; wi++) {
      int v = wav[wi];
      v = Math.min(v, Short.MAX_VALUE);
      v = Math.max(v, Short.MIN_VALUE);
      for (int channel = 0; channel < audioFormat.getChannels(); channel++) {
        bytes[i++] = (byte) v;
        bytes[i++] = (byte) (v >> 8);
      }
    }
    line.write(bytes, 0, bytes.length);
  }

  /**
   * Loads instruments from sound font to the sound system synthesizer.
   * This enables software wave table synthesizer.
   * @param soundFont with instruments
   */
  public void loadAllInstruments(Font soundFont) {
    this.soundFont = soundFont;
    for (int i = 0; i < ch.length; i++) {
      ch[i] = new TsClip();
    }
  }

  /**
   * Sets the receiver to which sound system will deliver MIDI messages.
   * This enables external (e.g. hardware) synthesizer.
   * @param midiReceiver the midi receiver
   */
  public void setMidiReceiver(Receiver midiReceiver) {
    this.midiReceiver = midiReceiver;
  }

  public void setMidiOutput(int midiOutput) {
    this.midiOutput = midiOutput;
  }

  public void sendMessage(int command, int channel, int data1, int data2, long timeStamp) {
    ShortMessage message = new ShortMessage();
    try {
      message.setMessage(command, channel, data1, data2);
    } catch (InvalidMidiDataException ignore) {
    }
    midiReceiver.send(message, timeStamp);
  }

  public void noteOffOn(int channel, int sample, int key, boolean on) {
    noteOffOn(channel, sample, key, MIDI_DEFAULT_VELOCITY, on);
  }

  /**
   * Note On - Note Off, press key - release key.
   * @param channel midi channel 0-15 or tracker 0-3 (0-7)
   * @param sample midi program number 1-128 or tracker instrument 1-16, sample 0 - use current, no program change
   * @param key midi key, 60 for middle C
   * @param volume midi velocity - 64 mezzo piano, 80 mezzo forte, 96 forte
   * @param on note on if true, note off if false
   */
  public void noteOffOn(int channel, int sample, int key, int volume, boolean on) {
    if ((midiOutput & 1) != 0) {
      if (sample != 0) {
        sendMessage(ShortMessage.PROGRAM_CHANGE, channel, sample - 1, 0, -1);
      }
      sendMessage(on ? ShortMessage.NOTE_ON : ShortMessage.NOTE_OFF, channel, key, volume, -1);
    }
    if ((midiOutput & 2) != 0) {
      if (on) {
        ch[channel].sampleRate = (int) (soundFont.c4spd * Math.exp((key - C4_MIDI) / 12.0 * Math.log(2)));
        Instrument instrument = soundFont.getInstruments()[sample];
        ch[channel].instrument = instrument;
        ch[channel].volume = volume;
        ch[channel].framePosition = instrument.sampleStart;
      } else {
        ch[channel].instrument = null;
      }
    }
  }

  @Override
  public void send(MidiMessage midiMessage, long timeStamp) {
    if (midiMessage instanceof ShortMessage) {
      ShortMessage shortMessage = (ShortMessage) midiMessage;
      int channel = shortMessage.getChannel();
      switch (shortMessage.getCommand()) {
        case ShortMessage.PROGRAM_CHANGE:
          int sample = shortMessage.getData1();
          Instrument instrument = soundFont.getInstruments()[sample];
          ch[channel].instrument = instrument;
          break;
        case ShortMessage.NOTE_ON:
          int note = shortMessage.getData1();
          int velocity = shortMessage.getData2();
          ch[channel].sampleRate = (int) (soundFont.c4spd * Math.exp((note - C4_MIDI) / 12.0 * Math.log(2)));
          ch[channel].volume = velocity;
          ch[channel].framePosition = ch[channel].instrument.sampleStart;
          break;
        case ShortMessage.NOTE_OFF:
          ch[channel].instrument = null;
          break;
        default:
          throw new IllegalStateException("not implemented");
      }
    }
  }

  public static class TsClip {
    Instrument instrument;
    int sampleRate;
    int volume;
    int framePosition;
    int r;
  }

  public static class Instrument {
    String name = "";
    int sampleStart;
    int sampleSize;
    int loopStart;
    int loopSize;
    List<Integer> presetGenerators = new ArrayList<>();
    List<Integer> instrumentGenerators = new ArrayList<>();

    public void setName(String name) {
      this.name = name;
    }

    public void setSampe(int start, int size) {
      sampleStart = start;
      sampleSize = size;
    }

    /**
     * @param start is relative to sample start
     */
    public void setLoop(int start, int size) {
      instrumentGenerators.add(54); // sampleModes
      instrumentGenerators.add(3); // loops for the duration of key depression
      loopStart = start;
      loopSize = size;
    }
  }

  public static class Font {
    byte[] pcm;
    int c4spd;
    String name;
    private int programNumber;
    final Instrument[] instruments;

    public Font(int instruments, byte[] pcm, int c4spd, String name) {
      this.instruments = new Instrument[instruments];
      for (int i = 0; i < instruments; i++) {
        this.instruments[i] = new Instrument();
      }
      this.pcm = pcm;
      this.c4spd = c4spd;
      this.name = name;
    }

    public Instrument[] getInstruments() {
      return instruments;
    }

    private byte[] bag(String id, Function<Instrument, List<Integer>> f) {
      ByteBuffer bytes = newChunk(id, (instruments.length + 1) * 4);
      bytes.putInt(0);
      int i1 = 0;
      for (Instrument instrument : instruments) {
        i1 += f.apply(instrument).size() / 2 + 1;
        bytes.putInt(i1);
      }
      return bytes.array();
    }

    private byte[] gen(String id, Function<Instrument, List<Integer>> f, int generator) {
      List<Integer> list = new ArrayList<>();
      for (int i = 0; i < instruments.length; i++) {
        list.addAll(f.apply(instruments[i]));
        list.add(generator);
        list.add(i);
      }
      list.add(0);
      list.add(0);
      ByteBuffer bytes = newChunk(id, list.size() * 2);
      list.stream().map(Integer::shortValue).forEach(bytes::putShort);
      return bytes.array();
    }

    public byte[] toByteArray() {
      int ins1 = instruments.length + 1;
      ByteBuffer phdr = newChunk("phdr", ins1 * 0x26);
      ByteBuffer inst = newChunk("inst", ins1 * 0x16);
      ByteBuffer shdr = newChunk("shdr", ins1 * 0x2E);
      for (int i = 0; i < instruments.length; i++) {
        Instrument ins = instruments[i];
        byte[] name = paddedStr(ins.name);
        phdr.put(name); inst.put(name); shdr.put(name);
        phdr.putInt(i + programNumber);
        phdr.putInt(i);
        phdr.put(new byte[0x0A]);
        inst.putShort((short) i);
        int start = ins.sampleStart;
        shdr.putInt(start);
        shdr.putInt(start + ins.sampleSize);
        start += ins.loopStart;
        shdr.putInt(start);
        shdr.putInt(start + ins.loopSize);
        shdr.putInt(c4spd);
        shdr.putInt(C4_MIDI); // C4
        shdr.putShort((short) 1);
      }
      phdr.put(paddedStr("EOP"));
      phdr.putInt(0);
      phdr.putInt(instruments.length);
      inst.put(paddedStr("EOI"));
      inst.putShort((short) instruments.length);
      shdr.put(paddedStr("EOS"));

      Function<Instrument, List<Integer>> pfn = instrument -> instrument.presetGenerators;
      Function<Instrument, List<Integer>> ifn = instrument -> instrument.instrumentGenerators;

      return list("RIFF", "sfbk",
          list("LIST", "INFO",
              chunk("ifil", new byte[]{2, 0, 1, 0}), // v 2.01
              chunk("isng", paddedStr("TNS Sound System", -1)),
              chunk("INAM", paddedStr(name, -1))),
          list("LIST", "sdta",
              chunk("smpl", pcm)),
          list("LIST", "pdta",
              phdr.array(),
              bag("pbag", pfn), chunk("pmod", new byte[10]), gen("pgen", pfn, 41), // instrument
              inst.array(),
              bag("ibag", ifn), chunk("imod", new byte[10]), gen("igen", ifn, 53), // sampleID
              shdr.array()));
    }

    private static byte[] paddedStr(String s, int newLength) {
      if (newLength < 0) newLength = s.length() + 1;
      return Arrays.copyOf(s.getBytes(StandardCharsets.ISO_8859_1), newLength);
    }

    private static byte[] paddedStr(String s) {
      return paddedStr(s, 0x14);
    }

    public static ByteBuffer newChunk(String id, int length) {
      assert id.length() == 4;
      length += length & 1;
      ByteBuffer bytes = ByteBuffer.wrap(new byte[length + 8]).order(ByteOrder.LITTLE_ENDIAN);
      bytes.put(paddedStr(id, 4));
      bytes.putInt(length);
      return bytes;
    }

    public static byte[] list(String id, String key, byte[]... chunks) {
      int length = Arrays.stream(chunks).mapToInt(b -> b.length).sum() + 4;
      ByteBuffer bytes = newChunk(id, length);
      bytes.put(paddedStr(key, 4));
      Arrays.stream(chunks).forEach(bytes::put);
      return bytes.array();
    }

    public static byte[] chunk(String id, byte[] content) {
      return newChunk(id, content.length).put(content).array();
    }

  }
}
