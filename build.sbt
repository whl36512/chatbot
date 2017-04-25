name := """chatbot"""

version := "1.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

libraryDependencies += filters
// remove jdbc. Cannnot be used with slick
libraryDependencies += jdbc
libraryDependencies += cache
libraryDependencies += ws
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
libraryDependencies += "org.postgresql" % "postgresql" % "9.4-1205-jdbc42"
// https://mvnrepository.com/artifact/org.json/json
libraryDependencies += "org.json" % "json" % "20160212"
//libraryDependencies += "com.typesafe.play" %% "play-slick" % "2.0.0"
//libraryDependencies += "com.typesafe.play" %% "play-slick-evolutions" % "2.0.0"
//libraryDependencies ++= Seq(
//  "com.typesafe.slick" %% "slick" % "3.1.1",
// "org.slf4j" % "slf4j-nop" % "1.6.4"
//)
libraryDependencies += "org.json" % "json" % "20160212"
// https://mvnrepository.com/artifact/commons-codec/commons-codec
libraryDependencies += "commons-codec" % "commons-codec" % "1.10"

// https://mvnrepository.com/artifact/org.apache.commons/commons-email
//libraryDependencies += "org.apache.commons" % "commons-email" % "1.4"




libraryDependencies += "com.typesafe.play" % "play-mailer_2.11" % "5.0.0"


