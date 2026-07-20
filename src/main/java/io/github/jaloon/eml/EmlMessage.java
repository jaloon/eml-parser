package io.github.jaloon.eml;

import io.github.jaloon.eml.io.ByteArrayMimeInputStream;
import io.github.jaloon.eml.io.MimeInputStream;
import io.github.jaloon.eml.part.MimePart;
import io.github.jaloon.eml.part.MultiMimePart;
import jakarta.mail.internet.MimeUtility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * EmlMessage 类表示一个电子邮件消息，继承自 MultiMimePart。它提供了获取邮件大小、发件人、收件人和主题的方法。
 * 该类通过解析指定文件来创建实例，并从中提取相关的邮件头部信息如发件人、收件人及主题。
 */
public class EmlMessage extends MultiMimePart {
    /**
     * 表示此电子邮件消息的大小，以字节为单位。
     * 该值通常通过解析邮件文件时确定，并反映整个邮件（包括头部和正文）占用的空间大小。
     */
    private long size;
    /**
     * 表示电子邮件消息的发件人地址。
     * 该字段存储了邮件头部"From:"字段的内容，经过处理去除了前缀并去除多余空白。
     */
    private String from;
    /**
     * 表示电子邮件消息的收件人地址。
     * 该字段存储了邮件头部"To:"字段的内容，经过处理去除了前缀并去除多余空白。
     */
    private String to;
    /**
     * 表示电子邮件消息的主题。
     * 该字段存储了邮件头部"Subject:"字段的内容，并通过MimeUtility.decodeText方法解码以支持包含非ASCII字符的主题。
     */
    private String subject;

    /**
     * 构造一个新的 EmlMessage 实例。
     *
     * @param headers 邮件头部信息列表
     * @param body    邮件体，作为 MimeInputStream 对象提供
     */
    private EmlMessage(List<String> headers, MimeInputStream body) {
        super(headers, body);
    }

    /**
     * 返回此电子邮件消息的大小（以字节为单位）。
     *
     * @return 该邮件的大小
     */
    public long getSize() {
        return size;
    }

    /**
     * 返回此电子邮件消息的发件人地址。
     *
     * @return 发件人的电子邮件地址
     */
    public String getFrom() {
        return from;
    }

    /**
     * 返回此电子邮件消息的收件人地址。
     *
     * @return 收件人的电子邮件地址
     */
    public String getTo() {
        return to;
    }

    /**
     * 返回此电子邮件消息的主题。
     *
     * @return 邮件的主题
     */
    public String getSubject() {
        return subject;
    }

    /**
     * 关闭邮件消息。
     * @throws IOException 邮件消息关闭时发生的异常
     */
    @Override
    public void close() throws IOException {
        this.getBody().forceClose();
    }

    /**
     * 从内存字节数组创建一个 EmlMessage 对象。
     * <p>
     * 适用于已将邮件文件一次性读入内存的场景（如通过 {@code Files.readAllBytes()} 读取网络路径文件）。
     * 内部使用 {@link ByteArrayMimeInputStream} 实现零拷贝内存解析，
     * 避免 {@link java.io.RandomAccessFile} 对网络路径的逐字节 seek + read 性能瓶颈。
     * <p>
     * 典型用法：
     * <pre>{@code
     * byte[] data = Files.readAllBytes(Paths.get(emlPath));
     * EmlMessage message = EmlMessage.of(data);
     * List<MimePart> parts = MultipartParser.quickly().parse(message);
     * }</pre>
     *
     * @param data 包含完整邮件内容的字节数组
     * @return 新创建的 EmlMessage 对象
     * @throws IOException 如果解析邮件头部时发生 I/O 错误
     * @see #of(File) 从本地文件创建（使用 RandomAccessFile）
     */
    public static EmlMessage of(byte[] data) throws IOException {
        ByteArrayMimeInputStream inputStream = new ByteArrayMimeInputStream(data);
        List<String> headers = MimePart.parseHeaders(inputStream);
        long headerEnd = inputStream.getPosition();
        MimeInputStream body = inputStream.newStream(headerEnd, data.length);
        EmlMessage emlMessage = new EmlMessage(headers, body);
        emlMessage.size = data.length;
        parseHeaders(emlMessage, headers);
        return emlMessage;
    }

    /**
     * 从给定的文件中创建一个 EmlMessage 对象。
     *
     * @param file 包含电子邮件消息的文件
     * @return 新创建的 EmlMessage 对象
     * @throws IOException 如果读取文件时发生 I/O 错误
     */
    public static EmlMessage of(File file) throws IOException {
        MimeInputStream inputStream = MimeInputStream.of(file);
        List<String> headers = MimePart.parseHeaders(inputStream);
        MimeInputStream body = inputStream.newStream(inputStream.getPosition(), file.length());
        EmlMessage emlMessage = new EmlMessage(headers, body);
        emlMessage.size = file.length();
        parseHeaders(emlMessage, headers);
        return emlMessage;
    }

    /**
     * 从邮件头部列表中解析发件人、收件人和主题信息，并设置到指定的 EmlMessage 对象上。
     *
     * @param emlMessage 要设置属性的 EmlMessage 对象
     * @param headers    邮件头部信息列表
     * @throws IOException 如果解码主题时发生异常
     */
    private static void parseHeaders(EmlMessage emlMessage, List<String> headers) throws IOException {
        int parseCount = 0;
        for (String header : headers) {
            if (header.startsWith("From:")) {
                emlMessage.from = getAddress(header.substring(5));
                parseCount++;
            } else if (header.startsWith("To:")) {
                emlMessage.to = getAddress(header.substring(3));
                parseCount++;
            } else if (header.startsWith("Subject:")) {
                emlMessage.subject = MimeUtility.decodeText(header.substring(8).trim());
                parseCount++;
            }
            if (parseCount >= 3) {
                break;
            }
        }
    }

    /**
     * 将给定的邮件头部值转换为 UTF-8 编码的地址字符串。
     *
     * @param headerValue 邮件头部中获取的原始地址字符串
     * @return 转换后的 UTF-8 编码的地址字符串
     */
    private static String getAddress(String headerValue) {
        return new String(headerValue.trim().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }
}
