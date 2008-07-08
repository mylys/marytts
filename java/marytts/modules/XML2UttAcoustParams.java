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

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import marytts.datatypes.MaryDataType;
import marytts.modules.synthesis.FreeTTSVoices;
import marytts.modules.synthesis.Voice;

import org.w3c.dom.Element;

import com.sun.speech.freetts.Item;
import com.sun.speech.freetts.Relation;
import com.sun.speech.freetts.Utterance;



/**
 * Convert a MaryXML DOM tree into FreeTTS utterances
 * (durations, English).
 *
 * @author Marc Schr&ouml;der
 */

public class XML2UttAcoustParams extends XML2UttBase
{
    public XML2UttAcoustParams()
    {
        super("XML2Utt AcoustParams",
              MaryDataType.ACOUSTPARAMS,
              MaryDataType.FREETTS_ACOUSTPARAMS,
              null);
    }

    public void powerOnSelfTest() throws Error
    {
    }
    
    public Utterance convert(List<Element> tokensAndBoundaries, Voice maryVoice)
    {
        com.sun.speech.freetts.Voice freettsVoice =
            FreeTTSVoices.getFreeTTSVoice(maryVoice);
        if (freettsVoice == null) {
            throw new NullPointerException("No FreeTTS voice for mary voice " + maryVoice.getName());
        }
        Utterance utterance = new Utterance(freettsVoice);
        utterance.createRelation(Relation.TOKEN);
        utterance.createRelation(Relation.WORD);
        utterance.createRelation(Relation.SYLLABLE_STRUCTURE);
        utterance.createRelation(Relation.SYLLABLE);
        utterance.createRelation(Relation.SEGMENT);
        utterance.createRelation(Relation.TARGET);
        utterance.createRelation(Relation.PHRASE);
        for (Element el : tokensAndBoundaries) {
            addOneElement(utterance, el,
                          true, // create word relation
                          true, // create sylstruct relation
                          true); // create target relation
        }
        // Check if default name needs to be added to last phrase item:
        Item phraseItem = utterance.getRelation(Relation.PHRASE).getTail(); 
        if (phraseItem != null && !phraseItem.getFeatures().isPresent("name")) {
            phraseItem.getFeatures().setString("name", "BB");
        }
        // Append a last target at the very end of the utterance, because
        // the FreeTTS diphone code only creates audio up to the last target:
        Relation segmentRelation = utterance.getRelation(Relation.SEGMENT);
        Relation targetRelation = utterance.getRelation(Relation.TARGET);
        assert segmentRelation != null;
        assert targetRelation != null;
        Item lastSegment = segmentRelation.getTail();
        if (lastSegment != null && lastSegment.getFeatures().isPresent("end")) {
            float pos = lastSegment.getFeatures().getFloat("end");
            float f0;
            Item lastTarget = targetRelation.getTail();
            if (lastTarget != null) f0 = lastTarget.getFeatures().getFloat("f0");
            else f0 = 100;
            Item finalTarget = targetRelation.appendItem();
            finalTarget.getFeatures().setFloat("pos", pos);
            finalTarget.getFeatures().setFloat("f0", f0);
        }
        return utterance;
    }


    /**
     * Depending on the data type, find the right information in the sentence
     * and insert it into the utterance.
     */
    protected void fillUtterance(Utterance utterance, Element sentence)
    {
        fillUtterance(utterance, sentence,
                      true, // create word relation
                      true, // create sylstruct relation
                      true); // create target relation
    }


}
