// Copyright 2016-2017 Cisco Systems Inc
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.ciscowebex.androidsdk.example.pns;

import com.ciscowebex.androidsdk.example.pns.model.PushNotificationRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class Main {

  private enum NotificationType {
    Message,
    VoIP;

    public static NotificationType fromShort(short x) {
      NotificationType[] types = NotificationType.values();
      if (x >= 0 && x < types.length) {
        return types[x];
      }
      return null;
    }

    public String getTitle() {
      return this == VoIP ? "Incoming Call" : "New Message";
    }

    public String getBody(WebhookNotification webhook) {
      return this == VoIP
        ? webhook.getActorId()
        : webhook.getData().getPersonEmail();
    }
  }

  @Value("${spring.datasource.url}")
  private String dbUrl;

  @Autowired
  private DataSource dataSource;

  @Autowired
  private FCMService fcmService;

  public static void main(String[] args) throws Exception {
    SpringApplication.run(Main.class, args);
  }

  private String getDecodedString(String string) {
    String result = null;
    String decodedString = new String(Base64.decodeBase64(string));
    String[] parts = decodedString.split("/");
    if (parts.length > 0) {
      result = parts[parts.length - 1];
    }
    return result;
  }

  @RequestMapping("/webhook")
  public ResponseEntity<?> doWebhook(
    @RequestBody WebhookNotification notification
  ) {
    try {
      System.out.println(new ObjectMapper().writeValueAsString(notification));
    } catch (JsonProcessingException e) {
      System.out.println(e);
    }
    String to = null;
    NotificationType type = NotificationType.Message;
    String resource = notification.getResource();
    if ("messages".equals(resource)) {
      String from = notification.getActorId();
      String createdBy = notification.getCreatedBy();
      if (createdBy != null && !createdBy.equals(from)) {
        // Decode and get createdBy
        to = getDecodedString(createdBy);
      }
    } else if ("callMemberships".equals(resource)) {
      to = getDecodedString(notification.getData().getPersonId());
      type = NotificationType.VoIP;
    }

    System.out.println("to: " + to);
    if (to != null && to.length() > 0) {
      try (Connection connection = dataSource.getConnection()) {
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(
          "SELECT token FROM device_tokens WHERE personId = '" + to + "';"
        );
        while (rs.next()) {
          String token = rs.getString("token");
          System.out.println("token: " + token);
          if (token != null && token.length() > 0) {
            PushNotificationRequest request = new PushNotificationRequest(
              type.getTitle(),
              type.getBody(notification),
              null,
              token
            );
            fcmService.sendMessage(request, notification);
            System.out.println("Done");
          }
        }
      } catch (Exception e) {
        System.out.println(e);
        return new ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
    return new ResponseEntity(HttpStatus.NO_CONTENT);
  }

  @RequestMapping(value = "/register", method = RequestMethod.POST)
  ResponseEntity<?> doRegister(@RequestBody DeviceRegistration reg) {
    if (
      reg.getToken() == null ||
      reg.getPersonId() == null ||
      reg.getEmail() == null
    ) {
      return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }
    System.out.println(reg.getEmail() + ": " + reg.getToken());
    try (Connection connection = dataSource.getConnection()) {
      Statement stmt = connection.createStatement();
      stmt.executeUpdate(
        "CREATE TABLE IF NOT EXISTS device_tokens (token varchar(255) NOT NULL PRIMARY KEY, email varchar(255) NOT NULL, personId varchar(255) NOT NULL);"
      );
      stmt.executeUpdate(
        "INSERT INTO device_tokens (personId, email, token) VALUES ('" +
        reg.getPersonId() +
        "', '" +
        reg.getEmail() +
        "', '" +
        reg.getToken() +
        "') ON CONFLICT (token) DO UPDATE SET email = EXCLUDED.email, personId = EXCLUDED.personId;"
      );
      return new ResponseEntity(HttpStatus.NO_CONTENT);
    } catch (Exception e) {
      System.out.println(e);
      return new ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @RequestMapping(value = "/register/{token}", method = RequestMethod.DELETE)
  ResponseEntity<?> doUnregister(@PathVariable("token") String token) {
    if (token == null) {
      return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }
    System.out.println("Device token: " + token);
    try (Connection connection = dataSource.getConnection()) {
      Statement stmt = connection.createStatement();
      stmt.executeUpdate(
        "DELETE FROM device_tokens WHERE token = '" + token + "';"
      );
      return new ResponseEntity(HttpStatus.NO_CONTENT);
    } catch (Exception e) {
      System.out.println(e);
      return new ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @RequestMapping(value = "/register", method = RequestMethod.GET)
  ResponseEntity<?> doLookup(@RequestParam("email") String email) {
    if (email == null || email.length() <= 0) {
      return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }
    System.out.println("Email: " + email);
    try (Connection connection = dataSource.getConnection()) {
      Statement stmt = connection.createStatement();
      ResultSet rs = stmt.executeQuery(
        "SELECT token FROM device_tokens WHERE email = '" + email + "';"
      );
      ArrayList<String> output = new ArrayList<String>();
      while (rs.next()) {
        String token = rs.getString("token");
        if (token != null && token.length() > 0) {
          output.add(token);
        }
      }
      return ResponseEntity.ok(output);
    } catch (Exception e) {
      System.out.println(e);
      return new ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @RequestMapping("/")
  String index() {
    return "Webex Android SDK Push Notification Server Example";
  }

  @Bean
  public DataSource dataSource() throws SQLException {
    if (dbUrl == null || dbUrl.isEmpty()) {
      return new HikariDataSource();
    } else {
      HikariConfig config = new HikariConfig();
      config.setJdbcUrl(dbUrl);
      return new HikariDataSource(config);
    }
  }
}
