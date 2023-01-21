# Test code based on user request.
Notes on Soundbar. The SmartThings implementation is limited for some devices. Some basic commands may not work. (audioNotification (see later work-around), inputSource, mediaPlayback, audioTrackData)

AudioNotification. I have not tested on later models the baseline audio notification (play text). It fails miserably on mine. I have created preferences as well as the a test command to assist in determining this capability for different models. I have also added an alternate TTS /Notification method that works on my soundbar (as well as my 2020 Samsung TV). See test procedure in next block.
Preferences:

    * Use AlternateTTS Method. Allow use on TTS encoding other than Hubitat (Amazon based). Select and save preferences. It will provide further preferneces.
    * TTS Site Key: you will need a FREE account and get the Key. uri in in preference square.
    * TTS Language: These are available through this site.
    * Device IP For Local Notification: If the SmartThings audioNotification functions do not work, enter a deviceIp. This will then use UPNP audio to play the notifications.

Command Test Audio Notify: This will attempt play(dogsBarking), wait several seconds then playText(testString).

Testing Audio Notification. Please Private Message me with the results. I need to finalize the preference design to make it smoother. No logs necessary unless you can not get any to work. After installing and checking out the other functions,
Test 1:

    * command: Test Audio Notify. Results:
    * No sound. the SmartThings implementation of the soundbar does not support Audio Notification. Go to Test 3.
    * Barking Dogs, but no text notification. (expected) The Hubitat TTS notification will not work with the HubiThing Replica. Go to Test 2.

Test 2 (Barking dogs worked, but TTS notification did not):

   *  Preference Use Alternate TTS Method. Save Preferences.
   * Preferences: Obtain and enter Key. Select TTS Language from drop-down. Save Preferences
    command: Test Audio Notify. Results:
    * Both Barking Dogs and Text Notification: STOP. You are good to go. PM me with configuration.
    Otherwise, go to Test 3.

Test 3 (One or both of the two notifications did not work):

    * Preference Device IP For Local Notification: Enter your speaker device IP.
    * Preference Use Alternat TTS Method.
    * Save Preferences.
    * Preferences: Obtain and enter Key. Select TTS Language from drop-down. Save Preferences
    * command: Test Audio Notify. Results: PM me with the results of this test if you get here (i.e., Barking Dogs and Text, Barking Dogs, Text, nothing).

NOTE that I will delete this post next week. Not general enough, but no where else to put it.
