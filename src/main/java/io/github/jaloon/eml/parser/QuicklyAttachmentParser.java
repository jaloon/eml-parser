package io.github.jaloon.eml.parser;

import io.github.jaloon.eml.io.ByteArrayMimeInputStream;
import io.github.jaloon.eml.io.MimeInputStream;
import io.github.jaloon.eml.part.AttachmentPart;
import io.github.jaloon.eml.part.MimePart;
import io.github.jaloon.eml.part.MultiMimePart;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * QuicklyAttachmentParser 是一个高性能附件提取解析器，专注于从多部分 MIME 消息中快速提取附件部分。
 * <p>
 * 核心优化策略（参考 JavaMail 的 MimeMultipart 实现）：
 * <ol>
 *   <li>将整个 multipart body 一次性读入内存字节数组（{@link #readAllBytes}），
 *       避免大量 seek + 逐字节 read 的 I/O 开销</li>
 *   <li>使用字节级边界扫描（{@link #findNextBoundary}）替代 readLine 逐行扫描，
 *       将 I/O 操作从 O(行数) 降低到 O(数据量/缓冲区大小)</li>
 *   <li>仅解析每个 part 的头部信息（通常几百字节），跳过 body 内容</li>
 *   <li>使用轻量级头部扫描（{@link #scanHeaders}）替代完整的
 *       {@link MimePart#parseHeaders(MimeInputStream)} 调用，
 *       避免为非附件部分创建 ArrayList 和 String 对象</li>
 *   <li>支持嵌套 multipart 递归处理，限定递归范围避免重复扫描</li>
 * </ol>
 * <p>
 * 非标准格式兼容性：
 * <ul>
 *   <li>boundary 不以换行开头（如 {@code </html>--boundary}）：
 *       {@link #findNextBoundary} 不要求 boundary 前必须是 LF/CR</li>
 *   <li>缺少结束边界 {@code --boundary--}：当找不到下一个 boundary 时，
 *       将剩余数据作为最后一个 part 处理</li>
 *   <li>RFC 2822 折叠头部：{@link #scanHeaders} 自动拼接以空格/tab 开头的续行</li>
 *   <li>RFC 2231 编码参数：{@link MimePart#getHeadItem} 支持 {@code filename*} 多段编码</li>
 * </ul>
 *
 * @see MultipartParser#quickly() 工厂方法
 * @see StandardMultipartParser 标准解析器（完整解析所有 part）
 */
public class QuicklyAttachmentParser implements MultipartParser {

    /** 读取缓冲区大小，用于无法预知大小时的回退读取策略 */
    private static final int BUFFER_SIZE = 65536;
    /** 回车符 \r，MIME 规范中行结束符的一部分 */
    private static final byte CR = '\r';
    /** 换行符 \n，MIME 规范中行结束符的一部分 */
    private static final byte LF = '\n';

    /** 单例实例，使用双重检查锁定保证线程安全 */
    private static volatile QuicklyAttachmentParser instance;

    public static QuicklyAttachmentParser getInstance() {
        if (instance == null) {
            synchronized (QuicklyAttachmentParser.class) {
                if (instance == null) {
                    instance = new QuicklyAttachmentParser();
                }
            }
        }
        return instance;
    }

    private QuicklyAttachmentParser() {}

    /**
     * 解析 multipart 消息并提取附件列表。
     * <p>
     * 解析流程：
     * <ol>
     *   <li>校验是否为 multipart 消息且含有 boundary</li>
     *   <li>将整个 body 一次性读入内存字节数组（{@link #readAllBytes}）</li>
     *   <li>调用 {@link #parseRange} 在字节数组中扫描 boundary 并提取附件</li>
     * </ol>
     *
     * @param message 待解析的 multipart 消息
     * @return 提取到的附件列表，若无附件则返回空列表
     * @throws IOException 读取 body 数据时发生 I/O 错误
     */
    @Override
    public List<MimePart> parse(MultiMimePart message) throws IOException {
        if (!message.isMultipart()) {
            return Collections.emptyList();
        }
        String boundary = message.getBoundary();
        if (boundary == null) {
            return Collections.emptyList();
        }
        byte[] data = readAllBytes(message.getBody());
        List<MimePart> attachments = new ArrayList<>();
        parseRange(data, 0, data.length, boundary, attachments);
        return attachments;
    }

    /**
     * 将 MimeInputStream 的全部内容读入字节数组。
     * <p>
     * 提供两种读取策略：
     * <ul>
     *   <li>已知大小（size > 0）：按精确大小分配数组，一次读取到位</li>
     *   <li>未知大小（size <= 0）：使用 {@link ByteArrayOutputStream} 动态扩展读取</li>
     * </ul>
     *
     * @param in 待读取的 MIME 输入流
     * @return 包含流全部内容的字节数组
     * @throws IOException 读取过程中发生 I/O 错误
     */
    private static byte[] readAllBytes(MimeInputStream in) throws IOException {
        long size = in.getSize();
        if (size > 0 && size <= Integer.MAX_VALUE) {
            byte[] data = new byte[(int) size];
            in.seek(0);
            int offset = 0;
            int remaining = data.length;
            while (remaining > 0) {
                int read = in.read(data, offset, remaining);
                if (read < 0) break;
                offset += read;
                remaining -= read;
            }
            if (offset < data.length) {
                byte[] trimmed = new byte[offset];
                System.arraycopy(data, 0, trimmed, 0, offset);
                return trimmed;
            }
            return data;
        }
        in.seek(0);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buf)) > 0) {
            baos.write(buf, 0, read);
        }
        return baos.toByteArray();
    }

    // ===== 核心解析逻辑 =====

    /**
     * 在字节数组的 [from, to) 范围内扫描 multipart boundary，逐个提取附件。
     * <p>
     * 扫描流程：
     * <ol>
     *   <li>查找第一个 {@code --boundary} 作为起始标记，跳过 preamble 区域</li>
     *   <li>循环查找下一个 {@code --boundary}，每两个 boundary 之间为一个 part</li>
     *   <li>调用 {@link #processPart} 判断 part 类型（嵌套 multipart / 附件 / 正文）</li>
     *   <li>遇到 {@code --boundary--} 结束标记时停止扫描</li>
     * </ol>
     * <p>
     * 兼容性说明：
     * <ul>
     *   <li>某些邮件缺少结束边界 {@code --boundary--}，当 {@link #findNextBoundary} 返回 -1 时，
     *       将剩余数据作为最后一个 part 处理，与 {@link StandardMultipartParser} 行为一致</li>
     *   <li>某些邮件的 boundary 不以换行开头（如 {@code </html>--boundary}），
     *       {@link #findNextBoundary} 不要求 boundary 前必须是 LF/CR</li>
     * </ul>
     *
     * @param data       包含 multipart body 的字节数组
     * @param from       扫描起始偏移（含）
     * @param to         扫描结束偏移（不含）
     * @param boundary   当前层级的 boundary 字符串（不含前缀 {@code --}）
     * @param attachments 附件收集列表
     */
    private void parseRange(byte[] data, int from, int to, String boundary, List<MimePart> attachments) {
        byte[] bStart = ("--" + boundary).getBytes();
        byte[] bEnd = ("--" + boundary + "--").getBytes();
        int bStartLen = bStart.length;

        int firstBoundary = indexOf(data, bStart, from, to);
        if (firstBoundary < 0) {
            // 未找到起始 boundary，可能是数据损坏或 boundary 不匹配，直接返回
            return;
        }

        // 跳过起始 boundary 行，pos 定位到第一个 part 的起始位置
        int pos = firstBoundary + bStartLen;
        pos = skipLineEnd(data, pos, to);

        while (pos < to) {
            // 在当前 part 之后查找下一个 boundary
            int nextBoundary = findNextBoundary(data, pos, to, bStart);
            if (nextBoundary < 0) {
                // 未找到后续 boundary（包括结束边界 --boundary--），
                // 说明邮件缺少结束标记（非标准格式，常见于某些邮件客户端导出的 EML）。
                // 将 [pos, to) 剩余数据作为最后一个 part 处理，
                // 与 StandardMultipartParser 在 readLine 返回 null 时处理最后一个 part 的行为一致。
                processPart(data, pos, to, attachments);
                break;
            }

            // 回退 boundary 前的行结束符（\r\n 或 \n），得到 part 内容的精确结束位置
            int partEnd = trimLineEnd(data, nextBoundary);
            // 检查是否为结束边界 --boundary--（在 --boundary 后紧跟 --）
            boolean isEnd = matchesAt(data, nextBoundary, bEnd);

            // 仅在 part 有实际内容时才处理（排除空 part 和连续 boundary）
            if (partEnd > pos) {
                processPart(data, pos, partEnd, attachments);
            }

            if (isEnd) {
                break;
            }

            // 跳过当前 boundary 行，pos 定位到下一个 part 的起始位置
            pos = nextBoundary + bStartLen;
            pos = skipLineEnd(data, pos, to);
        }
    }

    /**
     * 处理单个 MIME part：根据头部信息判断类型，执行相应操作。
     * <p>
     * 决策优先级：
     * <ol>
     *   <li>{@code multipart/*} 类型 → 递归调用 {@link #parseRange} 处理嵌套子 part</li>
     *   <li>{@code Content-Disposition: attachment} → 构造 {@link AttachmentPart} 加入结果列表</li>
     *   <li>其他（正文等） → 直接跳过，不创建任何对象</li>
     * </ol>
     *
     * @param data        字节数组
     * @param partStart   part 内容起始偏移（含头部）
     * @param partEnd     part 内容结束偏移（不含下一个 boundary）
     * @param attachments 附件收集列表
     */
    private void processPart(byte[] data, int partStart, int partEnd, List<MimePart> attachments) {
        HeaderInfo info = scanHeaders(data, partStart, partEnd);

        if (info.isMultipart && info.boundary != null) {
            // 嵌套 multipart：定位 body 起始位置后递归解析子层级
            int bodyStart = findBodyStart(data, partStart, partEnd);
            if (bodyStart < partEnd) {
                parseRange(data, bodyStart, partEnd, info.boundary, attachments);
            }
        } else if (info.isAttachment) {
            // 附件 part：完整解析头部并构造 AttachmentPart
            int bodyStart = findBodyStart(data, partStart, partEnd);
            List<String> headers = parseHeadersFromBytes(data, partStart, bodyStart);
            // filename 优先从 Content-Disposition 提取，回退到 Content-Type 的 name 参数
            String filename = info.filename;
            if (StringUtils.isBlank(filename) && StringUtils.isNotBlank(info.contentType)) {
                filename = MimePart.getHeadItem(info.contentType, "name");
            }
            MimeInputStream bodyStream = createBodyStream(data, bodyStart, partEnd);
            attachments.add(new AttachmentPart(filename, headers, bodyStream));
        }
        // 非 multipart 且非附件的 part（如 text/html 正文）直接跳过
    }

    // ===== 轻量级头部扫描 =====

    /**
     * 轻量级头部扫描结果，仅提取判断 part 类型所需的最少信息。
     * <p>
     * 相比完整的 {@link MimePart#parseHeaders(MimeInputStream)}，
     * 此结构避免了为非附件 part 创建 ArrayList 和大量 String 对象。
     */
    private static class HeaderInfo {
        /** Content-Type 头部值（不含前缀），用于判断 multipart 和回退提取 name */
        String contentType;
        /** multipart 的 boundary 参数值 */
        String boundary;
        /** Content-Disposition 中提取的 filename */
        String filename;
        /** 是否为 multipart/* 类型 */
        boolean isMultipart;
        /** 是否含有 Content-Disposition: attachment */
        boolean isAttachment;
    }

    /**
     * 轻量级头部扫描：逐行扫描 part 头部，仅提取 Content-Type 和 Content-Disposition。
     * <p>
     * 关键设计：
     * <ul>
     *   <li>遇到空行（头部与 body 的分隔符）立即停止扫描</li>
     *   <li>Content-Type 为 multipart/* 时不提前 return，继续扫描以检查是否存在
     *       Content-Disposition: attachment（某些邮件的嵌套 multipart 同时含有两者）</li>
     *   <li>Content-Disposition 可能跨多行（RFC 2822 折叠头部，续行以空格或 tab 开头），
     *       先完整拼接所有折叠行，再统一提取 filename 参数，避免续行上的 filename 被遗漏</li>
     * </ul>
     *
     * @param data  字节数组
     * @param start 头部起始偏移
     * @param end   头部结束偏移（part 内容结束位置，含 body）
     * @return 扫描结果
     */
    private static HeaderInfo scanHeaders(byte[] data, int start, int end) {
        HeaderInfo info = new HeaderInfo();
        int pos = start;
        // 延迟提取 filename，等折叠头部完整拼接后再提取
        String fullDispLine = null;

        while (pos < end) {
            int lineEnd = findLineEnd(data, pos, end);
            if (lineEnd == pos) {
                break;
            }

            int nextLineStart = skipLineEnd(data, lineEnd, end);
            int lineLen = lineEnd - pos;

            if (lineLen > 14 && startsWith(data, pos, "Content-Type:")) {
                String ct = bytesToString(data, pos + 13, lineEnd).trim();
                info.contentType = ct;
                if (ct.startsWith("multipart/")) {
                    info.isMultipart = true;
                    info.boundary = MimePart.getHeadItem(ct, "boundary");
                    // 不再提前 return，继续扫描后续头部以检查 Content-Disposition
                }
            } else if (lineLen > 31 && startsWith(data, pos, "Content-Disposition: attachment")) {
                info.isAttachment = true;
                // 拼接折叠头部（续行以空格或 tab 开头）
                StringBuilder dispLine = new StringBuilder(bytesToString(data, pos, Math.min(nextLineStart, end)));
                nextLineStart = getNextLineStart(data, end, nextLineStart, dispLine);
                fullDispLine = dispLine.toString();
            }

            pos = nextLineStart;
        }

        // 折叠头部完整拼接后再提取 filename，避免续行上的 filename 被遗漏
        if (fullDispLine != null) {
            info.filename = MimePart.getHeadItem(fullDispLine, "filename");
        }

        return info;
    }

    /**
     * 从字节数组的 [start, end) 范围完整解析头部行列表（仅用于附件 part）。
     * <p>
     * 支持 RFC 2822 折叠头部：续行以空格或 tab 开头时，拼接到上一行。
     * 遇到空行（连续两个行结束符）停止解析。
     *
     * @param data  字节数组
     * @param start 头部起始偏移
     * @param end   扫描上限（通常为 body 起始位置）
     * @return 解析后的头部行列表，每行为一个完整字符串（含折叠续行）
     */
    private static List<String> parseHeadersFromBytes(byte[] data, int start, int end) {
        List<String> headers = new ArrayList<>();
        int pos = start;
        while (pos < end) {
            int lineEnd = findLineEnd(data, pos, end);
            if (lineEnd == pos) {
                break;
            }
            StringBuilder line = new StringBuilder(bytesToString(data, pos, lineEnd));
            int nextPos = skipLineEnd(data, lineEnd, end);
            nextPos = getNextLineStart(data, end, nextPos, line);
            headers.add(line.toString());
            pos = nextPos;
        }
        return headers;
    }

    /**
     * 获取下一行的起始偏移，支持 RFC 2822 头部折叠（续行以空格或 tab 开头）。
     *
     * @param data          字节数组
     * @param end           扫描上限
     * @param nextLineStart 下一行的起始偏移
     * @param dispLine      当前头部行，用于拼接折叠行
     * @return 下一行的起始偏移
     */
    private static int getNextLineStart(byte[] data, int end, int nextLineStart, StringBuilder dispLine) {
        while (nextLineStart < end && (data[nextLineStart] == ' ' || data[nextLineStart] == '\t')) {
            int foldEnd = findLineEnd(data, nextLineStart, end);
            dispLine.append("\n").append(bytesToString(data, nextLineStart, foldEnd));
            nextLineStart = skipLineEnd(data, foldEnd, end);
        }
        return nextLineStart;
    }

    /**
     * 定位 MIME part 头部的结束位置（即 body 的起始位置）。
     * <p>
     * MIME 规范中，头部与 body 由一个空行分隔（连续两个行结束符）。
     * 此方法从头扫描，找到第一个空行后返回 body 起始偏移。
     *
     * @param data  字节数组
     * @param start 扫描起始偏移
     * @param end   扫描上限
     * @return body 起始偏移；若未找到空行则返回 end
     */
    private static int findBodyStart(byte[] data, int start, int end) {
        int pos = start;
        while (pos < end) {
            int lineEnd = findLineEnd(data, pos, end);
            if (lineEnd == pos) {
                return skipLineEnd(data, lineEnd, end);
            }
            pos = skipLineEnd(data, lineEnd, end);
        }
        return end;
    }

    // ===== 字节数组操作工具方法 =====

    /**
     * 在字节数组的 [from, to) 范围内查找模式首次出现的位置。
     * 使用暴力匹配算法，适用于 boundary 模式的查找。
     *
     * @param data    待搜索的字节数组
     * @param pattern 待匹配的模式字节数组
     * @param from    搜索起始偏移（含）
     * @param to      搜索结束偏移（不含）
     * @return 匹配起始偏移，未找到返回 -1
     */
    private static int indexOf(byte[] data, byte[] pattern, int from, int to) {
        int limit = to - pattern.length;
        outer:
        for (int i = from; i <= limit; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /**
     * 检查字节数组在指定偏移处是否与模式完全匹配。
     *
     * @param data    待检查的字节数组
     * @param offset  匹配起始偏移
     * @param pattern 待匹配的模式字节数组
     * @return 完全匹配返回 true
     */
    private static boolean matchesAt(byte[] data, int offset, byte[] pattern) {
        if (offset + pattern.length > data.length) return false;
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) return false;
        }
        return true;
    }

    /**
     * 检查字节数组在指定偏移处是否以给定 ASCII 前缀开头。
     * 仅支持 ASCII 字符的前缀比较。
     *
     * @param data   待检查的字节数组
     * @param offset 检查起始偏移
     * @param prefix ASCII 前缀字符串
     * @return 匹配返回 true
     */
    private static boolean startsWith(byte[] data, int offset, String prefix) {
        if (offset + prefix.length() > data.length) return false;
        for (int i = 0; i < prefix.length(); i++) {
            if (data[offset + i] != (byte) prefix.charAt(i)) return false;
        }
        return true;
    }

    /**
     * 在字节数组的 [from, to) 范围内查找下一个 boundary 出现位置。
     * <p>
     * 匹配模式为 {@code --boundary}（RFC 2046 规定的 boundary 前缀）。
     * <p>
     * <strong>不要求 boundary 前必须是 LF/CR</strong>：某些非标准邮件中，
     * boundary 可能紧跟在前一个 part 的内容后面（如 {@code </html>--boundary}），
     * {@link StandardMultipartParser} 通过 {@code line.endsWith(boundary)} 处理了这种情况。
     * 由于 boundary 模式通常足够长（30+ 字节），在正文内容中不会产生误匹配。
     *
     * @param data   字节数组
     * @param from   搜索起始偏移
     * @param to     搜索结束偏移
     * @param bStart boundary 前缀字节数组（{@code --boundary}）
     * @return boundary 起始偏移，未找到返回 -1
     */
    private static int findNextBoundary(byte[] data, int from, int to, byte[] bStart) {
        int limit = to - bStart.length;
        for (int i = from; i <= limit; i++) {
            // 快速跳过首字节不匹配的位置，减少 matchesAt 调用次数
            if (data[i] != bStart[0]) {
                continue;
            }
            if (matchesAt(data, i, bStart)) {
                // 不要求 boundary 前面必须是 LF/CR。
                // 某些邮件中 boundary 可能紧跟在前一个 part 的内容后面（如 </html>--boundary），
                // StandardMultipartParser 通过 line.endsWith(boundary) 处理了这种情况。
                // 由于 boundary 模式足够长（通常 30+ 字节），不会产生误匹配。
                return i;
            }
        }
        return -1;
    }

    /**
     * 查找行结束符的位置（CR 或 LF 首次出现的位置）。
     *
     * @param data 字节数组
     * @param pos  搜索起始偏移
     * @param end  搜索结束偏移
     * @return 行结束符的偏移位置；若未找到则返回 end
     */
    private static int findLineEnd(byte[] data, int pos, int end) {
        for (int i = pos; i < end; i++) {
            if (data[i] == CR || data[i] == LF) {
                return i;
            }
        }
        return end;
    }

    /**
     * 跳过行结束符序列，支持 \r\n、\r、\n 三种格式。
     *
     * @param data 字节数组
     * @param pos  行结束符起始偏移
     * @param end  数组边界
     * @return 跳过行结束符后的下一个位置
     */
    private static int skipLineEnd(byte[] data, int pos, int end) {
        if (pos >= end) return pos;
        if (data[pos] == CR) {
            pos++;
            if (pos < end && data[pos] == LF) {
                pos++;
            }
            return pos;
        }
        if (data[pos] == LF) {
            return pos + 1;
        }
        return pos;
    }

    /**
     * 回退 boundary 位置前的行结束符（\r\n 或 \n），得到 part 内容的精确结束位置。
     * <p>
     * MIME 规范中 boundary 前的 \r\n 属于分隔符而非 part 内容，需要排除。
     *
     * @param data        字节数组
     * @param boundaryPos boundary 的起始偏移
     * @return part 内容的精确结束偏移（不含行结束符）
     */
    private static int trimLineEnd(byte[] data, int boundaryPos) {
        int pos = boundaryPos;
        if (pos > 0 && data[pos - 1] == LF) pos--;
        if (pos > 0 && data[pos - 1] == CR) pos--;
        return pos;
    }

    /**
     * 将字节数组的指定范围转换为 ISO-8859-1 字符串。
     * MIME 头部默认使用 ISO-8859-1 编码，通过 {@code & 0xFF} 无符号转换保证正确性。
     */
    private static String bytesToString(byte[] data, int start, int end) {
        int len = end - start;
        if (len <= 0) return "";
        char[] chars = new char[len];
        for (int i = 0; i < len; i++) {
            chars[i] = (char) (data[start + i] & 0xFF);
        }
        return new String(chars);
    }

    /**
     * 为附件 part 的 body 创建零拷贝的 {@link ByteArrayMimeInputStream}。
     * 直接引用原始字节数组的子范围，无数据复制开销。
     *
     * @param data  字节数组
     * @param start body 起始偏移
     * @param end   body 结束偏移
     * @return 指向 body 区域的 MimeInputStream；若范围为空则返回空流
     */
    private static MimeInputStream createBodyStream(byte[] data, int start, int end) {
        int len = end - start;
        if (len <= 0) {
            return MimeInputStream.empty();
        }
        return new ByteArrayMimeInputStream(data, start, len);
    }

}
