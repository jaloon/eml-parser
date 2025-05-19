package io.github.jaloon.eml;

import javax.mail.internet.MimeUtility;
import java.io.File;
import java.io.IOException;

public class EmlMessage extends MimePart {
    private String from;
    private String to;
    private String subject;

    public EmlMessage(File file) throws IOException {
        super(new MimeInputStream(file), 0, file.length());
        for (String header : headers) {
            if (header.startsWith("From:")) {
                from = header.substring(5).trim();
            } else if (header.startsWith("To:")) {
                to = header.substring(3).trim();
            } else if (header.startsWith("Subject:")) {
                subject = MimeUtility.decodeText(header.substring(8).trim());
            }
        }
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getSubject() {
        return subject;
    }

    @Override
    public void close() throws IOException {
        body.forceClose();
    }
}
