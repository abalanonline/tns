package ab;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

public class App {

  public static void testMidi() {
    try {
      Sequencer sequencer = MidiSystem.getSequencer();
      sequencer.open();
      Sequence sequence = MidiSystem.getSequence(Files.newInputStream(Paths.get("test.mid")));
      sequencer.setSequence(sequence);
      sequencer.start();
    } catch (MidiUnavailableException | InvalidMidiDataException | IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static void main( String[] args ) {
    InputStream inputStream;
    try {
      inputStream = Files.newInputStream(Paths.get("test.mod"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    AmigaMod mod = new AmigaMod(inputStream);
    TyphoonSound sound = new TyphoonSound();

    try {
      MidiDevice midiDevice = MidiSystem.getMidiDevice(MidiSystem.getMidiDeviceInfo()[0]);
      midiDevice.open();
//      sound.setMidiReceiver(midiDevice.getReceiver());

      byte[] pcm8 = mod.bytes.array();
      byte[] pcm16 = new byte[pcm8.length * 2];
      for (int i = 0, i1 = 1; i < pcm8.length; i++, i1 += 2) {
        pcm16[i1] = pcm8[i];
      }

      TyphoonSound.Font soundFont = new TyphoonSound.Font(mod.samplesSize, pcm16, 8363, mod.getSongName());
      TyphoonSound.Instrument[] ins = soundFont.getInstruments();
      for (int i = 0; i < ins.length; i++) {
        ins[i].setName(mod.getSampleName(i));
        ins[i].setSampe(mod.getSampleStart(i), mod.getSampleSize(i));
        if (mod.isLoop(i)) ins[i].setLoop(mod.getLoopStart(i), mod.getLoopLength(i));
      }
      Soundbank soundbank = MidiSystem.getSoundbank(new ByteArrayInputStream(soundFont.toByteArray()));
      ((Synthesizer) midiDevice).loadAllInstruments(soundbank);
      sound.loadAllInstruments(soundFont);

    } catch (MidiUnavailableException | InvalidMidiDataException | IOException ignore) {
    }

    int bpmSpeed = 6;
    int bpmTempo = 125;

    sound.putWav(sound.getWav());
    Instant now = Instant.now();
    int[] chSample = new int[0x20];
    int[] chNote = new int[0x20];
    int[] chVolume = new int[0x20];
    for (AmigaMod.Sequencer sequencer = mod.newSequencer(); sequencer.getLoop() == 0; sequencer.inc()) {
      try {
        System.out.print(String.format("\r  %02d/%02d", sequencer.getOrder(), sequencer.getRow()));
        AmigaMod.Note[] notes = sequencer.getNotes();
        for (int c = 0; c < 4; c++) {
          AmigaMod.Note note = notes[c];
          System.out.print(" | " + note);
          int volume = 0x40;
          switch (note.getFxCommand()) {
            case 0xC:
              volume = note.getFxData();
              break;
            case 0xF:
              int d = note.getFxData();
              if (d == 0) break;
              if (d < 0x20) {
                bpmSpeed = d;
              } else {
                bpmTempo = d;
              }
              break;
          }
          if (note.isNoteOn()) {
            sound.noteOffOn(c, chSample[c], chNote[c], chVolume[c], false);
            if (note.getSample() > 0) chSample[c] = note.getSample();
            chNote[c] = note.getMidiNote();
            chVolume[c] = volume * 3 / 2;
            sound.noteOffOn(c, chSample[c], chNote[c], chVolume[c], true);
          }
        }
        System.out.println();
        sound.putWav(sound.getWav());
        now = now.plusNanos(2_500_000_000L * bpmSpeed / bpmTempo);
        Duration duration = Duration.between(Instant.now(), now);
        if (duration.isNegative()) {
          now = Instant.now();
          continue;
        }
        Thread.sleep(duration.toMillis());
        for (int c = 0; c < 4; c++) {
          AmigaMod.Note note = notes[c];
          switch (note.getFxCommand()) {
            case 0xD:
              for (int i = sequencer.getOrder(); i == sequencer.getOrder(); sequencer.inc()) {}
              break;
          }
        }
      } catch (InterruptedException e) {
        break;
      }
    }
  }

}
