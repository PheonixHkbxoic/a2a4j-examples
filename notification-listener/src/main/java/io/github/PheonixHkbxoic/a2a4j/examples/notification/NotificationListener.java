package io.github.PheonixHkbxoic.a2a4j.examples.notification;

import io.github.PheonixHkbxoic.a2a4j.mvc.WebMvcNotificationAdapter;
import io.github.PheonixHkbxoic.a2a4j.notification.autoconfiguration.A2a4jNotificationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/20 01:39
 * @desc
 */
@Component
public class NotificationListener extends WebMvcNotificationAdapter {

    public NotificationListener(@Autowired A2a4jNotificationProperties a2a4jNotificationProperties) {
        super(a2a4jNotificationProperties.getEndpoint(), a2a4jNotificationProperties.getJwksUrls());
    }

}
