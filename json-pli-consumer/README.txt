======================
=  Sar PLI Consumer  =
======================

Initially created: 20130228

The sar-consumer app listens for UDP packet containing PLI data in JSON format, 
coverts to GML format, amd publishes the GML to the message bus for consumption
by the geodatafeed-consumer component. This will insert the data into the PostGIS
database for use by GeoServer. The SADisplay can then display the WFS generated
from this data and will show the location of the mobile devices reporting in.

A spring configuration xml file (sar-consumer.xml) contains configurable 
properties, which can be set in the sar-consumer.properties file.

BUILD

    mvn clean install

DEPLOY

To get a tar.gz file to put on a server, run:
	mvn deploy

This results in a tar file:
	target/sar-consumer-5.x-SNAPSHOT-deployable.tar.gz

Which contains a /lib directory housing all the dependencies listed in the pom,
along with the following files necessary to run the spring app:
	log4j.properties
	start.sh
	sar-consumer.properties
	sar-consumer.xml

Simply copy the gz to a server,
    tar -zxvf sar-consumer-5.x-SNAPSHOT-deployable.tar.gz
into the directory of choice, edit the above files, and add any beans to the
dependencies folder, configure a route in the xml file, and you're off.
*** Ideally you should use the component manager application to deploy this, as 
that's the structure we're using for the tar file.***
