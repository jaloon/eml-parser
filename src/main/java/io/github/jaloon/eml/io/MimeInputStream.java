package io.github.jaloon.eml.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * MimeInputStream 是一个抽象类，继承自 InputStream，用于处理 MIME 类型的输入流。
 * 它提供了额外的方法来控制和查询流的位置，并支持读取文本行。
 */
public abstract class MimeInputStream extends InputStream {
    /**
     * 返回一个空的 MimeInputStream 实例。
     *
     * @return 一个空的 MimeInputStream 实例
     */
    public static MimeInputStream empty() {
        return EmptyMimeInputStream.getInstance();
    }

    /**
     * 从给定的文件创建一个MimeInputStream实例。
     *
     * @param file 文件对象，表示要从中读取数据的文件
     * @return 一个MimeInputStream实例，用于处理指定文件中的MIME类型数据
     * @throws IOException 如果在尝试访问文件时发生I/O错误
     */
    public static MimeInputStream of(File file) throws IOException {
        return new FileSharedMimeInputStream(file);
    }

    /**
     * 创建一个新的MimeInputStream，该流从指定的开始位置到结束位置读取数据。
     *
     * @param start 流中开始读取的位置（包含），单位是字节
     * @param end   流中停止读取的位置（不包含），单位是字节
     * @return 一个新的MimeInputStream实例，用于从给定的[start, end)范围内读取数据
     * @throws IOException 如果在创建新流时发生I/O错误
     */
    public abstract MimeInputStream newStream(long start, long end) throws IOException;

    /**
     * 移动文件指针到相对于流开始位置的指定偏移量
     *
     * @param offset 文件偏移量，表示从流的开始处计算的字节偏移
     * @throws IOException 如果发生I/O错误
     */
    public abstract void seek(long offset) throws IOException;

    /**
     * 获取当前流的位置。
     *
     * @return 当前流中的位置，相对于流开始位置的偏移量，单位是字节
     * @throws IOException 如果发生I/O错误
     */
    public abstract long getPosition() throws IOException;

    /**
     * 获取当前文件指针位置
     *
     * @return 当前文件指针的位置，相对于文件开始位置的偏移量，单位是字节
     * @throws IOException 如果发生I/O错误
     */
    public abstract long getFilePointer() throws IOException;

    /**
     * 获取流的起始位置。
     *
     * @return 流的起始位置，单位是字节
     */
    public abstract long getStart();

    /**
     * 获取当前流的大小。
     *
     * @return 当前流的大小，单位是字节
     */
    public abstract long getSize();

    /**
     * 读取一行文本。一行被定义为从行终止符（\n、\r或\r\n）之前的所有字节。
     * 每个字节都转换为一个字符，方法是采用该字符的低八位字节值，并将该字符的高八位设置为零。
     * 因此，此方法不支持完整的 Unicode 字符集。
     *
     * @return 从此流中读取的一行文本，不包括任何行终止字符，如果已到达流的末尾，则返回 null
     * @throws IOException 如果发生I/O错误
     */
    public abstract String readLine() throws IOException;

    /**
     * 读取一行，并转换成指定编码
     *
     * @param charset 字符串编码
     * @return 此文件文本的下一行，如果连一个字节也没有读取就已到达文件的末尾，则返回 null。
     * @throws IOException 如果发生 I/O 错误
     */
    public String readLine(Charset charset) throws IOException {
        String line = readLine();
        if (line == null || charset == null || StandardCharsets.ISO_8859_1.equals(charset)) {
            return line;
        }
        return new String(line.getBytes(StandardCharsets.ISO_8859_1), charset);
    }

    /**
     * 强制关闭当前的输入流。此方法确保所有系统资源被释放，即使在异常情况下也尝试执行清理操作。
     *
     * @throws IOException 如果在关闭过程中发生I/O错误
     */
    public void forceClose() throws IOException {
        close();
    }

}
