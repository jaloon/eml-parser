package io.github.jaloon.eml;

import io.github.jaloon.eml.parser.MultipartParser;
import io.github.jaloon.eml.part.MimePart;
import org.junit.Before;
import org.junit.Test;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class MultiPartParserTest {
    private String resourcePath;

    @Before
    public void getPath() {
        URL url = this.getClass().getClassLoader().getResource("");
        assert url != null;
        resourcePath = url.getPath();
    }

    @Test
    public void testParse() throws IOException {
        // String eml = "STMP_outlook.eml";
        String eml = "WEB20250512092651.eml";
        try (EmlMessage message = EmlMessage.of(new File(resourcePath, eml))){
            List<MimePart> parts = MultipartParser.standard().parse(message);
            for (MimePart part : parts) {
                if (part.isAttachment()) {
                    String fileName = part.getAttachName();
                    System.out.println(fileName);
                    Files.copy(part.getInputStream(), Paths.get(resourcePath, fileName));
                }
            }
        }
    }

    @Test
    public void testParseByJavaMail() throws IOException, MessagingException {
        String eml = "WEB20250512092651.eml";
        try (InputStream in = Files.newInputStream(Paths.get(resourcePath, eml))) {
            MimeMessage message = new MimeMessage(null, in);
            if (message.isMimeType("multipart/*")) {
                Multipart multipart = (Multipart) message.getContent();
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart part = multipart.getBodyPart(i);
                    if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                        String fileName = part.getFileName();
                        fileName = MimeUtility.decodeText(fileName);
                        System.out.println(fileName);
                    }
                }
            }

        }
    }

}