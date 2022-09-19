package ab;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
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
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

public class App {

  public static void testMidi() {
    try {
      AmigaMod mod = new AmigaMod(Files.newInputStream(Paths.get("test.mod")));
      byte[] midiBytes = mod.toMidi();
      Files.write(Paths.get("target", "mod.mid"), midiBytes);
      Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(midiBytes));
      Synthesizer synthesizer = MidiSystem.getSynthesizer();
      synthesizer.open();

      Sequencer sequencer = MidiSystem.getSequencer(false);
      sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
      sequencer.addMetaEventListener(meta -> {
        if (meta.getType() == 1) System.out.println(new String(meta.getData()));
      });

      sequencer.open();
      sequencer.setSequence(sequence);
      sequencer.start();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static void main( String[] args ) {
    testMidi();
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
      sound.setMidiReceiver(midiDevice.getReceiver());

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

    sound.putWav(sound.getWav());
    AtomicLong milliseconds = new AtomicLong(300);
    mod.getSequencer(midiMessage -> {
      if (midiMessage instanceof MetaMessage) {
        MetaMessage message = (MetaMessage) midiMessage;
        if (message.getType() == 0x51) {
          milliseconds.set(new BigInteger(message.getData()).longValue() / 4000);
        }
        if (message.getType() == 1) {
          System.out.println(new String(message.getData()));
          try {
            Thread.sleep(milliseconds.get());
          } catch (InterruptedException ignore) {
          }
        }
      } else sound.midiReceiver.send(midiMessage, -1);
    }).start();
  }

}
