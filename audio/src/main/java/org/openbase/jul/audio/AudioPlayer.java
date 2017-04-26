package org.openbase.jul.audio;

/*-
 * #%L
 * JUL Audio
 * %%
 * Copyright (C) 2015 - 2017 openbase.org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineEvent.Type;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 */
public class AudioPlayer {

    public static final int BUFSIZE = 512;
    private final ExecutorService executorService;

    private static final Logger logger = LoggerFactory.getLogger(AudioPlayer.class);

    public AudioPlayer(final int soundChannels) {
        this.executorService = Executors.newFixedThreadPool(soundChannels);
    }

    public AudioPlayer() {
        this(10);
    }

    public boolean playAudio(final AudioSource source) {
        return playAudio(source, false);
    }

    public boolean playAudio(final AudioSource source, final boolean wait) {
        try {
            if (wait) {
                play(source);
                return true;
            }
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        play(source);
                    } catch (IOException | UnsupportedAudioFileException | LineUnavailableException | InterruptedException ex) {
                        ExceptionPrinter.printHistory(new CouldNotPerformException("Could not play clip!", ex), logger, LogLevel.WARN);
                    }
                }
            });
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException | InterruptedException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Could not play clip!", ex), logger, LogLevel.WARN);
            return false;
        }
        return true;
    }

    private static void play(final AudioSource source) throws IOException, UnsupportedAudioFileException, LineUnavailableException, InterruptedException {
        if (source instanceof AudioData) {
            play((AudioData) source);
        } else if (source instanceof AudioFileHolder) {
            play(((AudioFileHolder) source).getFile());
        } else {
            logger.warn("Unkown audio source! Skip clip...");
            assert false;
        }
    }

    private static void play(final AudioData audioData) throws IOException, UnsupportedAudioFileException, LineUnavailableException, InterruptedException {

        AudioInputStream audioInputStream = new AudioInputStream(new ByteArrayInputStream(audioData.getData()), audioData.getFormat(), audioData.getDataLenght());
//		
        play(audioInputStream);
//		play(AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioData.getData())));

//		SourceDataLine line = null;
//		DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
//
//		line = (SourceDataLine) AudioSystem.getLine(info);
    }

    private static void play(final File clipFile) throws IOException, UnsupportedAudioFileException, LineUnavailableException, InterruptedException {
        play(AudioSystem.getAudioInputStream(clipFile));
    }

    private static void play(final AudioInputStream audioInputStream) throws IOException, UnsupportedAudioFileException, LineUnavailableException, InterruptedException {
        class AudioListener implements LineListener {

            private boolean done = false;

            @Override
            public synchronized void update(final LineEvent event) {
                final Type eventType = event.getType();
                if (eventType == Type.STOP || eventType == Type.CLOSE) {
                    done = true;
                    notifyAll();
                }
            }

            public synchronized void waitUntilDone() throws InterruptedException {
                while (!done) {
                    wait();
                }
            }
        }
        final AudioListener listener = new AudioListener();
        try {
            final Clip clip = AudioSystem.getClip();
            clip.addLineListener(listener);
            clip.open(audioInputStream);
            try {
                clip.start();
                listener.waitUntilDone();
            } finally {
                clip.close();
            }
        } finally {
            audioInputStream.close();
        }
    }
}
