/**
 * Copyright 2000-2006 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * Permission is hereby granted, free of charge, to use and distribute
 * this software and its documentation without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of this work, and to
 * permit persons to whom this work is furnished to do so, subject to
 * the following conditions:
 * 
 * 1. The code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 * 2. Any modifications must be clearly marked as such.
 * 3. Original authors' names are not deleted.
 * 4. The authors' names are not used to endorse or promote products
 *    derived from this software without specific prior written
 *    permission.
 *
 * DFKI GMBH AND THE CONTRIBUTORS TO THIS WORK DISCLAIM ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL DFKI GMBH NOR THE
 * CONTRIBUTORS BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 */
package marytts.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;

import marytts.datatypes.MaryDataType;
import marytts.modules.synthesis.Voice;
import marytts.server.MaryProperties;
import marytts.util.audio.AudioDestination;
import marytts.util.audio.AudioReader;

import org.apache.log4j.Logger;


/**
 * A link to the synthesis part of festival.
 *
 * @author Marc Schr&ouml;der
 */

public class FestivalCaller extends SynthesisCallerBase
{
    protected File festivalDir;
    protected File relationsDir;
    protected File segmentDir;
    protected File syllableDir;
    protected File wordDir;
    protected File intEventDir;
    protected File phraseDir;
    protected File targetDir;
    protected File uttsDir;
    private static int timeout;
    
    public FestivalCaller()
    {
        super("FestivalCaller",
              MaryDataType.FESTIVAL_UTT,
              MaryDataType.AUDIO
              );
        timeout = MaryProperties.needInteger("modules.timeout");

    }

    public synchronized void startup() throws Exception {
        // Make sure the festival directory structure exists under
        // mary.base/tmp:
        festivalDir = new File(MaryProperties.getFilename("festival.tmp.dir",
                                System.getProperty("mary.base") +
                                File.separator + "tmp" +
                                File.separator + "festival"));
        relationsDir = new File(festivalDir.getPath() + File.separator + "relations");
        segmentDir = new File(relationsDir.getPath() + File.separator + "Segment");
        syllableDir = new File(relationsDir.getPath() + File.separator + "Syllable");
        wordDir = new File(relationsDir.getPath() + File.separator + "Word");
        intEventDir = new File(relationsDir.getPath() + File.separator + "IntEvent");
        phraseDir = new File(relationsDir.getPath() + File.separator + "Phrase");
        targetDir = new File(relationsDir.getPath() + File.separator + "Target");
        uttsDir = new File(festivalDir.getPath() + File.separator + "utts");
        makeSureIsDirectory(festivalDir);
        makeSureIsDirectory(relationsDir);
        makeSureIsDirectory(segmentDir);
        makeSureIsDirectory(syllableDir);
        makeSureIsDirectory(wordDir);
        makeSureIsDirectory(intEventDir);
        makeSureIsDirectory(phraseDir);
        makeSureIsDirectory(targetDir);
        makeSureIsDirectory(uttsDir);
        super.startup();
    }

    private void makeSureIsDirectory(File dir) throws IOException {
        if (!dir.isDirectory()) {
            boolean success;
            if (dir.exists()) { // exists, but is not a directory
                success = dir.delete();
                if (!success) {
                    throw new IOException("Need to create directory " +
                                        dir.getPath() +
                                        ", but file exists that cannot be deleted.");
                }
            }
            success = dir.mkdir();
            if (!success) {
                throw new IOException("Cannot create directory " + dir.getPath());
            }
        }
    }

    /**
     * Process a single utterance in FESTIVAL_UTT text format.
     * @param festivalUtt
     * @return the synthesized audio data
     * @throws IOException
     * @throws UnsupportedAudioFileException
     */
    public AudioInputStream synthesiseOneSection(String festivalUtt, Voice voice)
    throws IOException
    {
        writeRelationFiles(festivalUtt);
        File audioFile = new File(festivalDir.getPath() + File.separator + "mary.wav");
        String festivalVoiceCmd = "(voice_" + voice.getName() + ")";
        AudioInputStream sound = festivalSynthesise(audioFile, festivalVoiceCmd);
        return sound;
    }

