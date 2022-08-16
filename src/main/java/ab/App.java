package ab;

import javax.sound.midi.Instrument;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

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

    Synthesizer synthesizer;
    try {
      synthesizer = MidiSystem.getSynthesizer();
      synthesizer.open();
    } catch (MidiUnavailableException e) {
      throw new IllegalStateException(e);
    }
    Instrument[] instruments = synthesizer.getDefaultSoundbank().getInstruments();
    MidiChannel[] midi = synthesizer.getChannels();
    int piano = -1 + 2; // 2 - bright piano
    synthesizer.loadInstrument(instruments[piano]);

    for (AmigaMod.Sequencer sequencer = mod.newSequencer(); sequencer.getLoop() == 0; sequencer.inc()) {
      try {
        System.out.print(String.format("\r%02X/%02X", sequencer.getOrder(), sequencer.getRow()));
        AmigaMod.Note[] notes = sequencer.getNotes();
        for (int c = 0; c < 4; c++) {
          AmigaMod.Note note = notes[c];
          System.out.print(" | " + note);
          if (note.isNoteEmpty() || note.getSample() == 0) continue;
          sound.ch[c].setSampleRate(note.getSampleRate());
          sound.ch[c].setFramePosition(mod.getSampleStart(note.getSample()));
          sound.ch[c].setLoopPoints(mod.getSampleEnd(note.getSample()), mod.getSampleEnd(note.getSample()));
//          midi[0].programChange(piano);
//          midi[0].noteOn(note.getMidiNote(), 0x60);
        }
        System.out.println();
        sound.putWav(sound.getWav());
        Thread.sleep(120);
        Arrays.stream(notes).filter(AmigaMod.Note::isNoteEmpty)
            .map(AmigaMod.Note::getMidiNote).forEach(n -> midi[0].noteOff(n));
      } catch (InterruptedException e) {
        break;
      }
    }
  }

}
