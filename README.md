# srandroid
SkyReacher app for Android

This application allows you to display, in aeronautical navigation software (SDVFR, SkyDemon, Air Navigation Pro, etc.), aircraft which are close to your current position.

To do this, it connects to a position server, via the mobile network, and transfers the aircraft positions received, to the navigation software using the GDL90 protocol on UDP port 4000.

The position server is fed by the following networks:
* [OGN](https://www.glidernet.org/) for glider positions (mainly via the FLARM protocol) and for aircraft positions using the SafeSky application
* [ADSBHub](https://www.adsbhub.org/) for aircraft equipped with ADS-B transponders

## Installation
The application must have the following permissions to function properly:
* Access to the coarse position, to send this position to the position server in order to optimize the bandwidth to only receive the closest aircraft, and this even in the background because it is the navigation application which is in the foreground
* Battery, allow operation in the background because it is the navigation application which is in the foreground

## Operation
A button allows you to activate or deactivate the transfer of aircraft positions.

The level bar has a different meaning depending on the color:
* Red: the application is not connected to the position server
* Orange: The application is connected to the location server but cannot receive locations because it has not yet sent its current coarse location
* Green: the application can receive positions and transmit them to the navigation software. The number of positions transmitted is represented by a level like a VU meter.
