package io.github.jaloon.eml;

import org.apache.commons.lang3.StringUtils;

import javax.mail.MessagingException;
import javax.mail.internet.MimePartDataSource;
import javax.mail.internet.MimeUtility;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MimePart implements Closeable {
    protected List<String> headers;
    protected String contentType;

    protected boolean multipart;
    protected String boundary;

    protected String contentTransferEncoding;

    protected boolean attachment;
    protected String filename;

    protected MimeInputStream body;

    public MimePart(MimeInputStream in, long partOffset, long partEnd) throws IOException {
        this.headers = new ArrayList<>();
        in.seek(partOffset);
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            char first = line.charAt(0);
            if ((first == ' ' || first == '\t') && !headers.isEmpty()) {
                headers.set(headers.size() - 1, headers.get(headers.size() - 1) + "\n" + line);
            } else {
                headers.add(line);
            }
        }
        for (String header : headers) {
            if (header.startsWith("Content-Type:")) {
                contentType = header.substring(14).trim();
                if (contentType.startsWith("multipart/")) {
                    multipart = true;
                    boundary = subHeader(contentType, "boundary=");
                    break;
                }
            } else if (header.startsWith("Content-Disposition:")) {
                attachment = true;
                filename = MimeUtility.decodeText(subHeader(header, "filename="));
            } else if (header.startsWith("Content-Transfer-Encoding:")) {
                contentTransferEncoding = header.substring(26).trim();
            }
        }
        this.body = in.newStream(in.getPosition(), partEnd);
    }

    private String subHeader(String header, String searchStr) {
        return StringUtils.strip(header.substring(header.indexOf(searchStr) + searchStr.length()), "\"' ");
    }

    public List<String> getHeaders() {
        return headers;
    }

    public String getContentType() {
        return contentType;
    }

    public boolean isMultipart() {
        return multipart;
    }

    public String getBoundary() {
        return boundary;
    }

    public boolean isAttachment() {
        return attachment;
    }

    public String getFilename() {
        return filename;
    }


    public MimeInputStream getBody() {
        return body;
    }

    /**
     * @see MimePartDataSource#getInputStream() for more details
     */
    public InputStream getInputStream() throws IOException {
        if (multipart || contentTransferEncoding == null) {
            return body;
        }
        try {
            return MimeUtility.decode(body, contentTransferEncoding);
        } catch (MessagingException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        body.close();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MimePart");
        if (attachment) {
            sb.append("[Attachment filename=").append(filename).append("]");
        }
        if (multipart) {
            sb.append("[Multipart boundary=").append(boundary).append("]");
        }
        for (String header : headers) {
            sb.append("\n\t").append(header);
        }
        sb.append("\n\n").append(body).append("\n");
        return sb.toString();
    }

}
