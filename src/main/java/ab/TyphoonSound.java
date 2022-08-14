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

  private final AudioFormat audioFormat;
  private final SourceDataLine line;

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

}
