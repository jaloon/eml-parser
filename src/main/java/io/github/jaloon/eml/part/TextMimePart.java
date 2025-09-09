package io.github.jaloon.eml.part;

import io.github.jaloon.eml.MimeInputStream;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * TextMimePart 类继承自 AbstractMimePart，专门用于表示邮件中的纯文本 MIME 部分。
 * 该类提供了获取 MIME 部分的字符集编码以及读取内容的方法。
 */
public class TextMimePart extends AbstractMimePart {

    /**
     * 构造一个新的TextMimePart实例，用于表示邮件中的纯文本MIME部分。
     *
     * @param headers MIME部分的头部信息列表，每个元素为一个完整的头部行（包括字段名和值）。
     * @param body    包含MIME部分实际内容的输入流。
     */
    public TextMimePart(List<String> headers, MimeInputStream body) {
        super(headers, body);
    }

    /**
     * 判断当前MIME部分是否为多部分消息。
     *
     * @return 总是返回false，表示此MIME部分不是一个多部分消息。
     */
    @Override
    public boolean isMultipart() {
        return false;
    }

    /**
     * 判断当前MIME部分是否为附件。
     *
     * @return 总是返回false，表示此MIME部分不是附件。
     */
    @Override
    public boolean isAttachment() {
        return false;
    }

    /**
     * 获取当前MIME部分的字符集编码。
     * 如果在内容类型头部中未指定字符集，则默认返回 UTF-8 字符集。
     * 如果指定的字符集无法被识别或创建失败，同样返回 UTF-8 字符集作为默认值。
     *
     * @return 当前MIME部分使用的字符集对象，若未指定或解析失败则为UTF-8。
     */
    public Charset getCharset() {
        String charset = MimePart.getHeadItem(getContentType(), "charset");
        if (charset.isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(charset);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * 从当前MIME部分读取并返回其内容作为字符串。
     *
     * @return 返回当前MIME部分的内容，使用适当的字符集进行解码。
     * @throws IOException 如果在读取或解码内容时发生I/O错误
     */
    public String getContent() throws IOException {
        return IOUtils.toString(getInputStream(), getCharset());
    }

}
