# Hubitat-TP-Link-Integration
TP-Link devices Hubitat Integration without a need for a Node Applet nor a Kasa Account (login).

# Update 4.2
Changes
1.  Application only does device search when run manually.  (Note:  If an IP address changes, the user runs the app to update.  To preclude this, set your IP addresses to STATIC in your wifi router.)

2.  Application automatically updates the device data for older versions, assuring upgrade is as smooth as possible.

3.  Bulbs now support capability "change level".  This allows pressing an up/down button then stopping to gradually change the bulb brightness.

4.  All devices added a user command to synchronize the Kasa App device name with the set Hubitat label.

5.  Cleaned up error messages for comms error with explicit recommendations to resolve.

# Description
A TP-Link integration without the need for a Node.js server nor the need for a Kasa Cloud login.  Uses UDP messaging on Hubitat, so the solution is cloudless and a single integration for all situations.

APPLICATION FEATURES

1.  Automatically installs the selected devices setting all data required to operate.

2.  Whenever opened, checks for device IP changes and updates the devices.

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
    
    3.  From the list, select the app you just installed "TP-Link App V4.2"
    
    4.  It will take about 20 seconds for the App to pop up to the next window.
    
    5.  Select "Install Kasa Devices"
    
    6.  Select your devices you want to install from the drop-down window.
    
    7.  Select Done.  Your devices should now be installed.
    
    
# Upgrade Instructions (for standard installation):

a.  Replace the contents of the existing driver and application.

b.  Run the application (this will update the required data elements.

c.  Test a sampling of devices.

# Upgrade Instructions (for manual installation):

a.  Replace the contents of the existing driver.

b.  Complete a save preferences for each device.

# TP-Link Device and GitHub file names

Model	Driver GitHub File Name

HS100	TP-Link Plug-Switch (Hubitat).groovy

HS103	TP-Link Plug-Switch (Hubitat).groovy

HS105	TP-Link Plug-Switch (Hubitat).groovy

HS107	TP-Link Multi-Plug (Hubitat).groovy

HS110	TP-Link Plug-Switch (Hubitat).groovy

HS110	TP-Link Engr Mon Plug (Hubitat).groovy

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
