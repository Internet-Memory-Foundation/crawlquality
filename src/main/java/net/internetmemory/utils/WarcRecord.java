/*
 * A Warc record encapsulation.
 * 
 * This code is derived from the Clue09 project -- 
 * see http://boston.lti.cs.cmu.edu/clueweb09
 */
package net.internetmemory.utils;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.io.LimitInputStream;

/**
 *
 * A WARC record representation
 *
 * @author mhoy@cs.cmu.edu (Mark J. Hoy)
 */
public class WarcRecord {

    /**
     * List of the Warc record types
     */
    public static final String WARCINFO_TYPE = "warcinfo",
            RESPONSE_TYPE = "response",
            RESOURCE_TYPE = "resource",
            REQUEST_TYPE = "request",
            METADATA_TYPE = "metadata",
            REVISIT_TYPE = "revisit",
            CONVERSION_TYPE = "conversion",
            CONTINUATION_TYPE = "continuation",
            LINK_TYPE = "link";

    /**
     * A list of the predefined Metadata fields
     */
    public static final String TARGET_URI_FIELD = "WARC-Target-URI",
            DATE_FIELD = "WARC-Date", TYPE_FIELD = "WARC-Type",
            CONTENT_TYPE_FIELD = "Content-Type",
            CONTENT_LENGTH_FIELD = "Content-Length",
            PAYLOAD_DIGEST_FIELD = "WARC-Payload-Digest",
            BLOCK_DIGEST_FIELD = "WARC-Block-Digest",
            REFERS_TO_FIELD = "WARC-Refers-To",
            FILENAME_FIELD = "WARC-Filename",
            RECORD_ID_FIELD = "WARC-Record-ID",
            WARCINFO_ID_FIELD = "WARC-Warcinfo-ID",
            IP_ADDRESS_FIELD = "WARC-IP-Address",
            CONCURRENT_TO_FIELD = "WARC-Concurrent-To",
            IDENTIFIED_PAYLOAD_TYPE_FIELD = "WARC-Identified-Payload-Type";
    // public static final Log LOG = LogFactory.getLog(WarcRecord.class);
    //public static final String WARC_VERSION = "WARC/0.18";
    // with this version specification the library is unable to read newer WARCS (see readNextRecord())
    public static final String WARC_VERSION = "WARC/";
    public static final String WARC_VERSION_LINE = "WARC/0.18\n";
    private static final String NEWLINE = "\n";
    private static final String CR_NEWLINE = "\r\n";
    private static final byte MASK_THREE_BYTE_CHAR = (byte) (0xE0);
    private static final byte MASK_TWO_BYTE_CHAR = (byte) (0xC0);
    private static final byte MASK_TOPMOST_BIT = (byte) (0x80);
    private static final byte MASK_BOTTOM_SIX_BITS = (byte) (0x1F);
    private static final byte MASK_BOTTOM_FIVE_BITS = (byte) (0x3F);
    private static final byte MASK_BOTTOM_FOUR_BITS = (byte) (0x0F);
    private static String LINE_ENDING = "\n";

    /**
     * Representation of a Warc recored header
     */
    public class WarcHeader {

        /**
         * Mandatory fields
         */
        public String contentType = "";
        /**
         * Record ID
         */
        public String UUID = "";
        /**
         * Date of the Warc record
         */
        public String dateString = "";
        /**
         * Type of the Warc record
         */
        public String recordType = "";
        /**
         * length of the record's block
         */
        public long contentLength = Long.valueOf(0);
        /**
         * Additional fields
         */
        public HashMap<String, String> metadata = new HashMap<String, String>();

        /**
         * Default constructor
         */
        public WarcHeader() {
        }

        /**
         * Copy constructor
         * 
         * @param o The header to copy from
         */
        public WarcHeader(WarcHeader o) {
            this.contentType = o.contentType;
            this.UUID = o.UUID;
            this.dateString = o.dateString;
            this.recordType = o.recordType;
            this.metadata.putAll(o.metadata);
            this.contentLength = o.contentLength;
        }

