/*
 * Uses Tika to detect the MIME type of documents
 */

package net.internetmemory.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Class that encapsulates TIKA based mime type detection.
 * 
 * Deprecated: now replaced by the extractor (?)
 */
@Deprecated
public class MimeDetection {
	static Set<String> qualifyingMimes = new HashSet<String>();

	private final AutoDetectParser parser = new AutoDetectParser();
	private final Metadata metadata = new Metadata();
	private final DefaultHandler defHandler = new DefaultHandler();
	private final ParseContext parConte = new ParseContext();

	private final int timeout = 10;

	// private ExecutorService executor = Executors.newFixedThreadPool(2);
	private ExecutorService executor = Executors.newSingleThreadExecutor();

	public MimeDetection() {

		// Philippe: does not compile?
		// parser.setParsers(new HashMap<MediaType, Parser>());

		qualifyingMimes.add("pdf");

		qualifyingMimes.add("word");
		qualifyingMimes.add("powerpoint");
		qualifyingMimes.add("office");
		qualifyingMimes.add("excel");

		qualifyingMimes.add("html");
		qualifyingMimes.add("sgml");
		qualifyingMimes.add("xml");
		qualifyingMimes.add("text");
		qualifyingMimes.add("rtf");
	}

	public static boolean isQualifyingStatusCode(String statusCode) {
		int sc = 200;
		try {
			sc = Integer.parseInt(statusCode);
		} catch (NumberFormatException ex) {

		}

		if (sc >= 200 && sc < 300) {
			return true;
		}
		return false;
	}

	/**
	 * Detect mime type given a raw byte content
	 * 
	 * @param content
	 * @return detected mime type
	 */
	public String detectMimeType(byte[] content) {
		InputStream is = new ByteArrayInputStream(content);
		// return detectMimeType(is);
		return detectMimeTypeTimeout(is, timeout);
	}

	public String detectMimeType(InputStream is) {
		String detectedMimeType = null;
		try {
			parser.parse(is, defHandler, metadata, parConte);
			detectedMimeType = metadata.get(HttpHeaders.CONTENT_TYPE);
			// just trying to put at first DefaultHanlder and ParserContext
		} catch (IOException ex) {
			Logger.getLogger(getClass().getName()).log(Level.WARNING,
					ex.getMessage(), ex);
		} catch (SAXException ex) {
			Logger.getLogger(getClass().getName()).log(Level.WARNING,
					ex.getMessage(), ex);
		} catch (TikaException ex) {
			Logger.getLogger(getClass().getName()).log(Level.WARNING,
					ex.getMessage(), ex);
		} finally {
			try {
				is.close();
			} catch (IOException ex) {
				Logger.getLogger(getClass().getName()).log(Level.SEVERE, null,
						ex);
			}
		}

		return detectedMimeType;
	}

	/**
	 * Detect the mime type with time out. It will call parser in a separate
	 * thread, if the timeout is met, it will return control to the task thread
	 * and tries to cancel the parser thread. The canceling might not be
	 * successful and the thread might hang. Hopefully, the task thread will
	 * give the job thread a notice that it finished successfully, however the
	 * task thread will hang at the end too.
	 * 
	 * In the moment, there is no safe (or other way) way (at least not known to
	 * the author) how to safely kill the hang thread from within java. If the
	 * thread does not respond to interrupt() call, then its screwed.
	 * 
	 * @param is
	 *            Input stream on which perform the test
	 * @param timeout
	 *            timeout in seconds
	 * @return detected mime
	 */
	public String detectMimeTypeTimeout(final InputStream is, int timeout) {
		String detectedMime = null;

		Callable<String> parseCallable = new Callable<String>() {
			@Override
			public String call() throws Exception {
				// parser.parse(is, defHandler, metadata, parConte);
				// return metadata.get(HttpHeaders.CONTENT_TYPE);

				String detectMimeType = null;
				detectMimeType = detectMimeType(is);
				return detectMimeType;
			}

		};

		Future<String> future = executor.submit(parseCallable);

		try {
			detectedMime = future.get(timeout, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			Logger.getLogger(MimeDetection.class.getName()).log(Level.WARNING,
					null, ex);
		} catch (ExecutionException ex) {
			Logger.getLogger(MimeDetection.class.getName()).log(Level.WARNING,
					null, ex);
		} catch (TimeoutException ex) {
			future.cancel(true);
			try {
				is.close();
			} catch (IOException ex1) {
				Logger.getLogger(getClass().getName()).log(Level.SEVERE, null,
						ex1);
			}
			List<Runnable> shutdownNow = executor.shutdownNow();
			System.out.println("shutdownNow: " + shutdownNow);

			executor = Executors.newSingleThreadExecutor();

			Logger.getLogger(MimeDetection.class.getName()).log(Level.WARNING,
					"Time out while detecting mime.", ex);
		}
		return detectedMime;
	}

	/**
	 * Decide whether a mime type is within a set of predefined (full-text
	 * indexable) mime types
	 * 
	 * @param mime
	 * @return true if mime type qualifies for full text indexing
	 */
	public boolean isMimeQualifying(String mime) {

		for (String qualifyingMime : qualifyingMimes) {
			if (mime.toLowerCase().contains(qualifyingMime))
				return true;
		}

		return false;
	}

	public void destroy() {
		executor.shutdownNow();
	}
}
