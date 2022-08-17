package ab;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.sampled.AudioFormat;
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
    byte[] bytes = mod.bytes.array();
    for (int i = 0; i < 4; i++) {
      TyphoonSound.TsClip clip = new TyphoonSound.TsClip();
      clip.open(new AudioFormat(8363, 8, 1, true, false), bytes, 0, bytes.length);
      clip.setFramePosition(0x43C + mod.patternSize * 0x400);
      clip.start();
      clip.setSampleRate(0);
      sound.ch[i] = clip;
    }
    for (int i = 0; i < mod.samplesSize; i++) {
      sound.loadInstrument(i, mod.getSampleStart(i), mod.getSampleEnd(i));
    }

    int bpmSpeed = 6;
    int bpmTempo = 125;

    sound.putWav(sound.getWav());
    Instant now = Instant.now();
    for (AmigaMod.Sequencer sequencer = mod.newSequencer(); sequencer.getLoop() == 0; sequencer.inc()) {
      try {
        System.out.print(String.format("\r  %02d/%02d", sequencer.getOrder(), sequencer.getRow()));
        AmigaMod.Note[] notes = sequencer.getNotes();
        for (int c = 0; c < 4; c++) {
          AmigaMod.Note note = notes[c];
          System.out.print(" | " + note);
          sound.noteOffOn(c, note.getSample(), note.getMidiNote(), true);
          switch (note.getFxCommand()) {
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
        //Thread.sleep(15_000 / bpm);
        for (int c = 0; c < 4; c++) {
          AmigaMod.Note note = notes[c];
          sound.noteOffOn(c, note.getSample(), note.getMidiNote(), false);
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
