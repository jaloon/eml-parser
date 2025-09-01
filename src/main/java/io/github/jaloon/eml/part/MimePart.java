package io.github.jaloon.eml.part;

import io.github.jaloon.eml.MimeInputStream;
import org.apache.commons.lang3.StringUtils;

import javax.mail.MessagingException;
import javax.mail.internet.MimePartDataSource;
import javax.mail.internet.MimeUtility;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * 代表MIME消息的一部分，可以是多部分、附件或其他类型的内容。
 * 提供了获取该部分的属性和内容的方法。
 */
public interface MimePart extends Closeable {
    /**
     * 返回此MIME部分的所有头部信息。
     *
     * @return 包含该MIME部分所有头部信息的列表。这些头部描述了MIME部分的内容类型、内容传输编码等属性。
     */
    List<String> getHeaders();

    /**
     * 判断当前MIME部分是否为多部分消息。
     *
     * @return 如果此MIME部分是一个多部分消息，则返回true；否则返回false。
     */
    boolean isMultipart();

    /**
     * 获取当前MIME部分的边界字符串。如果当前MIME部分不是多部分消息，则返回null。
     *
     * @return 当前MIME部分的边界字符串，如果此MIME部分不是一个多部分消息或者没有找到边界字符串，则返回null。
     */
    default String getBoundary() {
        if (!isMultipart()) {
            return null;
        }
        return getHeaders().stream()
                .filter(line -> line.startsWith("Content-Type:"))
                .map(line -> getHeadItem(line, "boundary"))
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断当前MIME部分是否为附件。
     *
     * @return 如果此MIME部分是一个附件，则返回true；否则返回false。
     */
    boolean isAttachment();

    /**
     * 获取当前MIME部分的附件名称。
     * <p>
     * 该方法首先检查当前MIME部分是否为附件。如果不是附件，则返回null。
     * 如果是附件，它会尝试从Content-Disposition头部获取filename参数值，并对其进行解码后返回。
     * 如果在解码过程中遇到不支持的编码异常，则直接返回原始的文件名字符串。
     *
     * @return 当前MIME部分的附件名称，如果当前MIME部分不是附件或者没有找到附件名称，则返回null。
     */
    default String getAttachName() {
        if (!isAttachment()) {
            return null;
        }
        return getHeaders().stream()
                .filter(line -> line.startsWith("Content-Disposition:"))
                .map(line -> getHeadItem(line, "filename"))
                .findFirst()
                .map(filename -> {
                    try {
                        return MimeUtility.decodeText(filename);
                    } catch (UnsupportedEncodingException e) {
                        System.err.println("Error decoding attachment filename: " + filename);
                    }
                    return filename;
                })
                .orElse(null);
    }

    /**
     * 获取当前MIME部分的内容体
     *
     * @return 当前MIME部分的内容体，以{@link MimeInputStream}形式返回。
     * @throws IOException 如果在获取内容体时发生I/O错误
     */
    MimeInputStream getBody() throws IOException;

    /**
     * 关闭当前MIME部分及其关联的资源。特别地，如果MIME部分有内容体，则关闭该内容体的{@link MimeInputStream}。
     *
     * @throws IOException 如果在关闭输入流时发生I/O错误
     */
    @Override
    default void close() throws IOException {
        MimeInputStream body = getBody();
        if (body != null) {
            body.close();
        }
    }

    /**
     * 获取当前MIME部分的内容类型。
     * <p>
     * 该方法通过解析MIME部分的头部信息来查找"Content-Type:"字段，并返回其值。如果找不到相应的内容类型，则返回null。
     *
     * @return 当前MIME部分的内容类型字符串，如果未找到则返回null。
     */
    default String getContentType() {
        return getHeaders().stream()
                .filter(line -> line.startsWith("Content-Type:"))
                .map(line -> line.substring(14).trim())
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取当前MIME部分的内容传输编码。
     *
     * @return 当前MIME部分的内容传输编码字符串，如果未找到则返回null。
     */
    default String getTransferEncoding() {
        return getHeaders().stream()
                .filter(line -> line.startsWith("Content-Transfer-Encoding:"))
                .map(line -> line.substring(26).trim())
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取当前MIME部分的内容体作为输入流。如果当前MIME部分是多部分消息或者没有内容传输编码，则直接返回内容体。
     * 如果存在内容传输编码，则尝试使用适当的解码器来解码内容体并返回解码后的输入流。
     *
     * @return 当前MIME部分解码后的内容体，以{@link InputStream}形式返回。
     * @throws IOException 如果在获取或解码内容体时发生I/O错误
     * @see MimePartDataSource#getInputStream() for more details
     */
    default InputStream getInputStream() throws IOException {
        if (isMultipart() || getTransferEncoding() == null) {
            return getBody();
        }
        try {
            return MimeUtility.decode(getBody(), getTransferEncoding());
        } catch (MessagingException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * 解析头部信息
     *
     * @param in 输入流
     * @return 头部信息列表
     */
    static List<String> parseHeaders(MimeInputStream in) throws IOException {
        List<String> headers = new ArrayList<>();
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
        return headers;
    }

    /**
     * 获取头部项的值
     *
     * @param header   头部信息
     * @param headItem 头部项
     * @return 头部项的值
     */
    static String getHeadItem(String header, String headItem) {
        String searchStr = headItem + '=';
        int index = header.indexOf(searchStr);
        if (index < 0) {
            return StringUtils.EMPTY;
        }
        int startIndex = index + searchStr.length();
        char startChar = header.charAt(startIndex);
        if (startChar != '"' && startChar != '\'') {
            return header.substring(startIndex);
        }
        startIndex++;
        int endIndex = header.indexOf(startChar, startIndex);
        return header.substring(startIndex, endIndex);
    }

}
