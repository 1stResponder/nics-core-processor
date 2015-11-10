===================
=  Spring Runner  =
===================

Spring Runner is a basic, empty Spring app that you would use with a processor built in another library.  You can simply run the Spring app, which points to a libdir with all your dependencies in it, including any Camel Processors, and a spring configuration xml file to run.

The purpose of this component is to basically be the one true source of spring runners used for all apps, and to maintain versions of dependencies via the outputting of dependency jars to 'target/dependencies'.

You can also copy this directory as a starting point for a new spring app, if you didn't want to build your processor bean, then have to test it with a separate spring runner instance.  

You would customize the filenames from "app" to your component's name:
	src/main/resources/app.xml
	src/main/config/app.properties
	src/main/config/log4j.properties
	src/main/scripts/runSpring.sh

You would also edit the above files, inserting your bean references, etc.

DEPLOY

To get a zip file to put on a server, run:
	mvn deploy

This results in a zip file:
	target/spring-runner.zip

Which contains a /dependencies directory housing all the dependencies listed in the pom,
along with the following files necessary to run the spring app:
	log4j.properties
	runSpring.sh
	app.properties
	app.xml

Simply copy the zip to a server, unzip into the directory of choice, edit the above files, and add any beans to the
dependencies folder, configure a route in the xml file, and you're off.
