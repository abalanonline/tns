package ab;

import com.sun.media.sound.SF2Instrument;
import com.sun.media.sound.SF2InstrumentRegion;
import com.sun.media.sound.SF2Layer;
import com.sun.media.sound.SF2LayerRegion;
import com.sun.media.sound.SF2Region;
import com.sun.media.sound.SF2Sample;
import com.sun.media.sound.SF2Soundbank;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Patch;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.sampled.AudioFormat;
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

  public static SF2Sample createSample(SF2Soundbank sf2, AmigaMod mod, int modSample) {
    int sampleSize = mod.getSampleSize(modSample);
    byte[] modBytes = mod.bytes.array();
    byte[] insBytes = new byte[sampleSize * 2];
    for (int i = 0, i0 = mod.getSampleStart(modSample), i1 = 1; i < sampleSize; i++, i0++, i1 += 2) {
      insBytes[i1] = modBytes[i0];
    }

    SF2Sample sample = new SF2Sample(sf2);
    sample.setName(mod.getSampleName(modSample));
    sample.setData(insBytes);
    if (mod.isLoop(modSample)) {
      int loopPoint = mod.getLoopStart(modSample);
      sample.setStartLoop(loopPoint);
      loopPoint += mod.getLoopLength(modSample);
      sample.setEndLoop(loopPoint);
    }
    sample.setSampleRate(8363);
    sample.setSampleType(1);
    sample.setOriginalPitch(60);
    sf2.addResource(sample);

    return sample;
  }

  public static SF2Layer createLayer(SF2Soundbank sf2, AmigaMod mod, int modSample) {
    SF2LayerRegion region = new SF2LayerRegion();
    region.setSample(createSample(sf2, mod, modSample));
    if (mod.isLoop(modSample)) region.putInteger(SF2Region.GENERATOR_SAMPLEMODES, 3);

    SF2Layer layer = new SF2Layer(sf2);
    layer.setName(mod.getSampleName(modSample));
    layer.getRegions().add(region);
    sf2.addResource(layer);
    return layer;
  }

  public static SF2Soundbank createSoundbank(AmigaMod mod) {
    SF2Soundbank sf2 = new SF2Soundbank();
    sf2.setName(mod.getSongName());
    for (int i = 1; i < mod.samplesSize; i++) {
      SF2Instrument instrument = new SF2Instrument(sf2);
      instrument.setPatch(new Patch(0, i));
      instrument.setName(mod.getSampleName(i));
      sf2.addInstrument(instrument);
      SF2InstrumentRegion instrumentRegion = new SF2InstrumentRegion();
      instrumentRegion.setLayer(createLayer(sf2, mod, i));
      instrument.getRegions().add(instrumentRegion);
    }
    return sf2;
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
      sound.loadInstrument(i, mod.getSampleStart(i), mod.getSampleStart(i) + mod.getSampleSize(i));
    }

    try {
      MidiDevice midiDevice = MidiSystem.getMidiDevice(MidiSystem.getMidiDeviceInfo()[0]);
      midiDevice.open();
      sound.setMidiReceiver(midiDevice.getReceiver());
      Synthesizer synthesizer = (Synthesizer) midiDevice;

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
      synthesizer.loadAllInstruments(MidiSystem.getSoundbank(new ByteArrayInputStream(soundFont.toByteArray())));

    } catch (MidiUnavailableException | InvalidMidiDataException | IOException ignore) {
    }

    int bpmSpeed = 6;
    int bpmTempo = 125;

    sound.putWav(sound.getWav());
    Instant now = Instant.now();
    int[] chSample = new int[0x20];
    int[] chNote = new int[0x20];
    for (AmigaMod.Sequencer sequencer = mod.newSequencer(); sequencer.getLoop() == 0; sequencer.inc()) {
      try {
        System.out.print(String.format("\r  %02d/%02d", sequencer.getOrder(), sequencer.getRow()));
        AmigaMod.Note[] notes = sequencer.getNotes();
        for (int c = 0; c < 4; c++) {
          AmigaMod.Note note = notes[c];
          System.out.print(" | " + note);
          if (note.isNoteOn()) {
            sound.noteOffOn(c, chSample[c], chNote[c], false);
            chSample[c] = note.getSample();
            chNote[c] = note.getMidiNote();
            sound.noteOffOn(c, chSample[c], chNote[c], true);
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
