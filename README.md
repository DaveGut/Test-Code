# Hubitat-TP-Link-Integration
TP-Link devices Hubitat Integration without a need for a Node Applet nor a Kasa Account (login).

Finally, a TP-Link integration without the need for a Node.js server nor the need for a Kasa Cloud login.  Uses UDP messaging on Hubitat, so the solution is cloudless and a single integration for all situations.

APPLICATION FEATURES

1.  Automatically installs the selected devices setting all data required to operate.

2.  Automatically checks for device IP changes every 30 minutes and updates the device.

3.  Whenever opened, checks for device IP changes and updates the devices.

4.  Provides two Options:

    a.  Install:  Installs the devices.
    
    b.  List Devices:  Provides a list of the devices including the device IP, alias and MAC.
                        (Useful in setting up static IP addresses).

# Installation Instructions - NEW installation using application
For a new (or clean) installation, the procedure is very simple.

a.  Device Driver Installation (for each driver)

    1.  Determine the GitHub files from the Device Drivers folder you will need.  See list at bottom of page.  Copy these files to your PC or MAC (or whatever).
    
    2.  Open Hubitat Environment and the "Drivers Code".  Select New Driver.
    
    3.  On a PC, you can drag the PC file over to the window and then press Save.  Otherwise, copy the content of the file and then past into the Hubitat Environment driver window.
    
    4.  Save the file.

b.  Application Installation

    1.  Copy the application file from Hubitat to your PC (or the file content to a clipboard).
    
    2.  Open the Apps Code Window in Hubitat Environment.  Select New App
    
    3.  Paste the code into the Environment window.
    
    4.  Save the file.

c.  Run the application

    1.  Open the Apps window in the Environment.
    
    2.  Select Add User App.
    
    3.  From the list, select the app you just installed "TP-Link Smart Home Device Manager"
    
    4.  It will take about 5 - 10 seconds for the App to pop up to the next window.
    
    5.  Select "Install Kasa Devices"
    
    6.  Select your devices you want to install from the drop-down window.
    
    7.  Select Done.  Your devices should now be installed.
    
    
# Upgrade Instructions:
Recommendation:  Upgrade is not required until you add (in the future) a new device type.  However, the significant advantages are that the new integration is off the cloud and does not require a separate node.js server.

Generally, the upgrade instructions are relatively simple:

a.  Replace the contents of the existing driver and (if applicable) application.

b.  If you have an application, Run the application (this will update some of the data elements.

c.  For each device:

    1.  Set the Bulb Preference "Default transition Time" to a desired value.
    
    2.  Set the Bulb Preference "Hue" to Low Rez.
    
    3.  For each device, "Save" the preferences.
    
This should work in 95% of the cases.

# TP-Link Device and GitHub file names

Model	Driver GitHub File Name

HS100	TP-Link Plug-Switch (Hubitat).groovy

HS103	TP-Link Plug-Switch (Hubitat).groovy

HS105	TP-Link Plug-Switch (Hubitat).groovy

HS107	TP-Link Multi-Plug (Hubitat).groovy

HS110	TP-Link Plug-Switch (Hubitat).groovy

HS110	TP-Link Plug-Switch (Hubitat).groovy

HS200	TP-Link Plug-Switch (Hubitat).groovy

HS210	TP-Link Plug-Switch (Hubitat).groovy

HS220	TP-Link Dimming Switch (Hubitat).groovy

HS300	TP-Link Multi-Plug (Hubitat).groovy

KB100	TP-Link Soft White Bulb (Hubitat).groovy

KB130	TP-Link Color Bulb (Hubitat).groovy

KL110	TP-Link Soft White Bulb (Hubitat).groovy

KL120	TP-Link Tunable White Bulb (Hubitat).groovy

KL130	TP-Link Color Bulb (Hubitat).groovy

KP100	TP-Link Plug-Switch (Hubitat).groovy

LB100	TP-Link Soft White Bulb (Hubitat).groovy

LB110	TP-Link Soft White Bulb (Hubitat).groovy

LB120	TP-Link Tunable White Bulb (Hubitat).groovy

LB130	TP-Link Color Bulb (Hubitat).groovy

LB200	TP-Link Soft White Bulb (Hubitat).groovy

LB230	TP-Link Color Bulb (Hubitat).groovy
