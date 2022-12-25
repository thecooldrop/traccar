FROM eclipse-temurin:17.0.5_8-jdk-focal
ENV TRACCAR_VERSION 5.5

WORKDIR /opt/traccar
COPY target/tracker-server.jar /opt/traccar/tracker-server.jar
COPY target/lib /opt/traccar/lib
COPY schema /opt/traccar/schema
COPY templates /opt/traccar/templates
COPY traccar-web/modern/build /opt/traccar/modern
COPY setup/default.xml /opt/traccar/conf/default.xml
COPY setup/traccar.xml /opt/traccar/conf/traccar.xml
RUN mkdir -p /opt/traccar/logs


ENTRYPOINT ["java", "-Xms1g", "-Xmx1g", "-Djava.net.preferIPv4Stack=true"]

CMD ["-jar", "tracker-server.jar", "conf/traccar.xml"]
