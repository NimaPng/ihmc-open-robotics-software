plugins {
   id("us.ihmc.ihmc-build") version "0.21.0"
   id("us.ihmc.ihmc-ci") version "5.3"
   id("us.ihmc.ihmc-cd") version "1.14"
   id("us.ihmc.log-tools-plugin") version "0.5.0"
}

ihmc {
   loadProductProperties("../product.properties")
   
   configureDependencyResolution()
   configurePublications()
}

mainDependencies {
   api("net.sf.trove4j:trove4j:3.0.3")
   api("com.esotericsoftware:kryonet:2.22.0-RC1")
   api("us.ihmc:ihmc-realtime:1.3.0")
   api("us.ihmc:ihmc-video-codecs:2.1.5")
   api("org.boofcv:boofcv-geo:0.36")
   api("org.apache.commons:commons-lang3:3.8.1")
   api("com.google.guava:guava:18.0")
   api("org.reflections:reflections:0.9.10")
   api("commons-net:commons-net:3.6")

   api("us.ihmc:euclid:0.15.0")
   api("us.ihmc:euclid-geometry:0.15.0")
   api("us.ihmc:ihmc-ros2-library:0.18.3")
   api("us.ihmc:ihmc-robotics-toolkit:source")
   api("us.ihmc:ihmc-interfaces:source")
   api("us.ihmc:ihmc-java-toolkit:source")
   api("us.ihmc:ihmc-interfaces:source")
}

testDependencies {
   api("us.ihmc:ihmc-robotics-toolkit-test:source")
}