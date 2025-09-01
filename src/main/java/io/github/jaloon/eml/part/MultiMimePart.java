package io.github.jaloon.eml.part;

import io.github.jaloon.eml.MimeInputStream;

import java.util.List;

/**
 * MultiMimePart 类扩展了 AbstractMimePart，专门用于表示多部分的 MIME 消息。
 * 多部分消息包含多个正文部分，每个部分都有自己的标题和内容，通常用于发送带有附件或混合内容类型的电子邮件。
 */
public class MultiMimePart extends AbstractMimePart {

    /**
     * 构造一个新的 MultiMimePart 实例。
     * 该构造函数通过给定的消息头列表和 MIME 输入流来初始化一个表示多部分 MIME 消息的对象。
     *
     * @param headers 包含该 MIME 部分的所有头部信息的列表，这些头部描述了 MIME 部分的内容类型、内容传输编码等属性。
     * @param body    代表多部分 MIME 消息体的输入流，用于读取实际的 MIME 内容。
     */
    public MultiMimePart(List<String> headers, MimeInputStream body) {
        super(headers, body);
    }

    /**
     * 判断当前MIME部分是否为多部分消息。
     *
     * @return 总是返回true，表示此MIME部分确实是一个多部分消息。
     */
    @Override
    public boolean isMultipart() {
        return true;
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

}
