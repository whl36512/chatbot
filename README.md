This is your new Play application
=================================

This file will be packaged with your application when using `activator dist`.

There are several demonstration files available in this template.

Controllers
===========

- HomeController.scala:

  Shows how to handle simple HTTP requests.

- AsyncController.scala:

  Shows how to do asynchronous programming when handling a request.

- CountController.scala:

  Shows how to inject a component into a controller and use the component when
  handling requests.

Components
==========

- Module.scala:

  Shows how to use Guice to bind all the components needed by your application.

- Counter.scala:

  An example of a component that contains state, in this case a simple counter.

- ApplicationTimer.scala:

  An example of a component that starts when the application starts and stops
  when the application stops.

Filters
=======

- Filters.scala:

  Creates the list of HTTP filters used by your application.

- ExampleFilter.scala

  A simple filter that adds a header to every response.


Production Deployment
=====================

- $sbt universal:packageZipTarball
- copy ./target/universal/vent-1.0-SNAPSHOT.tgz to poduction server

- untar files at production server and 

- $my-first-app-1.0/bin/my-first-app -Dplay.crypto.secret=mysecret

Production Deployment using Docker
==================================
- $sbt universal:packageZipTarball
- scp ~/chatbot/target/universal/chatbot-1.1-SNAPSHOT.tgz ec2-user@54.214.124.241:.
- Login to ec2 as root
- cp ~ec2-user/chatbot-1.1-SNAPSHOT.tgz ~/dockerfiles/chatbot/.
- cd dockerfiles/chatbot/
- tar xvf chatbot-1.1-SNAPSHOT.tgz
- cp chatbot-1.1-SNAPSHOT/conf/dockerfiles/chatbot.docker ~/dockerfiles/chatbot/.
- docker build  --rm -t local/chatbot -f chatbot.docker .
docker run --privileged --name chatbot   --network=my-net --ip='10.0.0.10'  -p 80:9000 -d -v /sys/fs/cgroup:/sys/fs/cgroup:ro local/chatbot  # --privileged is requred by sshd and postgresql

