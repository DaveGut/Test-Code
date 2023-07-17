# Test code based on user request.
These files are temporary files to support testing as well as temporary capability provision pending final integration.

## Kasa Matter Energy Monitor Plug (KP125M) Interim Driver
Provides on/off, energy monitor and ability to set various "settings"

* Driver Link:  https://raw.githubusercontent.com/DaveGut/Test-Code/master/man_kasaSmart_plug_em.groovy
* Installation: see instructions below.

## Kasa H100 Hub Test (Data Collection) Driver
Provides alarm functions as well as access to installed children (for data collection).  ON/OFF function allows toggleing alarm via voice systems such as Amazon/Google.

* Objective:  obtain data on the child TRV device so I can develop TRV driver.
* Driver Link: https://raw.githubusercontent.com/DaveGut/Test-Code/master/kasaSmart_hub_test.groovy
* Installation: see instructions below
* Test
  * Verify the on starts the alarm and off ends the alarm
  * Select Preference "Get Data for Developer"
  * Save preferences and send WARN message to developer via a Private Message
 
## Installation Instructions
* From the Drivers Page, select newDriver.
* On the driver edit page, at the top row, is an Import Button.
  * Select Import
  * Enter the Link from above
  * Save Driver
* go to the Devices tab in Hubitat
* Select New Driver,then Virtual
  * Enter a name of your choice
  * Select the appropriate "type" from drop-down
  * Save
* From the Devices Page, Preferences Section
  * Enter the IP address for the device
  * Enter your TP-LINK (KASA) UserName and Password
  * Select Save Preferences.
  * Test On/Off function observing the attribute on the device's page
* Most common errors are Device IP, UserName, and Password not properly entered.
