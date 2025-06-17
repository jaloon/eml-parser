package io.github.jaloon.eml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MultiPartParser {

    public static List<MimePart> parse(EmlMessage message) throws IOException {
        if (!message.isMultipart()) {
            return Collections.emptyList();
        }
        String boundaryStart = "--" + message.getBoundary();
        String boundaryEnd = boundaryStart + "--";
        MimeInputStream body = message.getBody();
        List<MimePart> parts = new ArrayList<>();
        String line;
        long start = -1, end = -1;
        while ((line = body.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.equals(boundaryStart)) {
                body.mark(0);
                if (start >= 0 && end > 0) {
                    parts.add(new MimePart(body, start, end));
                }
                body.reset();
                start = body.getPosition();
                end = -1;
                continue;
            }
            if (line.endsWith(boundaryStart)) {
                body.mark(0);
                if (start >= 0) {
                    int len = line.length() - boundaryStart.length();
                    end = end < 0 ? len : end + len;
                    parts.add(new MimePart(body, start, end));
                }
                body.reset();
                start = body.getPosition();
                end = -1;
                continue;
            }
            if (line.endsWith(boundaryEnd) && start >= 0 && end > 0) {
                parts.add(new MimePart(body, start, end));
                break;
            }
            end = body.getPosition();
        }
        return parts;
    }

}
