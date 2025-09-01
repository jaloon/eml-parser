package io.github.jaloon.eml.part;

import io.github.jaloon.eml.MimeInputStream;

import java.io.IOException;
import java.util.List;

/**
 * AbstractMimePart 是一个抽象类，实现了 MimePart 接口，用于表示 MIME 消息的一部分。
 * 它包含了此 MIME 部分的基本信息，如头部和主体内容，并提供了访问这些信息的方法。
 * 此类旨在作为创建具体 MIME 部分类的基础，支持不同类型的内容（例如文本、图片等）的处理。
 */
public abstract class AbstractMimePart implements MimePart {
    /**
     * 存储此MIME部分的所有头部信息。
     * 列表中的每个元素代表一个单独的头部行，这些头部提供了关于MIME部分的重要元数据，
     * 如内容类型、内容传输编码等。
     */
    protected final List<String> headers;
    /**
     * 代表当前MIME部分的主体内容输入流。
     * 该字段用于存储和提供访问构成此MIME部分主体的数据的方法。
     * 通过这个输入流，可以读取MIME消息的实际内容，如文本、图片或其他附件等。
     * 注意：在使用此输入流时，请确保遵循资源管理的最佳实践，例如，在不再需要时关闭流以释放系统资源。
     */
    protected final MimeInputStream body;

    /**
     * 构造一个新的 AbstractMimePart 实例。
     *
     * @param headers 包含该 MIME 部分的所有头部信息的列表，这些头部描述了 MIME 部分的内容类型、内容传输编码等属性。
     * @param body    代表该 MIME 部分主体内容的输入流，用于读取实际的 MIME 内容，例如文本或图片等。
     */
    protected AbstractMimePart(List<String> headers, MimeInputStream body) {
        this.headers = headers;
        this.body = body;
    }

    /**
     * 返回此MIME部分的所有头部信息。
     *
     * @return 包含该MIME部分所有头部信息的列表。这些头部描述了MIME部分的内容类型、内容传输编码等属性。
     */
    @Override
    public List<String> getHeaders() {
        return headers;
    }

    /**
     * 返回当前MIME部分的主体内容
     *
     * @return 当前MIME部分的内容作为MimeInputStream对象。此流可以用来读取该MIME部分的实际数据。
     * @throws IOException 如果在获取或处理MIME部分时发生I/O错误
     */
    @Override
    public MimeInputStream getBody() throws IOException {
        return body;
    }
}
