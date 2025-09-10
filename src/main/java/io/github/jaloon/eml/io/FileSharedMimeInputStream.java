package io.github.jaloon.eml.io;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件共享MIME输入流，用于从文件中读取MIME类型的数据，并支持多个实例共享同一底层文件。
 * 此类扩展了 {@link MimeInputStream} 并提供了对文件内容的随机访问能力。
 */
class FileSharedMimeInputStream extends MimeInputStream {
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

    /**
     * 构造一个新的 MimeInputStream 实例，用于从指定文件读取MIME类型的数据。
     *
     * @param file 作为输入源的文件
     * @throws IOException 如果在打开文件或初始化流时发生I/O错误
     */
    public FileSharedMimeInputStream(File file) throws IOException {
        this(new RandomAccessFile(file, "r"), 0, file.length(), new AtomicInteger(1));
    }

    /**
     * 构造一个新的 MimeInputStream 实例，用于从指定的 RandomAccessFile 读取数据。
     *
     * @param in       作为输入源的 RandomAccessFile
     * @param start    流开始位置
     * @param size     流大小
     * @param refCount 引用计数，用于跟踪当前流实例的引用数量
     */
    private FileSharedMimeInputStream(RandomAccessFile in, long start, long size, AtomicInteger refCount) {
        this.in = in;
        this.mark = start;
        this.pos = start;
        this.start = start;
        this.size = size;
        this.closed = new AtomicBoolean();
        this.refCount = refCount;
    }

    /**
     * 创建一个新的MimeInputStream实例，用于从当前流中指定的范围内读取数据。
     *
     * @param start 要创建的新流的起始位置（包含），相对于当前流的开始位置，单位是字节。必须非负。
     * @param end   要创建的新流的结束位置（不包含），单位是字节。如果为-1，则新流将与此流在相同的位置结束。
     * @return 返回一个表示新创建的MimeInputStream实例，如果请求的范围无效或为空，则返回EmptyMimeInputStream实例。
     * @throws IOException 如果在尝试创建新流时发生I/O错误，或者当前流已被关闭。
     */
    public MimeInputStream newStream(long start, long end) throws IOException {
        ensureOpen();
        if (start < 0)
            throw new IllegalArgumentException("start < 0");
        if (end == -1)
            end = this.size;
        long size = end - start;
        if (size <= 0) {
            return EmptyMimeInputStream.getInstance();
        }
        this.refCount.getAndIncrement();
        return new FileSharedMimeInputStream(this.in, this.start + start, size, this.refCount);
    }

    /**
     * Check to make sure that this stream has not been closed
     */
    private void ensureOpen() throws IOException {
        if (in == null || closed.get())
            throw new IOException("Stream closed");
    }

    @Override
    public void seek(long offset) throws IOException {
        ensureOpen();
        pos = start + offset;
        in.seek(pos);
    }

    @Override
    public long getPosition() throws IOException {
        ensureOpen();
        return pos - start;
    }

    @Override
    public long getFilePointer() throws IOException {
        ensureOpen();
        return pos;
    }

    @Override
    public long getStart() {
        return start;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
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
     * 从当前流中读取下一个字节的数据。
     * 如果当前可用数据大于0，则移动文件指针到当前位置并读取一个字节。如果已到达流的末尾，则返回-1。
     *
     * @return 读取的下一个字节数据，或者如果没有更多数据可读则返回-1
     * @throws IOException 如果在读取过程中发生I/O错误
     */
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
        }
        if (len == 0) {
            return 0;
        }
        in.seek(pos);
        int read = in.read(b, off, Math.min(available(), len));
        if (read > 0) {
            pos += read;
        }
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

    /**
     * 重置当前流的位置到最近一次标记的位置。
     * 如果之前没有调用过 {@link #mark(int)} 方法或者已经超过了读取限制，那么此方法的行为是未定义的。
     * 在调用此方法之前会检查流是否已关闭，如果已关闭则抛出异常。
     *
     * @throws IOException 如果在尝试重置流位置时发生I/O错误，或者流已被关闭
     */
    @Override
    public synchronized void reset() throws IOException {
        ensureOpen();
        pos = mark;
    }

    /**
     * 检查此输入流是否支持标记功能。
     *
     * @return 总是返回 true，表示此流支持标记功能
     */
    @Override
    public boolean markSupported() {
        return true;
    }

    /**
     * 关闭此输入流并释放与此流相关的系统资源。如果底层输入流已经被关闭或当前引用计数为零，
     * 则不会执行任何操作。否则，将减少引用计数，并在引用计数达到零时实际关闭底层输入流。
     * 此方法是同步的，以确保线程安全。
     *
     * @throws IOException 如果在尝试关闭底层输入流时发生I/O错误
     */
    @Override
    public synchronized void close() throws IOException {
        if (in == null || closed.getAndSet(true)) return;
        if (refCount.decrementAndGet() <= 0) {
            in.close();
        }
    }

    /**
     * 强制关闭当前的输入流。此方法确保即使在引用计数不为零的情况下也会尝试关闭底层输入流。
     * 如果引用计数大于零，则正常关闭输入流，并且任何关闭过程中抛出的异常将被传播。
     * 如果引用计数已经为零（意味着流应该已经被关闭），则会再次尝试关闭输入流，但忽略任何可能发生的异常。
     *
     * @throws IOException 如果在引用计数大于零时尝试关闭输入流的过程中发生I/O错误
     */
    @Override
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
