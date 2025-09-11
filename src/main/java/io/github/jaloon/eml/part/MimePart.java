package io.github.jaloon.eml.part;

import io.github.jaloon.eml.io.MimeInputStream;
import org.apache.commons.lang3.StringUtils;

import javax.mail.MessagingException;
import javax.mail.internet.MimePartDataSource;
import javax.mail.internet.MimeUtility;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
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
                .orElseGet(() -> getHeaders().stream()
                        .filter(line -> line.startsWith("Content-Type:"))
                        .map(line -> getHeadItem(line, "name"))
                        .findFirst()
                        .orElse(null));
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
        MimeInputStream body = getBody();
        if (body.getSize() == 0 || isMultipart() || getTransferEncoding() == null) {
            return body;
        }
        try {
            return MimeUtility.decode(body, getTransferEncoding());
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
     * 从给定的头部信息中提取指定头部项的值。
     *
     * @param header 完整的头部信息字符串，从中查找并提取头部项。
     * @param headItem 要查找的头部项名称。
     * @return 返回找到的头部项值。如果未找到匹配项，则返回空字符串。对于编码过的值，尝试解码后返回；若解码失败，则直接返回原始值。
     */
    static String getHeadItem(String header, String headItem) {
        String searchStr = headItem + '=';
        int index = header.indexOf(searchStr);
        if (index >= 0) {
            // Content-Disposition: attachment; filename="2(1)(1).pdf"
            // Content-Disposition: inline; filename="=?UTF-8?B?dGVtcDRjai5naWY=?="
            int startIndex = index + searchStr.length();
            char startChar = header.charAt(startIndex);
            String value;
            if (startChar != '"' && startChar != '\'') {
                value = header.substring(startIndex);
            } else {
                startIndex++;
                int endIndex = header.indexOf(startChar, startIndex);
                value = header.substring(startIndex, endIndex);
            }
            try {
                return MimeUtility.decodeText(value);
            } catch (UnsupportedEncodingException e) {
                System.err.printf("Error decoding header item: %s=%s%n", headItem, value);
                return value;
            }
        }

        searchStr = headItem + '*';
        index = header.indexOf(searchStr);
        if (index < 0) {
            return StringUtils.EMPTY;
        }
        int startIndex = index + searchStr.length();
        if (header.charAt(startIndex++) == '=') {
            // Content-Disposition: attachment; filename*=UTF-8''%33%E5%AF%86%E6%96%87%63%76%2E%74%78%74
            int endIndex = header.indexOf(';', startIndex);
            if (endIndex < 0) {
                endIndex = header.length();
            }
            int charsetIndex = header.indexOf("''", startIndex);
            if (charsetIndex < 0) {
                return header.substring(startIndex, endIndex);
            }
            String charset = header.substring(startIndex, charsetIndex);
            String value = header.substring(charsetIndex + 2, endIndex);
            try {
                return URLDecoder.decode(value, charset);
            } catch (UnsupportedEncodingException e) {
                System.err.printf("Error decoding header item: %s=%s%n", headItem, value);
                return value;
            }
        }
        if (header.charAt(startIndex - 1) == '0' && header.charAt(startIndex++) == '*' && header.charAt(startIndex++) == '=') {
            // Content-Disposition: attachment;
            //  filename*0*=UTF-8''%32%E5%AF%86%E6%96%87%E5%A4%8D%E5%88%B6%E7%B2%98;
            //  filename*1*=%E8%B4%B4%2E%74%78%74
            int charsetIndex = header.indexOf("''", startIndex);
            String charset = null;
            if (charsetIndex > 0) {
                charset = header.substring(startIndex, charsetIndex);
                startIndex = charsetIndex + 2;
            }
            int endIndex = header.indexOf(';', startIndex);
            String value;
            if (endIndex < 0) {
                value = header.substring(startIndex);
            } else {
                StringBuilder valueBuilder = new StringBuilder(header.substring(startIndex, endIndex));
                for (; ; ) {
                    startIndex = header.indexOf(searchStr, endIndex);
                    if (startIndex < 0) {
                        break;
                    }
                    startIndex += searchStr.length() + 3;
                    endIndex = header.indexOf(';', startIndex);
                    if (endIndex < 0) {
                        valueBuilder.append(header.substring(startIndex));
                        break;
                    }
                    valueBuilder.append(header, startIndex, endIndex);
                }
                value = valueBuilder.toString();
            }
            if (charset != null) {
                try {
                    return URLDecoder.decode(value, charset);
                } catch (UnsupportedEncodingException e) {
                    System.err.printf("Error decoding header item: %s=%s%n", headItem, value);
                }
            }
            return value;
        }
        return StringUtils.EMPTY;
    }

}
