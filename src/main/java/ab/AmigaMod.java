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
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

public class AmigaMod {

  public static final int C4_MIDI = 60;
  public static final double C9_FREQUENCY = 8372.018; // Hz
  public static final double NTSC_COLORBURST = 1_000_000.0 * 315 / 88; // 315/88 MHz
  // See how these constants were made
  public static final int C4_DIVISOR = (int) Math.round(NTSC_COLORBURST / C9_FREQUENCY); // 428
  public static final int C4_RATE = (int) Math.round(NTSC_COLORBURST / C4_DIVISOR); // 8363

  public final ByteBuffer bytes;
  public final int patternSize;
  public int samplesSize = 0x20;
  public final int[] samples = new int[samplesSize];
  public final int orderPos;
  public final int patternPos;
  public final int[] midiInstrumentMap = new int[samplesSize];

  private static byte[] readAllBytes(InputStream stream) {
    try (stream) {
      return stream.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public AmigaMod(InputStream stream) {
    this(stream, null);
  }

  public AmigaMod(InputStream stream, int[] midiInstrumentMap) {
    bytes = ByteBuffer.wrap(readAllBytes(stream));
    if (bytes.getInt(0x438) != 0x4D2E4B2E) {
      samplesSize = 0x10;
      throw new IllegalStateException("mod file error");
    }
    orderPos = samplesSize > 0x10 ? 0x3B6 : 0x1D6;
    patternPos = samplesSize > 0x10 ? 0x43C : 0x258;
    bytes.position(orderPos + 2);
    int maxPattern = 0;
    int maxOrder = 0;
    for (int i = 0; i < 0x80; i++) {
      byte b = bytes.get();
      maxPattern = Math.max(maxPattern, b);
      maxOrder = b == 0 ? maxOrder : i;
    }
    patternSize = maxPattern + 1;
    for (int i = 0, sampleStart = 0; i < samplesSize; i++) {
      samples[i] = sampleStart;
      sampleStart += i == 0 ? patternPos + patternSize * 0x400 : getSampleSize(i);
    }
    for (int i = 0; i < this.midiInstrumentMap.length; i++) {
      this.midiInstrumentMap[i] = i + 1;
    }
    if (midiInstrumentMap != null) {
      System.arraycopy(midiInstrumentMap, 0, this.midiInstrumentMap, 1,
          Math.min(midiInstrumentMap.length, this.midiInstrumentMap.length - 1));
    }
  }

  public int getSampleSize(int sample) {
    return sample == 0 ? samples[1] : bytes.getShort(sample * 0x1E + 0x0C) << 1 & 0x1FFFF;
  }

  public String getSongName() {
    return new String(Arrays.copyOfRange(bytes.array(), 0, 0x14), StandardCharsets.ISO_8859_1);
  }

  public String getSampleName(int sample) {
    if (sample == 0) return getSongName();
    return new String(Arrays.copyOfRange(bytes.array(), sample * 0x1E - 0x0A, sample * 0x1E + 0x0C),
        StandardCharsets.ISO_8859_1);
  }

  public int getSampleStart(int sample) {
    return samples[sample];
  }

  @Deprecated
  public int getSampleEnd(int sample) {
    return getSampleStart(sample) + getSampleSize(sample);
  }

  public int getOrderSize() {
    return bytes.get(orderPos);
  }

  /**
   * Definition of Loop.
   */
  public boolean isLoop(int sample) {
    // FIXME: 2022-08-18 this is wrong
    return sample == 0 ? true : getLoopStart(sample) != 0;
  }

  public int getLoopStart(int sample) {
    return sample == 0 ? 0 : bytes.getShort(sample * 0x1E + 0x10) << 1 & 0x1FFFF;
  }

  public int getLoopLength(int sample) {
    return sample == 0 ? samples[1] : bytes.getShort(sample * 0x1E + 0x12) << 1 & 0x1FFFF;
  }

  public byte[] toMidiBytes() {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    stream.write(0x00);
    getSequencer(midiMessage -> {
      try {
        stream.write(midiMessage.getMessage());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      stream.write(midiMessage instanceof MetaMessage && ((MetaMessage) midiMessage).getType() == 1 ? 1 : 0);
    }).start();

    stream.write(0xFF);
    stream.write(0x2F);
    stream.write(0x00);

    ByteBuffer result = ByteBuffer.wrap(new byte[stream.size() + 0x16]);
    result.putInt(0x4D546864).putInt(6).putShort((short) 1).putShort((short) 1).putShort((short) 0x04);
    result.putInt(0x4D54726B).putInt(stream.size()).put(stream.toByteArray());
    return result.array();
  }

  public InputStream toMidi() {
    return new ByteArrayInputStream(toMidiBytes());
  }

  public TyphoonSound.Font toSoundFont() {
    byte[] pcm8 = this.bytes.array();
    byte[] pcm16 = new byte[pcm8.length * 2];
    for (int i = 0, i1 = 1; i < pcm8.length; i++, i1 += 2) {
      pcm16[i1] = pcm8[i];
    }

    TyphoonSound.Font soundFont = new TyphoonSound.Font(this.samplesSize, pcm16, 8363, this.getSongName());
    TyphoonSound.Instrument[] ins = soundFont.getInstruments();
    for (int i = 0; i < ins.length; i++) {
      ins[i].setName(this.getSampleName(i));
      ins[i].setSampe(this.getSampleStart(i), this.getSampleSize(i));
      if (this.isLoop(i)) ins[i].setLoop(this.getLoopStart(i), this.getLoopLength(i));
    }
    return soundFont;
  }

  public InputStream toSoundbank() {
    return new ByteArrayInputStream(toSoundFont().toByteArray());
  }

  public Sequencer getSequencer(Consumer<MidiMessage> consumer) {
    return new Sequencer(consumer);
  }

  public class Sequencer {
    private final Consumer<MidiMessage> consumer;

    private int row;

    public Sequencer(Consumer<MidiMessage> consumer) {
      this.consumer = consumer;
    }

    public void inc() {
      this.row++;
    }

    public int getOrder() {
      return row >> 6;
    }

    public int getPattern() {
      return bytes.get(orderPos + 2 + getOrder());
    }

    public int getRow() {
      return row & 0x3F;
    }

    public int getLoop() {
      return getOrder() / getOrderSize();
    }

    public Note[] getNotes() {
      int position = ((getPattern() << 6) + getRow() << 4) + patternPos;
      bytes.position(position);
      Note[] result = new Note[4];
      for (int c = 0; c < 4; c++) {
        result[c] = new Note(bytes.getInt());
      }
      return result;
    }

    private void sendMessage(int command, int channel, int data1, int data2, long timeStamp) {
      ShortMessage message = new ShortMessage();
      try {
        message.setMessage(command, channel, data1, data2);
      } catch (InvalidMidiDataException ignore) {
      }
      consumer.accept(message);
    }

    private void noteOffOn(int channel, int sample, int key, boolean on) {
      if (sample != 0) {
        int instrument = midiInstrumentMap[sample];
        if (instrument == 0) return;
        if (instrument < 0) {
          instrument = -instrument;
          channel = 9;
        } else {
          sendMessage(ShortMessage.PROGRAM_CHANGE, channel, instrument - 1, 0, -1);
        }
      }
      sendMessage(on ? ShortMessage.NOTE_ON : ShortMessage.NOTE_OFF, channel, key, 0x60, -1);
    }

    private void sendMeta(int type, byte[] data) {
      try {
        consumer.accept(new MetaMessage(type, data, data.length));
      } catch (InvalidMidiDataException e) {
        throw new IllegalStateException(e);
      }
    }

    private void setSpeedTempo(int speed, int tempo) {
      long microseconds = 10_000_000L * speed / tempo;
      byte[] data = BigInteger.valueOf(microseconds).toByteArray();
      sendMeta(0x51, data);
    }

    private void sendText(String s) {
      sendMeta(1, s.getBytes());
    }

    public void start() {
      int bpmSpeed = 6;
      int bpmTempo = 125;
      setSpeedTempo(bpmSpeed, bpmTempo);
      int[] chSample = new int[0x20];
      int[] chNote = new int[0x20];
      for (; this.getLoop() == 0; this.inc()) {
        StringBuffer s = new StringBuffer(String.format("\r  %02d/%02d", this.getOrder(), this.getRow()));
        Note[] notes = this.getNotes();
        for (int c = 0; c < 4; c++) {
          Note note = notes[c];
          s.append(" | ").append(note);
          if (note.isNoteOn()) {
            this.noteOffOn(c, chSample[c], chNote[c], false);
            chSample[c] = note.getSample();
            chNote[c] = note.getMidiNote();
            this.noteOffOn(c, chSample[c], chNote[c], true);
          }
          switch (note.getFxCommand()) {
            case 0xF:
              int d = note.getFxData();
              if (d == 0) break;
              if (d < 0x20) {
                bpmSpeed = d;
              } else {
                bpmTempo = d;
              }
              setSpeedTempo(bpmSpeed, bpmTempo);
              break;
          }
        }
        sendText(s.toString());
        for (int c = 0; c < 4; c++) {
          Note note = notes[c];
          switch (note.getFxCommand()) {
            case 0xD:
              for (int i = this.getOrder(); i == this.getOrder(); this.inc()) {}
              break;
          }
        }
      }
    }

  }

  public static class Note {
    public static final String CDEFGAB0 = "CCDDEFFGGAAB";
    public static final String CDEFGAB1 = "-#-#--#-#-#-";
    public static final String FX = ".123456789ABCDEF";
    private final int data;
    private final int midiNote;

    /**
     * Divisors table have features in its structure.
     * It cannot be replaced by 428.0 / Math.exp((noteMidi - 60) / 12.0 * Math.log(2))
     */
    private static final int[] DIVISORS = {
        856, 808, 762, 720, 678, 640, 604, 570, 538, 508, 480, 453,
        428, 404, 381, 360, 339, 320, 302, 285, 269, 254, 240, 226,
        214, 202, 190, 180, 170, 160, 151, 143, 135, 127, 120, 113,
    };

    public static int noteCodeToMidi(int noteCode) {
      double log2 = Math.log((double) C4_DIVISOR / noteCode) / Math.log(2);
      return (int) Math.round(C4_MIDI + log2 * 12);
    }

    public static int noteMidiToCode(int noteMidi) {
      noteMidi = noteMidi - C4_MIDI + 12;
      return noteMidi < 0 ? 0 : noteMidi < DIVISORS.length ? DIVISORS[noteMidi] : 0;
    }

    public Note(int data) {
      this.data = data;
      int noteCode = getNoteCode();
      midiNote = noteCode > 0 ? noteCodeToMidi(noteCode) : 0;
    }

    public int getNoteCode() {
      return (data >> 16) & 0x0FFF;
    }

    /**
     * Definition of Note On.
     */
    public boolean isNoteOn() {
      return getNoteCode() != 0;
    }

    public int getMidiNote() {
      return midiNote;
    }

    public int getSample() {
      return data >> 24 & 0xF0 | data >> 12 & 0x0F;
    }

    public int getFxCommand() {
      return data >> 8 & 0x0F;
    }

    public int getFxData() {
      return data & 0xFF;
    }

    @Override
    public String toString() {
      return String.format("%s %s %c%02X",
          !isNoteOn() ? "..." : String.format("%c%c%d",
              CDEFGAB0.charAt(midiNote % 12), CDEFGAB1.charAt(midiNote % 12), (midiNote / 12 - 1)),
          getSample() == 0 ? ".." : String.format("%02d", getSample()),
          FX.charAt(getFxCommand()), getFxData());
    }
  }

}
