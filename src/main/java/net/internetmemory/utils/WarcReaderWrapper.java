package net.internetmemory.utils;

/**
 * Created by barton on 21/02/17.
 */

import net.internetmemory.utils.WarcRecord;
import org.jwat.common.HeaderLine;
import org.jwat.warc.WarcReader;
import org.jwat.warc.WarcReaderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileInputStream;
import java.io.IOException;

public class WarcReaderWrapper {
    public static int BUF_SIZE = 8 * 1024;

    public static final Logger LOG = LoggerFactory.getLogger(WarcReaderWrapper.class);

    public static MimeDetection mimeDetection = new MimeDetection();

    /**
     * Parse the jwat warc record to our internal warc record
     *
     * @param rec jwat warc record
     * @return internat warc record
     */
    public static WarcRecord parseWarcRecord(org.jwat.warc.WarcRecord rec) {
        WarcRecord retRecord = new WarcRecord();

        StringBuilder sb = new StringBuilder();
        // Decode the header, and set the related WARC fields
        for (HeaderLine headerLine : rec.getHeaderList()) {

            String headerName = headerLine.name;
            String headerValue = headerLine.value;

            // check for known keys
            if (headerName.equals(WarcRecord.TYPE_FIELD)) {
                retRecord.setWarcRecordType(headerValue);
            } else if (headerName.equals(WarcRecord.DATE_FIELD)) {
                retRecord.setWarcDate(headerValue);
            } else if (headerName.equals(WarcRecord.RECORD_ID_FIELD)) {
                retRecord.setWarcRecordId(headerValue);
            } else if (headerName.equals(WarcRecord.CONTENT_TYPE_FIELD)) {
                retRecord.setWarcContentType(headerValue);
            } else if (headerName.equals(WarcRecord.CONTENT_LENGTH_FIELD)) {
                retRecord.setContentLength(Long.parseLong(headerValue));
            } else {
                retRecord.addHeaderMetadata(headerName, headerValue);
            }
            sb.append(headerLine.line).append("\n");
        }

        // Set the content
        // the httpheader is not part of the payload if it is present, if it is
        // not present http header is null and payload contains whole content
        if (rec.getHttpHeader() != null) {
            try {
                retRecord.setContent(rec.getHttpHeader().getInputStreamComplete(), retRecord.getContentLength());
            } catch (Exception ex) {
                LOG.warn("Record parsing warning: ", ex);
                // if problems reading httpHeader (illegal state) then read payload
                //ByteArrayInputStream bais = new ByteArrayInputStream(Bytes.toBytes(sb.toString()));
                //SequenceInputStream sis = new SequenceInputStream(bais, rec.getPayloadContent());

                //retRecord.setContent(sis, retRecord.getContentLength());
                ex.printStackTrace();

                // which length use? the header in this case is in the payload apparently,
                // so no need to combine with artificial header (which is null anyway)
                //retRecord.setContent(rec.getPayloadContent(), retRecord.getContentLength());
                retRecord.setContent(rec.getPayloadContent(), rec.getPayload().getTotalLength());
            }
        } else {
            retRecord.setContent(rec.getPayloadContent(), rec.getPayload().getTotalLength());
        }
        return retRecord;
    }

    public static org.jwat.warc.WarcReader getReaderFromFile(String fName) throws IOException {
        WarcReader reader = WarcReaderFactory.getReader(new FileInputStream(fName), BUF_SIZE);
        return reader;
    }

    public static boolean isARes(WarcRecord record) {
        return record != null
                && (record.getWarcRecordType().equals(WarcRecord.RESPONSE_TYPE)
                || record.getWarcRecordType().equals(WarcRecord.LINK_TYPE));
    }
}
