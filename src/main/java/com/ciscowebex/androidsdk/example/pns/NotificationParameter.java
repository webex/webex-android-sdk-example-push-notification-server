package com.ciscowebex.androidsdk.example.pns;

public enum NotificationParameter {
  SOUND("default"),
  COLOR("#FFFF00");

  private String value;

  NotificationParameter(String value) {
    this.value = value;
  }

  public String getValue() {
    return this.value;
  }
}
