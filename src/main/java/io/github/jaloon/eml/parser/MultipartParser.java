package io.github.jaloon.eml.parser;

import io.github.jaloon.eml.part.MimePart;
import io.github.jaloon.eml.part.MultiMimePart;

import java.io.IOException;
import java.util.List;


/**
 * 定义了一个用于解析多部分MIME消息的功能接口。实现此接口的类能够将复杂的多部分MIME消息分解为多个单独的MIME部分，
 * 使得每个部分可以独立处理或分析。
 *
 * @see MimePart 接口代表了解析出的单个MIME部分，包括其头部信息、内容体等。
 */
@FunctionalInterface
public interface MultipartParser {
    /**
     * 解析给定的多部分MIME消息，将其分解为多个单独的MIME部分。
     *
     * @param message 要解析的多部分MIME消息
     * @return 包含解析后的各个MIME部分的列表
     * @throws IOException 如果在读取或解析过程中发生I/O错误
     */
    List<MimePart> parse(MultiMimePart message) throws IOException;

    /**
     * 返回一个遵循标准实现的多部分解析器实例。
     * <p>
     * 该方法提供了一个方便的方式来获取预定义配置的多部分MIME消息解析器，
     * 使用者无需手动创建或配置解析器对象。返回的实例是单例模式保证的唯一实例，
     * 确保了在应用程序中的高效资源利用和一致性。
     *
     * @return 标准配置下的 MultipartParser 实例
     */
    static MultipartParser standard() {
        return StandardMultipartParser.getInstance();
    }
}
