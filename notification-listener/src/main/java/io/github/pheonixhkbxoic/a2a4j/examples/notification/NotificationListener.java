package io.github.pheonixhkbxoic.a2a4j.examples.notification;

import io.github.pheonixhkbxoic.a2a4j.mvc.WebMvcNotificationAdapter;
import io.github.pheonixhkbxoic.a2a4j.notification.mvc.autoconfiguration.A2a4jNotificationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/20 01:39
 * @desc
 */
@Component
public class NotificationListener extends WebMvcNotificationAdapter {
    protected final ScheduledThreadPoolExecutor scheduler;

    public NotificationListener(@Autowired A2a4jNotificationProperties a2a4jNotificationProperties) {
        super(a2a4jNotificationProperties.getEndpoint(), a2a4jNotificationProperties.getJwksUrls());
        scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.scheduleAtFixedRate(() -> {
            if (verifyFailCount.get() != 0) {
                this.reloadJwks();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

}
