package io.github.jaloon.eml.part;

import io.github.jaloon.eml.MimeInputStream;

import java.util.List;

/**
 * AttachmentPart 类继承自 AbstractMimePart，专门用于表示 MIME 消息中的附件部分。
 * 该类提供了关于附件的基本信息以及相关操作方法，确保可以正确地处理和访问邮件中的附件内容。
 */
public class AttachmentPart extends AbstractMimePart {
    /**
     * 代表附件部分的文件名。该字段用于存储与MIME消息中特定附件相关的文件名称，
     * 在构造AttachmentPart对象时被初始化，并在整个对象生命周期内保持不变。
     */
    private final String filename;

    /**
     * 构造一个新的AttachmentPart对象，该对象代表MIME消息中的一个附件部分。
     *
     * @param filename 附件的文件名。
     * @param headers 与此附件相关的MIME头部信息列表。
     * @param body 包含实际附件内容的输入流。
     */
    public AttachmentPart(String filename, List<String> headers, MimeInputStream body) {
        super(headers, body);
        this.filename = filename;
    }

    /**
     * 判断当前MIME部分是否为多部分消息。
     *
     * @return 总是返回false，表示此MIME部分不是一个多部分消息。·
     */
    @Override
    public boolean isMultipart() {
        return false;
    }

    /**
     * 判断当前MIME部分是否为附件。
     *
     * @return 总是返回true，表示此MIME部分是附件。
     */
    @Override
    public boolean isAttachment() {
        return true;
    }

    /**
     * 返回附件的文件名。
     *
     * @return 附件的文件名。
     */
    public String getAttachName() {
        return filename;
    }

}
