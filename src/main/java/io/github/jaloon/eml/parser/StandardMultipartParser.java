package io.github.jaloon.eml.parser;

import io.github.jaloon.eml.MimeInputStream;
import io.github.jaloon.eml.part.MimePart;
import io.github.jaloon.eml.part.MultiMimePart;
import io.github.jaloon.eml.part.StandardMimePart;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * StandardMultipartParser 是实现了 MultipartParser 接口的类，用于解析多部分 MIME 消息。
 * 该类提供了一个单例模式实例获取方法，并且能够将复杂的多部分 MIME 消息分解为多个单独的 MIME 部分。
 * 每个解析出的部分都以 MimePart 形式表示，可以独立处理或分析。
 */
class StandardMultipartParser implements MultipartParser {
    private static volatile StandardMultipartParser instance;

    /**
     * 获取 StandardMultipartParser 的单例实例。
     * <p>
     * 该方法保证了在整个应用程序中只会创建一个 StandardMultipartParser 实例，
     * 通过双重检查锁定模式确保线程安全地初始化实例。
     *
     * @return 返回唯一的 StandardMultipartParser 实例
     */
    static StandardMultipartParser getInstance() {
        if (instance == null) {
            synchronized (StandardMultipartParser.class) {
                if (instance == null) {
                    instance = new StandardMultipartParser();
                }
            }
        }
        return instance;
    }

    /**
     * 私有构造函数，防止外部直接实例化StandardMultipartParser。
     * 该类遵循单例模式设计，因此不允许通过构造函数创建多个实例。
     * 使用者应通过调用{@link #getInstance()}方法来获取唯一实例。
     */
    private StandardMultipartParser() {}

    /**
     * 解析给定的多部分MIME消息，并将其分解为多个单独的MIME部分。
     *
     * @param message 要解析的多部分MIME消息
     * @return 包含从输入消息中提取的所有MIME部分的列表。如果输入的消息不是多部分格式，则返回空列表。
     * @throws IOException 如果在读取或处理MIME消息时发生I/O错误
     */
    @Override
    public List<MimePart> parse(MultiMimePart message) throws IOException {
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
                    parts.add(StandardMimePart.of(body, start, end));
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
                    parts.add(StandardMimePart.of(body, start, end));
                }
                body.reset();
                start = body.getPosition();
                end = -1;
                continue;
            }
            if (line.endsWith(boundaryEnd)) {
                if (start >= 0 && end > 0) {
                    parts.add(StandardMimePart.of(body, start, end));
                }
                start = -1;
                end = -1;
                break;
            }
            end = body.getPosition();
        }
        if (start >= 0 && end > 0) {
            parts.add(StandardMimePart.of(body, start, end));
        }
        return parts;
    }
}
