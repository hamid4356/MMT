package eu.modernmt.processing.chinese;

import eu.modernmt.lang.Language;
import eu.modernmt.lang.UnsupportedLanguageException;
import eu.modernmt.processing.ProcessingException;
import eu.modernmt.processing.TextProcessor;
import eu.modernmt.processing.string.SentenceBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Created by nicola on 06/02/18
 * <p>
 * A ChinesePreprocessor has the responsibility
 * to transform any Chinese variants (Simplified, Tranditional
 * (including Taiwan and Hong Kong standard) into Simplified Chinese
 */
public class ChinesePreprocessor extends TextProcessor<SentenceBuilder, SentenceBuilder> {

    private static final ChineseDetector detector = new ChineseDetector();
    private static final Map<String, ChineseConverter> converters = new HashMap<>();
    private static Language sourceLanguage, internalLanguage;

    /**
     * This constructor builds a ChinesePreprocessor
     * based on the initial language and the destination language of the translation process.
     *
     * @param sourceLanguage the language of the input String
     * @param targetLanguage the language the input String must be translated to
     * @throws UnsupportedLanguageException the requested language is not supported by this software
     */
    public ChinesePreprocessor(Language sourceLanguage, Language targetLanguage) throws UnsupportedLanguageException {
        super(sourceLanguage, targetLanguage);
        this.sourceLanguage = sourceLanguage;
        internalLanguage = new Language("zh", null, "TW"); //by default, we use Traditional Chinese (zh-TW) internally

        if (!detector.support(sourceLanguage)) {
            throw new UnsupportedLanguageException(sourceLanguage);
        }
    }

    /**
     * Method that, given a SentenceBuilder with the string to process,
     * extracts the string, scans it looking for whitespace characters sequences
     * and requests either their deletion,
     * if they are at the very beginning or at the very end end of the string
     * or their their replacement with a blank space (" ") in any other case.
     *
     * @param inputText a SentenceBuilder that holds the input String
     *                  and can generate Editors to process it
     * @param metadata  additional information on the current pipe
     *                  (not used in this specific operation)
     * @return the SentenceBuilder received as a parameter;
     * its internal state has been updated by the execution of the call() method
     * @throws ProcessingException
     */
    @Override
    public SentenceBuilder call(SentenceBuilder inputText, Map<String, Object> metadata) throws ProcessingException {
//        Language srcLanguage = null;
//        if (sourceLanguage.getRegion()== null){
//            srcLanguage = detector.detectLanguage(inputText.toString());
//        } else {
//            srcLanguage = sourceLanguage;
//        }
//        if (srcLanguage.getRegion().equals(internalLanguage.getRegion())) {
//            return inputText;
//        } else {
//            String conversion = srcLanguage.getRegion() + "-" + internalLanguage.getRegion();
//            if (!converters.containsKey(conversion)) {
//                try {
//                    converters.put(conversion, new ChineseConverter(srcLanguage, internalLanguage));
//                } catch (IOException e) {
//                    throw new Error(e);
//                }
//            }
//            ChineseConverter converter = converters.get(conversion);
//            String convertedText = converter.convert(inputText.toString());
//            return new SentenceBuilder(convertedText);
//        }
        return null;
    }

    public static void main(String[] args) throws ProcessingException {
        Language src = new Language("zh", "CN");
        Language trg = new Language("en");
        Map<String, Object> metadata = new HashMap<>();

        long startTime = System.nanoTime();
        ChinesePreprocessor normalizer = new ChinesePreprocessor(src, trg);
        long l = (System.nanoTime() - startTime) / 1000000;
        System.out.println("start up of normalizer; duration l: " + l + " milliseconds");


        Scanner in = new Scanner(System.in);

        String from, to;
        int N = 0;
        while (in.hasNext()) {
            from = in.next();
            SentenceBuilder builder = new SentenceBuilder(from);
            to = normalizer.call(builder, metadata).toString();
            System.out.println(to);
            N++;
        }
        l = (System.nanoTime() - startTime) / 1000000;
        System.out.println("duration l: " + l + " milliseconds speed: " + ((float) l) / N + " milliseconds/sentence");
    }
}