        @Override
        public String toString() {
            StringBuilder retBuffer = new StringBuilder();

            retBuffer.append(WARC_VERSION);
            retBuffer.append(LINE_ENDING);

            retBuffer.append(TYPE_FIELD + ": " + recordType + LINE_ENDING);
            retBuffer.append(DATE_FIELD + ": " + dateString + LINE_ENDING);

            retBuffer.append(RECORD_ID_FIELD + ": " + UUID + LINE_ENDING);
            Iterator<Entry<String, String>> metadataIterator = metadata.entrySet().iterator();
            while (metadataIterator.hasNext()) {
                Entry<String, String> thisEntry = metadataIterator.next();
                retBuffer.append(thisEntry.getKey());
                retBuffer.append(": ");
                retBuffer.append(thisEntry.getValue());
                retBuffer.append(LINE_ENDING);
            }

            retBuffer.append(CONTENT_TYPE_FIELD + ": " + contentType + LINE_ENDING);
            retBuffer.append("Content-Length: " + contentLength + LINE_ENDING);

            return retBuffer.toString();
        }
    } // End of the inner WarcHEader class
    /**
     * A Warc record = a Warc header 
     */
    private WarcHeader warcHeader = new WarcHeader();
    
    /**
     * Binary content in a input stream
     */
    private InputStream warcContentInputStream = null;
    /**
     * The file is stored somewhere
     */
    private String warcFilePath = "";

    /**
     * Public constructor
     * 
     */
    public WarcRecord() {
    }

    /**
     * Copy constructor
     * 
     * @param o The record to copy from
     */
    public WarcRecord(WarcRecord o) {
        this.warcHeader = new WarcHeader(o.warcHeader);
        this.warcContentInputStream = o.warcContentInputStream;
    }

    /**
     * Copy from another Warc record
     */
    public void set(WarcRecord o) {
        this.warcHeader = new WarcHeader(o.warcHeader);
        this.warcContentInputStream = o.warcContentInputStream;
    }

    /**
     * Gets the path of the Warc file
     * 
     * @return The path
     */
    public String getWarcFilePath() {
        return warcFilePath;
    }

    /**
     * Adds some metadata to the record
     * 
     * @param key
     * @param value 
     */
    public void addHeaderMetadata(String key, String value) {
        // don't allow addition of known keys
        if (key.equals(TYPE_FIELD)
                || key.equals(CONTENT_TYPE_FIELD)
                || key.equals(RECORD_ID_FIELD)
                || key.equals(DATE_FIELD)
                || key.equals(CONTENT_LENGTH_FIELD)) {
            return;
        }

        warcHeader.metadata.put(key, value);
    }

    /**
     * Clear the metadata set
     */
    public void clearHeaderMetadata() {
        warcHeader.metadata.clear();
    }

    /**
     * Get all the metadata hash map
     * 
     * @return The set of metadata
     */
    public Set<Entry<String, String>> getHeaderMetadata() {
        return warcHeader.metadata.entrySet();
    }

    /**
     * Get a specific metadata item
     * 
     * @param key
     * @return The metadata value 
     */
    public String getHeaderMetadataItem(String key) {
        return warcHeader.metadata.get(key);
    }

    /**
     * Setters and getters
     */
    public void setWarcFilePath(String path) {
        warcFilePath = path;
    }

    public void setWarcRecordType(String rType) {
        warcHeader.recordType = rType;
    }

    public String getWarcRecordType() {
        return warcHeader.recordType;
    }

    public void setWarcContentType(String contentType) {
        warcHeader.contentType = contentType;
    }

    public String getWarcContentType() {
        return warcHeader.contentType;
    }

    public void setWarcDate(String dateString) {
        warcHeader.dateString = dateString;
    }

    public String getWarcDate() {
        return warcHeader.dateString;
    }

    public long getWarcTimestamp() {
        //examle 2010 01 28 16 27 33                                
        //SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");        
        //WARC date: 2011-02-25T18:32:17Z
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        try {
            Date date = sdf.parse(warcHeader.dateString);
            return date.getTime();
        } catch (Exception ex) {
            // Unable to parse the Date
            Logger.getLogger(WarcRecord.class.getName()).log(Level.SEVERE, null, ex);
            Date date = new Date();
            return date.getTime();
        }

    }

    public void setWarcRecordId(String UUID) {
        warcHeader.UUID = UUID;
    }

    public String getWarcRecordId() {
        return warcHeader.UUID;
    }

    public String getTargetURI() {
        return warcHeader.metadata.get(TARGET_URI_FIELD);
    }

    public String getRefersTo() {
        return warcHeader.metadata.get(REFERS_TO_FIELD);
    }

    public String getIP() { return warcHeader.metadata.get(IP_ADDRESS_FIELD); }

