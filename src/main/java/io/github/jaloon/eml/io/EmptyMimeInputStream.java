package io.github.jaloon.eml.io;

import java.io.IOException;

/**
 * EmptyMimeInputStream 是 MimeInputStream 的一个具体实现，代表一个空的 MIME 输入流。
 * 该类的所有方法都返回表示空或默认值的结果，适用于需要空输入流的场景。
 */
class EmptyMimeInputStream extends MimeInputStream {
    /**
     * 代表 EmptyMimeInputStream 类的唯一实例，用于实现单例模式。
     * 使用 volatile 关键字确保了多线程环境下的可见性，使得一个线程对该变量的修改能够立即被其他线程看到。
     * 这个实例应该通过调用 {@link #getInstance()} 方法来获取。
     */
    private static volatile EmptyMimeInputStream instance;

    /**
     * 获取 EmptyMimeInputStream 的单例实例。
     *
     * @return 返回 EmptyMimeInputStream 类的唯一实例
     */
    public static EmptyMimeInputStream getInstance() {
        if (instance == null) {
            synchronized (EmptyMimeInputStream.class) {
                if (instance == null) {
                    instance = new EmptyMimeInputStream();
                }
            }
        }
        return instance;
    }

    /**
     * 私有构造函数，用于创建EmptyMimeInputStream的实例。
     * 该构造函数是私有的，以确保类的单例模式。用户应通过调用getInstance()方法来获取此类的唯一实例。
     */
    private EmptyMimeInputStream() {}

    /**
     * 创建一个新的MimeInputStream实例，该实例从指定的开始位置到结束位置读取数据。
     *
     * @param start 流中开始读取的位置（包含），单位是字节
     * @param end   流中停止读取的位置（不包含），单位是字节
     * @return 返回当前EmptyMimeInputStream实例，因为此实现总是返回自身
     * @throws IOException 如果在创建新流时发生I/O错误
     */
    @Override
    public MimeInputStream newStream(long start, long end) throws IOException {
        return this;
    }

    /**
     * 移动文件指针到指定位置。
     *
     * @param offset 文件偏移量，表示从流的开始处计算的字节偏移。对于空的 MIME 输入流，此方法不做任何操作。
     * @throws IOException 如果发生I/O错误
     */
    @Override
    public void seek(long offset) throws IOException {

    }

    /**
     * 获取当前流的位置。
     *
     * @return 当前流中的位置，单位是字节。对于空的 MIME 输入流，此方法始终返回 0。
     * @throws IOException 如果发生I/O错误
     */
    @Override
    public long getPosition() throws IOException {
        return 0;
    }

    /**
     * 获取当前文件指针的位置。
     *
     * @return 当前文件指针的位置，对于空的 MIME 输入流，此方法始终返回 0。
     * @throws IOException 如果发生I/O错误
     */
    @Override
    public long getFilePointer() throws IOException {
        return 0;
    }

    /**
     * 获取流的起始位置。
     *
     * @return 流的起始位置，单位是字节。对于空的 MIME 输入流，此方法始终返回 0。
     */
    @Override
    public long getStart() {
        return 0;
    }

    /**
     * 获取当前流的总大小。
     *
     * @return 当前流的总大小，单位是字节。对于空的 MIME 输入流，此方法始终返回 0。
     */
    @Override
    public long getSize() {
        return 0;
    }

    /**
     * 读取一行文本。对于空的 MIME 输入流，此方法总是返回 null。
     *
     * @return 从此流中读取的一行文本，不包括任何行终止字符，如果已到达流的末尾，则返回 null
     * @throws IOException 如果发生I/O错误
     */
    @Override
    public String readLine() throws IOException {
        return null;
    }

    /**
     * 从当前流中读取下一个字节的数据。
     * 对于空的 MIME 输入流，此方法总是返回 -1 表示已到达流的末尾。
     *
     * @return 返回下一个字节的数据，如果已到达流的末尾则返回 -1
     * @throws IOException 如果发生I/O错误
     */
    @Override
    public int read() throws IOException {
        return -1;
    }

    /**
     * 从当前流中读取最多 {@code len} 个字节的数据，并将其存储到给定的缓冲区数组 {@code b} 中，开始存储的位置由 {@code off} 指定。
     * 对于空的 MIME 输入流，此方法总是返回 -1 表示已到达流的末尾。
     *
     * @param b   目标缓冲区
     * @param off 缓冲区中的起始偏移量
     * @param len 要尝试读取的最大字节数
     * @return 实际读取的字节数；如果已经到达流的末尾，则返回 -1
     * @throws IOException 如果发生I/O错误
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return -1;
    }

    /**
     * 跳过并丢弃输入流中的下一个 n 个字节。
     *
     * @param n 要跳过的字节数
     * @return 实际跳过的字节数。对于空的 MIME 输入流，此方法总是返回 0L。
     * @throws IOException 如果发生I/O错误
     */
    @Override
    public long skip(long n) throws IOException {
        return 0L;
    }

}
