package com.goodbird.cnpcgeckoaddon.client.gui.theme;

import java.util.ArrayList;
import java.util.List;

/**
 * How a theme texture is laid over a rectangle of any size, as pieces blitted one texel to one
 * pixel.
 *
 * <p>The corners go down as drawn; the edges and the middle are tiled at their own size rather
 * than stretched. The theme is pixel art, and a stretched middle smears it into bands of uneven
 * width at every size but the one it was drawn at. Where the rectangle is shorter than its two
 * borders the borders give way - the near one keeps the first half, the far one the rest -
 * instead of overlapping.</p>
 *
 * <p>Kept free of the game's classes, so the arithmetic is covered by a plain test.</p>
 */
public final class ThemeSlice {

    /** One blit: where it lands and which texels it takes, at the same size. */
    public record Piece(int x, int y, int u, int v, int width, int height) {
    }

    /** One run along an axis: where it lands, where in the texture it starts, how long it is. */
    record Span(int at, int from, int length) {
    }

    private ThemeSlice() {
    }

    /** A nine-slice with the same border on all four sides. */
    public static List<Piece> nine(int x, int y, int width, int height,
                                   int u, int v, int sourceWidth, int sourceHeight, int border) {
        return grid(spans(x, width, u, sourceWidth, border), spans(y, height, v, sourceHeight, border));
    }

    /** A strip of its source's height, whose two ends are kept and whose middle is tiled across. */
    public static List<Piece> across(int x, int y, int width, int u, int v, int sourceWidth, int height, int end) {
        if (height <= 0) {
            return List.of();
        }
        return grid(spans(x, width, u, sourceWidth, end), List.of(new Span(y, v, height)));
    }

    /** The same standing up: a column of its source's width, the middle tiled down. */
    public static List<Piece> down(int x, int y, int height, int u, int v, int width, int sourceHeight, int end) {
        if (width <= 0) {
            return List.of();
        }
        return grid(List.of(new Span(x, u, width)), spans(y, height, v, sourceHeight, end));
    }

    /**
     * Cuts one axis: the near end, as many tiles of the middle as fit (the last one cut short),
     * and the far end.
     */
    static List<Span> spans(int at, int length, int from, int sourceLength, int border) {
        List<Span> spans = new ArrayList<>();
        if (length <= 0 || sourceLength <= 0) {
            return spans;
        }
        // The source's own ends cannot overlap either.
        int cap = Math.max(0, Math.min(border, sourceLength / 2));
        int head = Math.min(cap, length / 2);
        int tail = Math.min(cap, length - head);
        if (head > 0) {
            spans.add(new Span(at, from, head));
        }
        int middle = length - head - tail;
        int tile = sourceLength - cap * 2;
        int tileFrom = from + cap;
        if (tile <= 0) {
            // All ends and no middle: the whole source is the tile.
            tile = sourceLength;
            tileFrom = from;
        }
        for (int done = 0; done < middle; done += tile) {
            spans.add(new Span(at + head + done, tileFrom, Math.min(tile, middle - done)));
        }
        if (tail > 0) {
            spans.add(new Span(at + length - tail, from + sourceLength - tail, tail));
        }
        return spans;
    }

    private static List<Piece> grid(List<Span> columns, List<Span> rows) {
        List<Piece> pieces = new ArrayList<>(columns.size() * rows.size());
        for (Span row : rows) {
            for (Span column : columns) {
                pieces.add(new Piece(column.at(), row.at(), column.from(), row.from(),
                        column.length(), row.length()));
            }
        }
        return pieces;
    }
}
