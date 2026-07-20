package io.github.jaloon.eml.io;

/**
 * 基于字节数组的 {@link MimeInputStream} 实现，直接引用原始字节数组（零拷贝）。
 * <p>
 * 适用于已将文件内容一次性读入内存的场景（如通过 {@code Files.readAllBytes()} 读取网络路径文件），
 * 避免 {@link java.io.RandomAccessFile} 对网络路径的逐字节 seek + read 性能瓶颈。
 * <p>
 * 设计特点：
 * <ul>
 *   <li><strong>零拷贝</strong>：{@link #newStream} 创建的子流共享原始字节数组，无数据复制开销</li>
 *   <li><strong>纯内存操作</strong>：所有读取、寻址、行扫描均在内存中完成，无 I/O 调用</li>
 *   <li><strong>支持 mark/reset</strong>：通过 {@link #mark}/{@link #reset} 支持回退读取</li>
 * </ul>
 *
 * @see MimeInputStream
 * @see io.github.jaloon.eml.EmlMessage#of(byte[]) 使用该流解析内存中的邮件
 */
public class ByteArrayMimeInputStream extends MimeInputStream {

    /** 原始字节数组引用（零拷贝，不复制数据） */
    private final byte[] data;
    /** 数据在原始数组中的起始偏移 */
    private final int offset;
    /** 数据长度（字节数） */
    private final int length;
    /** 当前读取位置（相对于 offset） */
    private int pos;
    /** mark 标记位置（相对于 offset） */
    private int mark;

    private static final byte CR = '\r';
    private static final byte LF = '\n';

    /**
     * 创建引用字节数组子范围的流。
     *
     * @param data   原始字节数组（不复制，直接引用）
     * @param offset 数据在数组中的起始偏移
     * @param length 数据长度
     */
    public ByteArrayMimeInputStream(byte[] data, int offset, int length) {
        this.data = data;
        this.offset = offset;
        this.length = length;
        this.pos = 0;
        this.mark = 0;
    }

    /**
     * 创建引用整个字节数组的流（便捷构造器）。
     *
     * @param data 原始字节数组
     */
    public ByteArrayMimeInputStream(byte[] data) {
        this(data, 0, data.length);
    }

    @Override
    public int read() {
        if (pos >= length) return -1;
        return data[offset + pos++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        if (pos >= length) return -1;
        int toRead = Math.min(len, length - pos);
        System.arraycopy(data, offset + pos, b, off, toRead);
        pos += toRead;
        return toRead;
    }

    /**
     * 创建一个零拷贝的子流，引用原始数组的 [start, end) 子范围。
     * <p>
     * 新流与当前流共享同一份字节数组，无数据复制。常用于将头部与 body 分离的场景。
     */
    @Override
    public MimeInputStream newStream(long start, long end) {
        int newOffset = offset + (int) start;
        int newLen = (int) (end - start);
        return new ByteArrayMimeInputStream(data, newOffset, newLen);
    }

    /** 将读取位置重置为相对于 offset 的指定偏移 */
    @Override
    public void seek(long offset) {
        this.pos = (int) offset;
    }

    /** 返回当前读取位置（相对于 offset） */
    @Override
    public long getPosition() {
        return pos;
    }

    /** 返回当前在原始数组中的绝对位置（offset + pos） */
    @Override
    public long getFilePointer() {
        return offset + pos;
    }

    /** 返回数据在原始数组中的起始偏移 */
    @Override
    public long getStart() {
        return offset;
    }

    /** 返回数据长度（字节数） */
    @Override
    public long getSize() {
        return length;
    }

    /**
     * 读取一行数据（不含行结束符），支持 \r\n、\r、\n 三种行结束格式。
     * 返回 null 表示已到达数据末尾。
     */
    @Override
    public String readLine() {
        if (pos >= length) return null;
        int lineStart = pos;
        while (pos < length && data[offset + pos] != CR && data[offset + pos] != LF) {
            pos++;
        }
        String line = bytesToString(data, offset + lineStart, offset + pos);
        if (pos < length && data[offset + pos] == CR) pos++;
        if (pos < length && data[offset + pos] == LF) pos++;
        return line;
    }

    @Override
    public int available() {
        return length - pos;
    }

    @Override
    public void mark(int readAheadLimit) {
        mark = pos;
    }

    @Override
    public void reset() {
        pos = mark;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void close() {
        // 无需释放资源，字节数组由 GC 回收
    }

    private static String bytesToString(byte[] data, int start, int end) {
        int len = end - start;
        if (len <= 0) return "";
        char[] chars = new char[len];
        for (int i = 0; i < len; i++) {
            chars[i] = (char) (data[start + i] & 0xFF);
        }
        return new String(chars);
    }
}