    public InputStream getContent() {
    	return new InputStream() {
			@Override
			public int read() throws IOException {
				return warcContentInputStream.read();
			}
			
			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				return warcContentInputStream.read(b, off, len);
			}
			
			@Override
			public int available() throws IOException {
				return warcContentInputStream.available();
			}
			
			@Override
			public void close() throws IOException {
				// IMPORTANT: DO NOTHING HERE!
			}
		};
    }

    public void setContent(InputStream content, long contentLength) {
    	warcContentInputStream = content;
        warcHeader.contentLength = contentLength;
    }

    public void setContentLength(Long len) {
        warcHeader.contentLength = len;
    }
    
    private boolean readInputStream = false;
    
    public byte[] getContentAsByteArray() throws IOException {
    	if(readInputStream) {
    		throw new UnsupportedOperationException("Can only convert to " +
    				"byte[] once");
    	} else {
    		readInputStream = true;
    		int totalRead = 0;
    		int length = (int) warcHeader.contentLength;
    		byte[] content = new byte[length];
    		while(totalRead != length) {
    			int read = warcContentInputStream.read(content, totalRead,
        				length - totalRead);
    			if(read < 0) {
    				throw new EOFException("Unexpected end of stream");
    			} else {
    				totalRead += read;
    			}
    		}
    		
    		return content;
    	}
    }

    public String getContentUTF8() throws IOException {
        String retString = null;
        try {
            retString = new String(getContentAsByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException ex) {
        	throw new RuntimeException("Impossible");
        }
        return retString;
    }

    public String getHeaderString() {
        return warcHeader.toString();
    }

    @Override
    public String toString() {
        StringBuilder retBuffer = new StringBuilder();
        retBuffer.append(warcHeader.toString());
        return retBuffer.toString();
    }

    /**
     * Decode the input stream of a WARC file and gets a fragment
     * representing a Warc record's line.
     * 
     * @param in Any input stream over a Warc file
     * 
     * @return A String containing a Warc record line
     * @throws IOException 
     */
    private static String readLineFromInputStream(DataInputStream in) throws IOException {
        StringBuilder retString = new StringBuilder();
        boolean found_cr = false;
        boolean keepReading = true;
        try {
            do {
                char thisChar = 0;
                byte readByte = in.readByte();
                // check to see if it's a multibyte character
                if ((readByte & MASK_THREE_BYTE_CHAR) == MASK_THREE_BYTE_CHAR) {
                    found_cr = false;
                    // need to read the next 2 bytes
                    if (in.available() < 2) {
                        // treat these all as individual characters
                        retString.append((char) readByte);
                        int numAvailable = in.available();
                        for (int i = 0; i < numAvailable; i++) {
                            retString.append((char) (in.readByte()));
                        }
                        continue;
                    }
                    byte secondByte = in.readByte();
                    byte thirdByte = in.readByte();
                    // ensure the topmost bit is set
                    if (((secondByte & MASK_TOPMOST_BIT) != MASK_TOPMOST_BIT)
                            || ((thirdByte & MASK_TOPMOST_BIT) != MASK_TOPMOST_BIT)) {
                        //treat these as individual characters
                        retString.append((char) readByte);
                        retString.append((char) secondByte);
                        retString.append((char) thirdByte);
                        continue;
                    }
                    int finalVal = (thirdByte & MASK_BOTTOM_FIVE_BITS)
                            + 64 * (secondByte & MASK_BOTTOM_FIVE_BITS)
                            + 4096 * (readByte & MASK_BOTTOM_FOUR_BITS);
                    thisChar = (char) finalVal;
                } else if ((readByte & MASK_TWO_BYTE_CHAR) == MASK_TWO_BYTE_CHAR) {
                    found_cr = false;

                    // need to read next byte
                    if (in.available() < 1) {
                        // treat this as individual characters
                        retString.append((char) readByte);
                        continue;
                    }
                    byte secondByte = in.readByte();
                    if ((secondByte & MASK_TOPMOST_BIT) != MASK_TOPMOST_BIT) {
                        retString.append((char) readByte);
                        retString.append((char) secondByte);
                        continue;
                    }
                    int finalVal = (secondByte & MASK_BOTTOM_FIVE_BITS) + 64 * (readByte & MASK_BOTTOM_SIX_BITS);
                    thisChar = (char) finalVal;
                } else {
                    // interpret it as a single byte
                    thisChar = (char) readByte;
                }

                // Look for carriage return; if found set a flag
                if (thisChar == '\r') {
                    found_cr = true;
                }
                if (thisChar == '\n') {
                    // if the linefeed is the next character after the carriage return
                    if (found_cr) {
                        LINE_ENDING = CR_NEWLINE;
                    } else {
                        LINE_ENDING = NEWLINE;
                    }
                    keepReading = false;
                } else {
                    retString.append(thisChar);
                }
            } while (keepReading);
        } catch (EOFException eofEx) {            
            return null;
        }

        if (retString.length() == 0) {
            return "";
        }

        return retString.toString();
    }

    /**
     * Get the content (block) of a Warc record from an input stream
     * 
     * @param in Any input stream
     * @param headers List of header strings
     * 
     * @return the record content
     * 
     * @throws IOException 
     */
    private static Content readNextRecord(DataInputStream in,
            List<String> headers) throws IOException {

        if (in == null) {
            throw new IOException("Cannot read from a null stream");
        }
        if (headers == null) {
            throw new IOException("Cannot read a record in a null string buffer");
        }

        String line = null;
        boolean foundMark = false;
        boolean inHeader = true;

        // Scan the file line by line until we find a WARC mark
        while ((!foundMark) && ((line = readLineFromInputStream(in)) != null)) {
        	// Due to bugs in the readLineFromInputStream method this may contain
        	// several lines (this happens it thinks it has a detected multibytes
        	// but then treats them as single characters without checking for
        	// file returns)
        	if(line.contains("\n")) {
        		String[] lines = line.split("\n");
        		for (String fixedLine : lines) {
        			if (fixedLine.startsWith(WARC_VERSION)) {
                        foundMark = true;
                    }
				}
        	} else if (line.startsWith(WARC_VERSION)) {
                foundMark = true;
            }
        }
        
        // No WARC mark? We probably fully scanned the file
        if (!foundMark) {
            return null;
        }

        // LOG.info("Found WARC_VERSION");

        // We read the header (all the non-empty lines) 
        // get the content length and set our retContent
        while (inHeader && ((line = readLineFromInputStream(in)) != null)) {
            if (line.trim().length() == 0) {
                inHeader = false;
            } else {
                headers.add(line);
                //headerBuffer.append(LINE_ENDING);
            }
        }

        // ok - we've got our header - find the content length
        // designated by Content-Length: <length>
        int contentLength = -1;
        for (String header : headers) {
        	if(header.startsWith("Content-Length")) {
                String[] thisHeaderPieceParts = header.split(":", 2);
                
                try {
                    contentLength = Integer.parseInt(thisHeaderPieceParts[1].trim());
                    // LOG.info("WARC record content length: " + contentLength);
                    break;
                } catch (NumberFormatException nfEx) {
                    contentLength = -1;
                }
        	}
        }

        if (contentLength < 0) {
            // We were unable to find the content's length
            return null;
        }

        return new Content(new LimitInputStream(in, contentLength), contentLength);
    }

    /**
     * Get the next record from a DataStream
     * 
     * @param in A datastream
     * 
     * @return A newly instantiated WARC record, null when the file is 
     *   fully scanned
     * 
     * @throws IOException 
     */
    public static WarcRecord readNextWarcRecord(DataInputStream in)
            throws IOException {
        // LOG.info("Starting read of WARC record");
    	ArrayList<String> headers = new ArrayList<String>();

        // Decode the next record from the stream
        Content recordContent = readNextRecord(in, headers);
        
        // Did we find something?
        if (recordContent == null) {            
            return null;
        }

        // OK, we found a content. Instantiate the new record
        WarcRecord retRecord = new WarcRecord();

        // Decode the header, and set the related WARC fields
        for (String header : headers) {
            String[] pieces = header.split(":", 2);
            if (pieces.length != 2) {
                retRecord.addHeaderMetadata(pieces[0], "");
                continue;
            }
            String headerName = pieces[0].trim();
            String headerValue = pieces[1].trim();

            // check for known keys
            if (headerName.equals(TYPE_FIELD)) {
                retRecord.setWarcRecordType(headerValue);
            } else if (headerName.equals(DATE_FIELD)) {
                retRecord.setWarcDate(headerValue);
            } else if (headerName.equals(RECORD_ID_FIELD)) {
                retRecord.setWarcRecordId(headerValue);
            } else if (headerName.equals(CONTENT_TYPE_FIELD)) {
                retRecord.setWarcContentType(headerValue);
            } else if (headerName.equals(CONTENT_LENGTH_FIELD)) {
                retRecord.setContentLength(Long.parseLong(headerValue));
            } else {
                retRecord.addHeaderMetadata(headerName, headerValue);
            }
		}

        // Set the content
        retRecord.setContent(recordContent.is, recordContent.length);

        return retRecord;
    }
    
    private static class Content {
    	InputStream is;
    	long length;
    	
    	public Content(InputStream is, long length) {
    		this.is = is;
    		this.length = length;
    	}
    }

	public long getContentLength() {
		return warcHeader.contentLength;
	}
}
