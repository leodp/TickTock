# TickTock App - Technical Specifications

## Project Overview
Develop an Android application for characterizing the pendulum timing in old clocks
**License:** GNU General Public License V3.0

## Core Configuration
- **Target Platform:** Android 11+
- **App Name:** "TickTock"
- **APK Filename:** TickTock.apk

## Visual Design
- **Orientation:** Portrait mode
- **Color Scheme:** White background, black borders, blue text

## Description
This project wil generate an apk android>11 app to help setting the beat on a pendulum clock

A pendulum has a left **L** and a right **R** swing, which may have a distinct duration, based on asymmetries in mechanical parts of the clock and to a non fully respected vertical orientation of the clock

Purpose of the app is detecting the asymmetry and provide a feedback to the user in order to help with"setting the beat", which happens when the durations of **L** and **R** are equal

Typical durations of **L** and **R** range from 0.2 to 4 seconds

The app will open an audio stream on the android device, and analyze the input audio in real time. 

The first beat detected will be assumed to be **L**

## UI
The UI has a field where the user sets an initial guessing value for the duration of each beat. Initial value is set at 600ms. The field is named "Initial guess"

There is then an arrow on a gauge field. Values range from -25% to +25%. The arrow value is given as 100*(**L**-**R**)/(**L**+**R**). 

Below the arrow gauge some numerical fields are provided for calculated data:

- "Period", showing **R**+**L**
- "Asymmetry [ms]", showing (**L**-**R**)
- "Asymmetry [%]", showing 100*(**L**-**R**)/(**L**+**R**)

Below that a pulsing dot will shortly light-up blue for a detected **L** beat, and green for **R**. During the initial data acquisition (see below) the dots light-up in red, and a message is printed with the text "Searching beat..."

## Analysis
The audio stream is acquired with at least 22Khz sampling rate
A bandpass filter between 600Hz and 5000Hz with a reduction gain of 40dB or more is applied to clean the audio
The **L** and **R** beats have a typical shape of an oscillating signal, with an envelope rapidly growing and then slowly decaying. There may be beating in the envelope, of a duration around 10ms. **L** and **R** beat may have distinct fit parmeters (for example the base frequency, in the kHz range, may be slight different) although an initial guessing may be the same one
The beats time position should be detected with a precision of no less than 1ms

## App Icon
Use the stylized image of an old pendulum clock

## Test
A WAV file si provided as example, to test the fit routines, the correct identification of the beats, the calculations of the parameters. 
In the example file named Clock.wav, the duration of the **L** and **R** sound is around 20ms, and the two beats have a duration each of 560ms and 586ms

## Licensing
GNU GPL V3, whatever the latest is . Add notices and required files

## Update1

Make all indications and text 3x bigger, make the arrow longer and thicker. The Arrow color changes from red when the asymmetry modulus is >10%, to yellow for values between 10% and 2%.5% to green for less than 2%

Double the initial guess value, changing the text to "Initial guess period" and using this value as period, not single **L** or **R** beat

After finding the beat, the values may show a drift for about 10 beats. This may be due to drifting fit parameters. After starting the audio refine fit parameters for max 40 beats, then keep the fit constant

There is a blue button for starting the audio recording. Add a text inside this button: "Start" for beginning the recording. Once the audio recording has started, change the text to "Stop". Pressing this button stops the audio acquisition, and changes the button text back to "Start"

Below the GUI add a text reading "Android app for setting the beat on a pendulum clock"

Make a better icon for the app, with a more clear image of a pendulum clock. The icon of the installed apk should be visualized in both square or circular shape (background color in both images consistent, without square cutout)

Mark the release with the version number 1.0
Add a readme file for explaining what the app does and how to use it
Remove the debug apk and assemble a release app
Upload everything, including the built apk to github

## Update2

The fit stops after the required number of beats, but the tracking too: tracking and calculation of beats asymmetry and all UI indications should continue

The fields with indications should be split in two lines, to allow bigger indications in the display: one line with the field text name, one line for the calculated value

Make the tracking dot 3 times bigger

The icon is ok, but still not on on round symbols. Make the background of the icon (now white) of the same color as the icon background for square icons (blue)

Assemble and push