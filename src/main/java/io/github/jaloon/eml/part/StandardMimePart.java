package io.github.jaloon.eml.part;

import io.github.jaloon.eml.io.MimeInputStream;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;

/**
 * StandardMimePart 类实现了 MimePart 接口，用于表示一个标准的 MIME 部分。
 * 它可以从输入流中解析出 MIME 部分，并提供方法来获取该部分的各种属性，
 * 包括头部信息、内容类型、内容传输编码等。此外，它还支持多部分消息和附件。
 */
public class StandardMimePart extends AbstractMimePart {
    /**
     * 表示当前MIME部分的内容类型。
     * 该字段存储了描述MIME部分数据格式的字符串，例如"text/plain"或"image/jpeg"等，
     * 用于识别和处理不同类型的MIME内容。
     */
    protected String contentType;
    /**
     * 表示当前MIME部分是否为多部分类型。
     * 当此值为true时，表示该MIME部分包含了多个子部分，这些子部分通常由一个特定的边界字符串分隔。
     * 此属性用于判断MIME部分是否需要进一步解析以提取其包含的多个子部分。
     */
    protected boolean multipart;
    /**
     * 表示当前MIME部分的边界字符串。在多部分MIME消息中，此字段用于定义不同部分之间的分隔符。
     * 如果该MIME部分不是多部分类型，则此字段可能为null或未设置。
     */
    protected String boundary;
    /**
     * 指定当前MIME部分的内容传输编码方式。
     * 此属性描述了数据在传输过程中采用的编码方法，例如"base64"或"quoted-printable"等，
     * 用于确保数据能够正确无误地通过电子邮件或其他网络协议发送。
     */
    protected String contentTransferEncoding;
    /**
     * 附件变量，用于指示当前MIME部分是否为附件。
     */
    protected boolean attachment;
    /**
     * 文件名变量，用于存储当前MIME部分作为附件时的文件名称。
     * 如果此MIME部分不是附件或者没有指定文件名，则该字段可能为null或空字符串。
     */
    protected String filename;

    /**
     * 构造一个新的StandardMimePart实例，根据给定的头部信息列表和MIME输入流。
     *
     * @param headers 包含MIME部分头部信息的字符串列表
     * @param body    用于读取MIME部分主体内容的输入流
     */
    private StandardMimePart(List<String> headers, MimeInputStream body) {
        super(headers, body);
    }

    /**
     * 创建一个新的StandardMimePart对象，从给定的MIME输入流中解析头部信息及内容，并初始化该对象。
     *
     * @param in         用于读取MIME数据的输入流
     * @param partOffset MIME部分在输入流中的起始偏移量
     * @param partEnd    MIME部分在输入流中的结束位置
     * @return 新创建的StandardMimePart对象
     * @throws IOException 如果读取输入流时发生I/O错误
     */
    public static StandardMimePart of(MimeInputStream in, long partOffset, long partEnd) throws IOException {
        in.seek(partOffset);
        List<String> headers = MimePart.parseHeaders(in);
        MimeInputStream body = in.newStream(in.getPosition(), partEnd);
        StandardMimePart part = new StandardMimePart(headers, body);
        String attachHeader = null;
        for (String header : headers) {
            if (header.startsWith("Content-Type:")) {
                part.contentType = header.substring(14).trim();
                if (part.contentType.startsWith("multipart/")) {
                    part.multipart = true;
                    part.boundary = MimePart.getHeadItem(part.contentType, "boundary");
                    break;
                }
            } else if (header.startsWith("Content-Disposition: attachment;")) {
                // Content-Disposition: inline; filename="文件名" 为插入正文的图片或 emoji 表情
                part.attachment = true;
                attachHeader = header;
            } else if (header.startsWith("Content-Transfer-Encoding:")) {
                part.contentTransferEncoding = header.substring(26).trim();
            }
        }
        if (attachHeader != null) {
            String filename = MimePart.getHeadItem(attachHeader, "filename");
            if (StringUtils.isBlank(filename) && StringUtils.isNotBlank(part.contentType)) {
                filename = MimePart.getHeadItem(part.contentType, "name");
            }
            part.filename = filename;
        }
        return part;
    }

    /**
     * 获取当前MIME部分的内容类型。
     *
     * @return 当前MIME部分的内容类型字符串。该值描述了MIME部分的数据格式，例如"text/plain"或"image/jpeg"等。
     */
    @Override
    public String getContentType() {
        return contentType;
    }

    /**
     * 获取当前MIME部分的内容传输编码。
     *
     * @return 当前MIME部分的内容传输编码字符串。此值描述了数据在传输过程中的编码方式，例如"base64"或"quoted-printable"等。
     */
    @Override
    public String getTransferEncoding() {
        return contentTransferEncoding;
    }

    /**
     * 判断当前MIME部分是否为多部分类型。
     *
     * @return 如果此MIME部分是多部分消息，则返回true；否则返回false。
     */
    @Override
    public boolean isMultipart() {
        return multipart;
    }

    /**
     * 返回当前MIME部分的边界字符串。
     *
     * @return 当前MIME部分的边界字符串。如果此MIME部分是多部分消息，则该值用于分隔不同的部分。
     */
    @Override
    public String getBoundary() {
        return boundary;
    }

    /**
     * 检查当前MIME部分是否被标记为附件。
     *
     * @return 如果此MIME部分是附件，则返回true；否则返回false。
     */
    @Override
    public boolean isAttachment() {
        return attachment;
    }

    /**
     * 获取当前MIME部分作为附件时的文件名。
     *
     * @return 附件的文件名。如果此MIME部分不是附件或没有指定文件名，则可能返回null或空字符串。
     */
    @Override
    public String getAttachName() {
        return filename;
    }

    /**
     * 返回当前MIME部分的字符串表示形式。
     * 如果此MIME部分是附件，则返回格式为"[Attachment: 文件名]"；
     * 如果是多部分消息，则返回格式为"[Multipart: 边界字符串]"；
     * 否则，仅返回"MimePart"。
     * 所有头部信息和主体内容也会被格式化后加入到返回的字符串中。
     *
     * @return 当前MIME部分的详细信息，包括附件或多部分内容、头部信息以及主体内容。
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (attachment) {
            sb.append("[Attachment: ").append(filename).append("]");
        } else if (multipart) {
            sb.append("[Multipart: ").append(boundary).append("]");
        } else {
            sb.append("MimePart");
        }
        for (String header : headers) {
            sb.append("\n\t").append(header);
        }
        sb.append("\n\n").append(body).append("\n");
        return sb.toString();
    }
}
