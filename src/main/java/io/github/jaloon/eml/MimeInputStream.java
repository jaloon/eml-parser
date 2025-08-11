package io.github.jaloon.eml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mime 输入流
 */
public class MimeInputStream extends InputStream {
    /**
     * 包含数据的文件，由所有相关共享缓冲输入流共享。
     */
    protected final RandomAccessFile in;
    /**
     * 最后一次调用 mark 方法时 pos 字段的值。
     */
    protected long mark;
    /**
     * 此输入流在文件中的偏移量
     */
    protected long pos;
    /**
     * 文件中此子集数据开始处的文件偏移量。
     */
    protected long start;
    /**
     * 此文件子集中的数据量。
     */
    protected long size;
    /**
     * 是否关闭
     */
    private final AtomicBoolean closed;
    /**
     * 引用计数
     */
    private final AtomicInteger refCount;

    public MimeInputStream(File file) throws IOException {
        this(new RandomAccessFile(file, "r"), 0, file.length(), new AtomicInteger(1));
    }

    private MimeInputStream(RandomAccessFile in, long start, long size, AtomicInteger refCount) {
        this.in = in;
        this.mark = start;
        this.pos = start;
        this.start = start;
        this.size = size;
        this.closed = new AtomicBoolean();
        this.refCount = refCount;
    }

    /**
     * Check to make sure that this stream has not been closed
     */
    private void ensureOpen() throws IOException {
        if (in == null || closed.get())
            throw new IOException("Stream closed");
    }

    public void seek(long offset) throws IOException {
        ensureOpen();
        pos = start + offset;
        in.seek(pos);
    }

    /**
     * Return the current position in the InputStream, as an
     * offset from the beginning of the InputStream.
     *
     * @return the current position
     */
    public long getPosition() throws IOException {
        ensureOpen();
        return pos - start;
    }

    public long getFilePointer() throws IOException {
        ensureOpen();
        return pos;
    }

    public long getStart() {
        return start;
    }

    public long getSize() {
        return size;
    }

    /**
     * Return a new InputStream representing a subset of the data
     * from this InputStream, starting at <code>start</code> (inclusive)
     * up to <code>end</code> (exclusive).  <code>start</code> must be
     * non-negative.  If <code>end</code> is -1, the new stream ends
     * at the same place as this stream.
     *
     * @param start the starting position
     * @param end   the ending position + 1
     * @return the new stream
     */
    public MimeInputStream newStream(long start, long end) throws IOException {
        ensureOpen();
        if (start < 0)
            throw new IllegalArgumentException("start < 0");
        if (end == -1)
            end = size;
        refCount.getAndIncrement();
        return new MimeInputStream(in, this.start + start, end - start, refCount);
    }

    /**
     * Reads a line of text.  A line is considered to be terminated by any one
     * of a line feed ('\n'), a carriage return ('\r'), or a carriage return
     * followed immediately by a linefeed.
     * <p>
     * 每个字节都转换为一个字符，方法是采用该字符的低八位字节值，并将该字符的高八位设置为零。
     * 因此，此方法不支持完整的 Unicode 字符集。
     * </p>
     *
     * @return A String containing the contents of the line, not including
     * any line-termination characters, or null if the end of the
     * stream has been reached
     * @throws IOException If an I/O error occurs
     */
    public synchronized String readLine() throws IOException {
        ensureOpen();
        long end = start + size;
        long available = end - pos;
        if (available <= 0) {
            return null;
        }
        in.seek(pos);
        String line = in.readLine();
        if (line == null) {
            return null;
        }
        long pointer = in.getFilePointer();
        if (pointer <= end) {
            pos = pointer;
            return line;
        }
        pos = end;
        return line.substring(0, (int) available);
    }

    /**
     * 读取一行，并转换成指定编码
     *
     * @param charset 字符串编码
     * @return 此文件文本的下一行，如果连一个字节也没有读取就已到达文件的末尾，则返回 null。
     * @throws IOException 如果发生 I/O 错误
     */
    public synchronized String readLine(Charset charset) throws IOException {
        String line = readLine();
        if (line == null || charset == null || StandardCharsets.ISO_8859_1.equals(charset)) {
            return line;
        }
        return new String(line.getBytes(StandardCharsets.ISO_8859_1), charset);
    }

    @Override
    public synchronized int read() throws IOException {
        if (available() > 0) {
            in.seek(pos++);
            return in.read();
        }
        return -1;
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        if ((off | len | (off + len) | (b.length - (off + len))) < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }
        in.seek(pos);
        int read = in.read(b, off, Math.min(available(), len));
        pos += read;
        return read;
    }

    @Override
    public synchronized long skip(long n) throws IOException {
        ensureOpen();
        if (n <= 0) {
            return 0;
        }
        long available = start + size - pos;
        if (available <= 0) {
            return 0;
        }
        long skipped = Math.min(available, n);
        pos += skipped;
        in.seek(pos);
        return skipped;
    }

    @Override
    public int available() throws IOException {
        ensureOpen();
        return (int) (start + size - pos);
    }

    /**
     * Set the current marked position in the stream.
     * <p> Note: The <code>readAheadLimit</code> for this class has no meaning.
     */
    @Override
    public synchronized void mark(int readAheadLimit) {
        mark = pos;
    }

    @Override
    public synchronized void reset() throws IOException {
        ensureOpen();
        pos = mark;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void close() throws IOException {
        if (in == null || closed.getAndSet(true)) return;
        if (refCount.decrementAndGet() <= 0) {
            in.close();
        }
    }

    public synchronized void forceClose() throws IOException {
        if (in == null) return;
        if (refCount.getAndSet(0) > 0) {
            // normal case, close exceptions propagated
            in.close();
        } else {
            // should already be closed, ignore exception
            try {
                in.close();
            } catch (IOException ignored) {}
        }
    }

}
