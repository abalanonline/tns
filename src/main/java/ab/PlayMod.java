package ab;

import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public class PlayMod {

  public static void playAmigaMod(String modFile, boolean useSunSequencer, boolean useSunSynthesizer) {
    TnsSound sound = new TnsSound();
    try {
      AmigaMod mod = new AmigaMod(Files.newInputStream(Paths.get(modFile)));
      Sequence sequence = MidiSystem.getSequence(mod.toMidi());
      Soundbank soundbank = MidiSystem.getSoundbank(mod.toSoundbank());

      MidiDevice midiDevice = MidiSystem.getMidiDevice(MidiSystem.getMidiDeviceInfo()[0]);
      midiDevice.open();
      Receiver midiReceiver = midiDevice.getReceiver();

      if (useSunSynthesizer) {
        Synthesizer synthesizer = MidiSystem.getSynthesizer();
        synthesizer.open();
        synthesizer.loadAllInstruments(soundbank);
        midiReceiver = synthesizer.getReceiver();
      } else {
        // TNS synthesizer
        sound.loadAllInstruments(mod.toSoundFont());
        midiReceiver = sound;
      }

      MetaEventListener metaEventListener = metaMessage -> {
        if (metaMessage.getType() == 1) {
          System.out.println(new String(metaMessage.getData()));
          sound.putWav(sound.getWav()); // FIXME: 2022-09-19 poor design
        }
      };

      if (useSunSequencer) {
        Sequencer sequencer = MidiSystem.getSequencer(false);
        sequencer.getTransmitter().setReceiver(midiReceiver);
        sequencer.addMetaEventListener(metaEventListener);

        sequencer.open();
        sequencer.setSequence(sequence);
        sequencer.start();
      } else {
        // TNS sequencer
        final Receiver finalMidiReceiver = midiReceiver;
        AtomicLong milliseconds = new AtomicLong(120);
        mod.getSequencer(midiMessage -> {
          if (midiMessage instanceof MetaMessage) {
            MetaMessage metaMessage = (MetaMessage) midiMessage;
            metaEventListener.meta(metaMessage);
            if (metaMessage.getType() == 0x51) {
              milliseconds.set(new BigInteger(metaMessage.getData()).longValue() / 4000);
            }
            if (metaMessage.getType() == 1) {
              try {
                Thread.sleep(milliseconds.get());
              } catch (InterruptedException ignore) {
              }
            }
          } else finalMidiReceiver.send(midiMessage, -1);
        }).start();
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static void playAmigaModMidi(String modFile, int[] midiInstrumentMap) {
    TnsSound sound = new TnsSound();
    try {
      AmigaMod mod = new AmigaMod(Files.newInputStream(Paths.get(modFile)), midiInstrumentMap);
      Sequence sequence = MidiSystem.getSequence(mod.toMidi());

      MidiDevice midiDevice = MidiSystem.getMidiDevice(MidiSystem.getMidiDeviceInfo()[0]);
      midiDevice.open();
      Receiver midiReceiver = midiDevice.getReceiver();

      MetaEventListener metaEventListener = metaMessage -> {
        if (metaMessage.getType() == 1) {
          System.out.println(new String(metaMessage.getData()));
        }
      };

      Sequencer sequencer = MidiSystem.getSequencer(false);
      sequencer.getTransmitter().setReceiver(midiReceiver);
      sequencer.addMetaEventListener(metaEventListener);

      sequencer.open();
      sequencer.setSequence(sequence);
      sequencer.start();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static void main( String[] args ) {
    switch (args.length) {
      case 1:
        playAmigaMod(args[0], true, true);
        break;
      case 2:
        int[] midiInstrumentMap = Arrays.stream(args[1].split(",")).mapToInt(Integer::valueOf).toArray();
        playAmigaModMidi(args[0], midiInstrumentMap);
        break;
      default: throw new IllegalStateException();
    }
  }

}
