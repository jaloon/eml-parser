package io.github.jaloon.eml;

import io.github.jaloon.eml.part.MimePart;
import io.github.jaloon.eml.part.MultiMimePart;

import javax.mail.internet.MimeUtility;
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
     * 从给定的文件中创建一个 EmlMessage 对象。
     *
     * @param file 包含电子邮件消息的文件
     * @return 新创建的 EmlMessage 对象
     * @throws IOException 如果读取文件时发生 I/O 错误
     */
    public static EmlMessage of(File file) throws IOException {
        MimeInputStream inputStream = new MimeInputStream(file);
        List<String> headers = MimePart.parseHeaders(inputStream);
        MimeInputStream body = inputStream.newStream(inputStream.getPosition(), file.length());
        EmlMessage emlMessage = new EmlMessage(headers, body);
        emlMessage.size = file.length();
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
        return emlMessage;
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
