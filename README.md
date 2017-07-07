This is a sample project to test the google shet api. Once you install the app on your device, it will do the following :
1. On app launch you will see a button named "Save Device Details to Drive". 
2. Click this button, you will get a pop-up to select a google account if there are more than 1 and one more to ask for permission.
3. In case of no connectivity issue or google account related issues, the following device details will be written to a google sheet and saved 
to the drive associated with the account you selected.
Device Name;
Brand;
Display;
Produce;
VERSION.RELEASE;
VERSION.INCREMENTAL;
VERSION.SDK.NUMBER;
BOARD;
BOOTLOADER;
FINGERPRINT;
HARDWARE;
HOST;
ID;
MANUFACTURER;
MODEL;
SERIAL;
TAGS;
TIME;
TYPE;
USER;
4. There would be a google sheet named "Device Details" in your google drive which has all these details.

Following modifications are intended for this app in future :
1. Display the details on the home screen along with writing to the google sheet
2. Currently for every run, the app creates a new google sheet, I want the app to first check if there is a sheet named "Device Details",
If exists then append the data without clearing existing data
If doesnot exist then create a new sheet named "Device Details" and write details to it.


