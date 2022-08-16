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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class TyphoonSound implements AutoCloseable {

  public static final AudioFormat AUDIO_CD = new AudioFormat(44_100, 16, 2, true, false);
  public static final int CHANNELS = 0x40;

  private final AudioFormat audioFormat;
  private final SourceDataLine line;
  public final TsClip[] ch = new TsClip[CHANNELS];

  public TyphoonSound() {
    audioFormat = AUDIO_CD;
    try {
      line = AudioSystem.getSourceDataLine(audioFormat);
      line.open(audioFormat);
    } catch (LineUnavailableException e) {
      throw new IllegalStateException(e);
    }
    line.start();
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
      if (clip == null) continue;
      int d0 = (int) (clip.getSampleRate());
      int d1 = (int) (audioFormat.getSampleRate());
      for (int i = 0; i < wav.length; i++) {
        clip.r += d0;
        clip.framePosition += clip.r / d1;
        clip.r %= d1;
        if (clip.framePosition >= clip.loopEnd) {
          clip.framePosition = clip.loopStart;
        } else {
          wav[i] += clip.data[clip.framePosition] << 6;
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

  public static class TsClip extends DummyClip {
    //private AudioFormat format;
    byte[] data;
    private float sampleRate;
    private int framePosition;
    private int loopStart;
    private int loopEnd;
    int r;

    public float getSampleRate() {
      return sampleRate;
    }

    public void setSampleRate(float sampleRate) {
      this.sampleRate = sampleRate;
    }

    @Override
    public void open(AudioFormat format, byte[] data, int offset, int bufferSize) {
      //this.format = format;
      this.data = new byte[bufferSize];
      this.sampleRate = format.getSampleRate();
      System.arraycopy(data, offset, this.data, 0, bufferSize);
    }

    @Override
    public void setFramePosition(int framePosition) {
      this.framePosition = framePosition;
    }

    @Override
    public void setLoopPoints(int start, int end) {
      loopStart = start;
      loopEnd = end;
    }

  }

}
