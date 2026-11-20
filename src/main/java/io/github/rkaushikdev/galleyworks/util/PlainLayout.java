package io.github.rkaushikdev.galleyworks.util;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;

/** Renders a log event as {@code LEVEL    logger message}, for a person at a terminal. */
public final class PlainLayout extends LayoutBase<ILoggingEvent> {

    private static final int LEVEL_WIDTH = 8;

    @Override
    public String doLayout(ILoggingEvent event) {
        String level = Log.levelName(event.getLevel());
        return level
                + " ".repeat(Math.max(0, LEVEL_WIDTH - level.length()))
                + " "
                + event.getLoggerName()
                + " "
                + event.getFormattedMessage()
                + "\n";
    }
}
