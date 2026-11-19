package io.github.gfmois.connlab.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * A line of the server log shown in the monitor.
 *
 * @param time    when it happened
 * @param kind    what happened
 * @param address the client involved, or null
 * @param text    the message or description
 */
public record LogLine(Instant time, Kind kind, String address, String text) {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public enum Kind {
        INFO("INFO"),
        RECEIVED("RX"),
        SENT("TX"),
        ERROR("ERROR");

        private final String tag;

        Kind(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    /**
     * @return the line as shown in the monitor, with the time of day
     */
    public String display() {
        return TIME.format(time) + "  " + format();
    }

    /**
     * @return the line with the full date, for copying
     */
    @Override
    public String toString() {
        return DATE_TIME.format(time) + " " + format();
    }

    private String format() {
        String tag = String.format("%-5s", kind.tag());
        return address == null ? tag + " " + text : tag + " [" + address + "] " + text;
    }
}
