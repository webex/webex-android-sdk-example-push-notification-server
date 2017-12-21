# Overview

[Cisco Spark Android SDK](https://developer.ciscospark.com/sdk-for-android.html) enables you to embed [Cisco Spark](https://www.ciscospark.com/) calling and meeting experience into your Android mobile application. The SDK provides APIs to make and receive audio/video calls. In order to receive audio/video calls, the user needs to be notified when someone is calling the user.

This sample Webhook/Push Notification Server demonstrates how to write a server application to receive [Incoming Call Notification](https://developer.ciscospark.com/sdk-for-android.html) from Cisco Spark and use [Google Firebase Cloud Messaging (FCM)](https://firebase.google.com/docs/cloud-messaging/) to notify the mobile application.

For more information about Google push notification, please see [Firebase Guides](https://firebase.google.com/docs/guides/).

# How it works

Assuming this sample Webook/Push Notification Server has been deployed on the public Internet, the following describes the webhooks and push notification workflow step by step.

![Spark-AndroidSDK-APNS](https://github.com/ciscospark/spark-android-sdk-example-push-notification-server/blob/master/Spark-ANDROIDSDK-APNS.png)

1. Launch the Firebase Cloud Messaging (FCM) client application on Android, which will register to Google FCM automatically.

2. Get device token from FCM via onTokenRefresh() callback.

3. Register the device token returned by the FCM and the user Id of current user to the  Webhook/Push Notification Server. The Server stores these information locally in a database.
	```
	let paramaters: Parameters = [
		"email": email,
		"voipToken": voipToken,
		"msgToken": msgToken,
		"personId": personId
	]
	Alamofire.request("https://example.com/register", method: .post, parameters: paramaters, encoding: JSONEncoding.default).validate().response { res in
		// ...
	}
	```

4. After the user logs into Cisco Spark，use [Webhook API](https://ciscospark.github.io/spark-android-sdk) to create an webhook at Cisco Spark cloud. The target URL of the webhook must be the /webhook REST endpoint of this server. The URL has to be publicly accessible from the Internet.
	```
	spark.webhooks().create("Message Webhook", targetUrl, "messages", "all", null, null, new CompletionHandler<Webhook>() {
	    @Override
	    public void onComplete(Result<Webhook> result) {
			if (result.isSuccessful()) {
	            Webhook webHook = result.getData();
	            // ...
	        } else {
	            // ...
	        }
		}
	});
	```

5. The remote party makes a call via Cisco Spark.

6. Ciso Spark receives the call and triggers the webhook. The incoming call event is sent to the target URL, which should be /webhook REST endpoint of this Webhook/Push Notification server.

7. The Webhook/Push Notification Server looks up the device token from the database by the user Id in the incoming call event, then sends the notification with the device token and incoming call information to the FCM.

8. The FCM pushs notification to the Android device.

9. Your Android application [gets the push notification](https://github.com/firebase/quickstart-android/blob/master/messaging/app/src/main/java/com/google/firebase/quickstart/fcm/MyFirebaseMessagingService.java) and uses the SDK API to accept the call from Spark Cloud.

For more details about Step 1, 2, 7 and 9, please see the following detailed explanation.

For more details about Step 3 and 6, please see Cisco Spark [Webhooks Explained](https://developer.ciscospark.com/webhooks-explained.html)

# How to get device token in Android App

1. Set up a [FCM Client App](https://firebase.google.com/docs/cloud-messaging/android/client) on Android. This functionality is demonstrated in the [quick sample](https://github.com/firebase/quickstart-android/tree/master/messaging), which is already setup with Firebase dependencies and such.

2. [Add Firebase to your Android project](https://firebase.google.com/docs/android/setup) with [Firebase Console](https://console.firebase.google.com/). Please notice that the package name to be filled should be the same as the Android app created before.

3. Download [google-services.json](https://support.google.com/firebase/answer/7015592) file from the newly created project and copy it to the Android client’s app/ folder, then sync with Gradle files from within Android Studio.

4. Build and run the app from Android Studio. When the app get launched, the Android client's device token can be captured via onTokenRefresh() callback.

# How to send message to client App

There are two types of messages in FCM (Firebase Cloud Messaging):

1. **Display Messages**: These messages trigger the onMessageReceived() callback only when your app is in **foreground**

2. **Data Messages**: Theses messages trigger the onMessageReceived() callback even if your app is in **foreground/background/killed**

You can send a display message via [Firebase Console](https://console.firebase.google.com/project/_/notification), but Firebase team have not developed a UI to send data-messages yet, which means when an app is not running you will not anyway receive notification from Firebase Console.

To achieve sending data message to client, you have to perform a POST to the following URL:
```
POST https://fcm.googleapis.com/fcm/send
```
And the following headers:

* **Key**: Content-Type, **Value**: application/json
* **Key**: Authorization, **Value**: key=&lt;your-server-key&gt;

Body using topics:
```
{
    "to": "/topics/my_topic",
    "data": {
        "my_custom_key" : "my_custom_value",
        "my_custom_key2" : "my_custom_value2"
     }
}
```
Or if you want to send it to specific devices:
```
{
    "data": {
        "my_custom_key" : "my_custom_value",
        "my_custom_key2" : "my_custom_value2"
     },
    "registration_ids": ["{device-token}","{device2-token}","{device3-token}"]
}
```
**NOTE**: To get your server key, you can find it in the Firebase Console: Your project -> settings -> Project settings -> Cloud messaging -> Server Key

# How to handle push notification message

This is how you handle the received message:
```
@Override
public void onMessageReceived(RemoteMessage remoteMessage) { 
     Map<String, String> data = remoteMessage.getData();
     String myCustomKey = data.get("my_custom_key");

     // Manage data
}
```
**NOTE**: If you press "Force Stop" in the application settings then FCM will not deliver new messages to app. This is consistent with the purpose of "Force Stop" which is:

"The user requested to stop the app. The system should not start app again until the user specifically asked for it by launching the app via the launcher icon."

To resume receiving push notifications on the device where the app was force stopped, simply relaunch the app. 