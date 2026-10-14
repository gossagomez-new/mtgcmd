package dev.mtgcmd.testsupport;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Captures one component's diagnostics without changing its configured log level. */
public final class LogCapture implements AutoCloseable {
  private final Logger logger;
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  public LogCapture(Class<?> component) {
    logger = (Logger) LoggerFactory.getLogger(component);
    appender.start();
    logger.addAppender(appender);
  }

  public List<ILoggingEvent> events() {
    return List.copyOf(appender.list);
  }

  @Override
  public void close() {
    logger.detachAppender(appender);
    appender.stop();
  }
}
