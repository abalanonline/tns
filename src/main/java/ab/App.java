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
      sound.ch[i] = clip;
    }

    for (AmigaMod.Sequencer sequencer = mod.newSequencer(); sequencer.getLoop() == 0; sequencer.inc()) {
      try {
        System.out.print(String.format("\r%02X/%02X", sequencer.getOrder(), sequencer.getRow()));
        AmigaMod.Note[] notes = sequencer.getNotes();
        for (int c = 0; c < 4; c++) {
          AmigaMod.Note note = notes[c];
          if (note.isNoteEmpty()) continue;
          sound.ch[c].setSampleRate(note.getSampleRate());
          sound.ch[c].setFramePosition(0x43C + mod.patternSize * 0x400); // FIXME: 2022-08-15 select sample
        }
        sound.putWav(sound.getWav());
        Thread.sleep(120);
      } catch (InterruptedException e) {
        break;
      }
    }
  }

}
