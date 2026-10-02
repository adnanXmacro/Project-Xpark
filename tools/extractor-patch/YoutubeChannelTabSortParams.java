package org.schabi.newpipe.extractor.services.youtube.extractors;

import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * InnerTube helpers for YouTube channel tabs.
 *
 * <p>Videos Latest still uses browse params. Popular (sort varint 2) and
 * Oldest (sort varint 5) are videos-shelf continuations because WEB browse
 * params either select the Home tab or return {@code INVALID_ARGUMENT}.
 */
public final class YoutubeChannelTabSortParams {

    public static final String POPULAR = "popular";
    public static final String OLDEST = "oldest";

    public static final String BROWSE_URL =
            "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false";

    static final String VIDEOS_LATEST = "EgZ2aWRlb3PyBgQKAjoA";
    static final String VIDEOS_POPULAR = "EgZ2aWRlb3PyBgQKAjIA";

    private static final String ZERO_UUID = "00000000-0000-0000-0000-000000000000";
    private static final char[] B64URL =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
                    .toCharArray();

    private YoutubeChannelTabSortParams() {
    }

    public static String params(final String tabName, final String sortFilter)
            throws ParsingException {
        if (ChannelTabs.VIDEOS.equals(tabName)) {
            if (POPULAR.equals(sortFilter)) {
                return VIDEOS_POPULAR;
            }
            if (OLDEST.equals(sortFilter)) {
                throw new ParsingException(
                        "Oldest videos use continuation, not browse params");
            }
            return VIDEOS_LATEST;
        }
        if (ChannelTabs.SHORTS.equals(tabName)) {
            return "EgZzaG9ydHPyBgUKA5oBAA%3D%3D";
        }
        if (ChannelTabs.LIVESTREAMS.equals(tabName)) {
            return "EgdzdHJlYW1z8gYECgJ6AA%3D%3D";
        }
        if (ChannelTabs.ALBUMS.equals(tabName)) {
            return "EghyZWxlYXNlc_IGBQoDsgEA";
        }
        if (ChannelTabs.PLAYLISTS.equals(tabName)) {
            return "EglwbGF5bGlzdHPyBgQKAkIA";
        }
        throw new ParsingException("Unsupported channel tab: " + tabName);
    }

    /**
     * First-page continuation token for a channel videos shelf.
     * {@link #OLDEST} is sort varint 5 (Invidious {@code make_initial_videos_ctoken}).
     */
    public static String videosContinuation(final String channelId,
            final String sortFilter) {
        final int sort;
        if (POPULAR.equals(sortFilter)) {
            sort = 2;
        } else if (OLDEST.equals(sortFilter)) {
            sort = 5;
        } else {
            sort = 4;
        }
        return wrapContinuation(channelId, videosInner(sort));
    }

    private static byte[] videosInner(final int sort) {
        final byte[] uuid = ZERO_UUID.getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream obj8 = new ByteArrayOutputStream();
        writeBytes(obj8, 1, uuid);
        writeVarintField(obj8, 3, sort);
        final ByteArrayOutputStream obj15 = new ByteArrayOutputStream();
        writeBytes(obj15, 8, obj8.toByteArray());
        final ByteArrayOutputStream obj3 = new ByteArrayOutputStream();
        writeBytes(obj3, 15, obj15.toByteArray());
        final ByteArrayOutputStream obj110 = new ByteArrayOutputStream();
        writeBytes(obj110, 3, obj3.toByteArray());
        final ByteArrayOutputStream inner = new ByteArrayOutputStream();
        writeBytes(inner, 110, obj110.toByteArray());
        return inner.toByteArray();
    }

    private static String wrapContinuation(final String channelId,
            final byte[] inner) {
        final String innerEnc = percentEncodeAlnum(base64UrlNoPad(inner));
        final ByteArrayOutputStream wrap = new ByteArrayOutputStream();
        writeBytes(wrap, 2, channelId.getBytes(StandardCharsets.UTF_8));
        writeBytes(wrap, 3, innerEnc.getBytes(StandardCharsets.UTF_8));
        final ByteArrayOutputStream outer = new ByteArrayOutputStream();
        writeBytes(outer, 80226972, wrap.toByteArray());
        return base64UrlNoPad(outer.toByteArray());
    }

    private static String percentEncodeAlnum(final String s) {
        final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        final StringBuilder sb = new StringBuilder(bytes.length);
        for (final byte b : bytes) {
            final int c = b & 0xff;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')) {
                sb.append((char) c);
            } else {
                sb.append('%');
                sb.append("0123456789ABCDEF".charAt(c >> 4));
                sb.append("0123456789ABCDEF".charAt(c & 0xf));
            }
        }
        return sb.toString();
    }

    private static void writeVarintField(final ByteArrayOutputStream out,
            final int field, final int value) {
        writeVarint(out, field << 3);
        writeVarint(out, value);
    }

    private static void writeBytes(final ByteArrayOutputStream out,
            final int field, final byte[] value) {
        writeVarint(out, (field << 3) | 2);
        writeVarint(out, value.length);
        out.write(value, 0, value.length);
    }

    private static void writeVarint(final ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static String base64UrlNoPad(final byte[] data) {
        final StringBuilder sb = new StringBuilder((data.length * 4 + 2) / 3);
        int i = 0;
        while (i + 3 <= data.length) {
            final int n = ((data[i] & 0xff) << 16)
                    | ((data[i + 1] & 0xff) << 8)
                    | (data[i + 2] & 0xff);
            sb.append(B64URL[(n >> 18) & 63]);
            sb.append(B64URL[(n >> 12) & 63]);
            sb.append(B64URL[(n >> 6) & 63]);
            sb.append(B64URL[n & 63]);
            i += 3;
        }
        final int rem = data.length - i;
        if (rem == 1) {
            final int n = (data[i] & 0xff) << 16;
            sb.append(B64URL[(n >> 18) & 63]);
            sb.append(B64URL[(n >> 12) & 63]);
        } else if (rem == 2) {
            final int n = ((data[i] & 0xff) << 16) | ((data[i + 1] & 0xff) << 8);
            sb.append(B64URL[(n >> 18) & 63]);
            sb.append(B64URL[(n >> 12) & 63]);
            sb.append(B64URL[(n >> 6) & 63]);
        }
        return sb.toString();
    }
}
