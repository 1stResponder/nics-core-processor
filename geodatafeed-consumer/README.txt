COMPONENT
geodatafeed-consumer

DESCRIPTION
Consumes GML PLI messages, and inserts them into a PostGIS database, which
correspond to WFS layers that can be placed in the SADISPLAY.

FILES

src/main/config/spring/geodatafeed-consumer.xml
-------------------------------------------------
Main Spring configuration. This shouldn't normally need to be modified unless
the routes are changing. Everything that is/should be configurable can be set
in the properties file.

src/main/config/geodatafeed-consumer.properties
------------------------------------------------
The properties file for configuring geodatafeed-consumer for different nodes and
environments. Individual configuration for different VMs may be present with a 
.### postfix of the VM IP.
*** The Component Manager application will manage properties on a node-by-node
basis. This properties file just serves a skeleton properties file or set of
defaults. ***


src/main/scripts/start.sh
--------------------------
The start script to run geodatafeed-consumer.

BUILD/DEPLOY
mvn (clean compile) package builds deployable tar named
	geodatafeed-consumer-<VERSION>.tar.gz
This tar contains the files needed to run it, including dependencies, and is
meant to be used with the Component Manager application for deployment.
