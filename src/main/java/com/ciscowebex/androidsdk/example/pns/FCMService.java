package com.ciscowebex.androidsdk.example.pns;

import com.ciscowebex.androidsdk.example.pns.model.PushNotificationRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.*;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FCMService {

  private Logger logger = LoggerFactory.getLogger(FCMService.class);

  public void sendMessage(
    PushNotificationRequest request,
    WebhookNotification notification
  )
    throws InterruptedException, ExecutionException {
    System.out.println("inside sendMessage method");
    try {
      Message message = getPreconfiguredMessageToToken(request, notification);
      String response = sendAndGetResponse(message);
      logger.info(
        "Sent message to token. Device token: " +
        request.getToken() +
        ", " +
        response
      );
    } catch (Exception e) {
      System.out.println(e);
    }
  }

  private String sendAndGetResponse(Message message)
    throws InterruptedException, ExecutionException {
    return FirebaseMessaging.getInstance().sendAsync(message).get();
  }

  private AndroidConfig getAndroidConfig(String topic) {
    return AndroidConfig
      .builder()
      .setTtl(Duration.ofMinutes(2).toMillis())
      .setCollapseKey(topic)
      .setPriority(AndroidConfig.Priority.HIGH)
      .setNotification(
        AndroidNotification
          .builder()
          .setSound(NotificationParameter.SOUND.getValue())
          .setColor(NotificationParameter.COLOR.getValue())
          .setTag(topic)
          .build()
      )
      .build();
  }

  // private ApnsConfig getApnsConfig(String topic) {
  //     return ApnsConfig.builder()
  //             .setAps(Aps.builder().setCategory(topic).setThreadId(topic).build()).build();
  // }

  private Message getPreconfiguredMessageToToken(
    PushNotificationRequest request,
    WebhookNotification notification
  ) {
    String data = "{}";
    try {
      data = new ObjectMapper().writeValueAsString(notification);
    } catch (JsonProcessingException e) {
      System.out.println(e);
    }
    return getPreconfiguredMessageBuilder(request)
      .putData("data", data)
      .setToken(request.getToken())
      .build();
  }

  private Message.Builder getPreconfiguredMessageBuilder(
    PushNotificationRequest request
  ) {
    AndroidConfig androidConfig = getAndroidConfig(request.getTopic());
    // ApnsConfig apnsConfig = getApnsConfig(request.getTopic());
    return Message
      .builder()
      // .setApnsConfig(apnsConfig)
      .setAndroidConfig(androidConfig)
      .setNotification(
        new Notification(request.getTitle(), request.getMessage())
      );
  }
}