    private void writeRelationFiles(String festivalUtt)
    throws IOException
    {
        BufferedReader buf = new BufferedReader(new StringReader(festivalUtt));
        String line;
        String relation = null;
        PrintWriter pw = null;
        while ((line = buf.readLine()) != null) {
            if (line.startsWith("==") && !line.startsWith("===")) {
                relation = line.substring(2, line.indexOf('=', 2));
                logger.debug("Writing " + relation + " relation:");
                if (pw != null) {
                    pw.close();                
                }
                pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(
                    relationsDir.getPath() + File.separator + relation + File.separator + "mary." + relation),
                    "ISO-8859-15")); 
            } else if (pw != null) {
                pw.println(line);
                logger.debug(line);
            }
        }
        if (pw != null) pw.close();
    }


    private AudioInputStream festivalSynthesise(File audioFile, String festVoiceCmd)
    throws IOException
    {
        String festvoxDirPath = MaryProperties.maryBase() + File.separator + "lib" + File.separator + "festvox" + File.separator;
        String festCmd = "(begin (set! argv nil) "
            + "(load \"" + festvoxDirPath.replaceAll("\\\\", "/") + "make_utts\") "
            + festVoiceCmd + " "
            + "(set! label_dir \"" + festivalDir.getAbsolutePath().replaceAll("\\\\", "/") + "/relations\")"
            + "(set! utt_dir \"" + uttsDir.getAbsolutePath().replaceAll("\\\\", "/") + "\") "
            + "(set! utt1 (make_utt \"mary\" basic_relations)) "
            + "(utt.save utt1 \"" + uttsDir.getAbsolutePath().replaceAll("\\\\", "/") + "/mary.utt\") "
            + "(Wave_Synth utt1) "
            + "(utt.send.wave.client utt1) "
//            + "(utt.save.wave utt1 \"" + festivalDir.getAbsolutePath().replaceAll("\\\\", "/") + "/mary.wav\" 'riff) "
            + ")";
        
/*
        String[] cmdArray = new String[3];
        cmdArray[0] = MaryProperties.getFilename("festival.bin");
        cmdArray[1] = "--pipe";
        cmdArray[2] = festvoxDirPath + "mary_phones.scm";
        logger.info("Starting Festival with command: " + cmdArray[0] + " " +
                     cmdArray[1] + " " + cmdArray[2]);
        try {
            Process process = Runtime.getRuntime().exec(cmdArray);
            PrintWriter toFestival = new PrintWriter(process.getOutputStream(), true); // autoflush
            toFestival.println(festCmd);
            toFestival.close();
            System.out.println("Festival error:");
            while (process.getErrorStream().available() > 0) {
                System.out.write(process.getErrorStream().read());
            }
            process.waitFor(); // wait until Festival has done it's job or timeout
            System.out.println("Festival terminated.");
            System.out.println("Festival error:");
            while (process.getErrorStream().available() > 0) {
                System.out.write(process.getErrorStream().read());
            }
        } catch (InterruptedException e) {}
*/
        	// Connect to Festival server:
        	return new SimpleFestivalClient().process(festCmd);
        }

    public static class SimpleFestivalClient
    {
        private Socket socket;
        private Logger logger;
        
        public SimpleFestivalClient()
        throws UnknownHostException, IOException
        {
            this("localhost", 1314);
        }
        
        public SimpleFestivalClient(String host, int port)
        throws UnknownHostException, IOException
        {
            this.socket = new Socket(host, port);
            logger = Logger.getLogger("SimpleFestivalClient");
        }
        
        public AudioInputStream process(String request)
        throws IOException
        {
            PrintStream toFestival = new PrintStream(socket.getOutputStream());
            toFestival.println("(Parameter.set 'Wavefiletype 'riff)\n");
            logger.debug("Sending request to Festival:\n" + request);
            toFestival.println(request);
            toFestival.flush();
            AudioDestination audioDestination = new AudioDestination();
            int c = -1;
            InputStream fromFestival = socket.getInputStream();
            logger.debug("Trying to read from Festival...");
            while ((c = fromFestival.read()) != -1) {
                logger.debug("Read: " + (char) c);
                if (c == 'W') {
                    c = fromFestival.read();
                    if (c == 'V') {
                        c = fromFestival.read();
                        if (c == '\n') {
                            // OK, now we read Audio data until the end
                            // marker is read.
                            AudioReader readingThread = new AudioReader(fromFestival, audioDestination, "ft_StUfF_key");
                            readingThread.start();
                            boolean timeoutOccurred = false;
                            do {
                                try {
                                    readingThread.join(timeout);
                                } catch (InterruptedException e) {
                                    logger.warn("Unexpected interruption while waiting for reader thread.");
                                }
                                timeoutOccurred = System.currentTimeMillis() - readingThread.latestSeenTime() >= timeout;
                            } while (readingThread.isAlive() && !timeoutOccurred);
                            if (!timeoutOccurred) {
                                try {
                                    return audioDestination.convertToAudioInputStream();
                                } catch (UnsupportedAudioFileException e) {
                                    logger.warn("Cannot interpret audio data", e);
                                    return null;
                                }
                            }
                            logger.warn("Timeout occurred");

                        }
                    }
                }
            }
            return null;
        }
    }

}
