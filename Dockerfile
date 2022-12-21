FROM amazoncorretto:17-alpine3.16 AS BUILDER
COPY . /temp
RUN apk add --update curl bash zip unzip nodejs npm tar && \
    rm -rf /var/cache/apk/* && \
    curl -L 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.5%2B8/OpenJDK17U-jdk_x64_linux_hotspot_17.0.5_8.tar.gz' --output /temp/setup/OpenJDK17U-jdk_x64_linux_hotspot_17.0.5_8.tar.gz && \
    curl -L 'https://github.com/megastep/makeself/releases/download/release-2.4.5/makeself-2.4.5.run' --output /temp/makeself.run && \
    chmod 777 /temp/makeself.run && \
    ./temp/makeself.run && \
    mv /makeself-2.4.5/makeself.sh /makeself-2.4.5/makeself && \
    export PATH=$PATH:/makeself-2.4.5
ENV PATH=$PATH:/makeself-2.4.5
RUN cd /temp/traccar-web/modern && \
    npm install && \
    npm run build

RUN cd /temp/setup && ./package.sh 1 linux-64
#FROM alpine:3.16 as RUNNER
#ENV TRACCAR_VERSION 5.5
#
#WORKDIR /opt/traccar
#COPY --from=BUILDER ./setup/traccar-linux-*.zip /tmp/traccar.zip
#RUN set -ex && \
#    apk add --no-cache --no-progress openjdk11-jre-headless && \
#    unzip -qo /tmp/traccar.zip -d /opt/traccar && \
#    rm /tmp/traccar.zip
#
#ENTRYPOINT ["java", "-Xms1g", "-Xmx1g", "-Djava.net.preferIPv4Stack=true"]
#
#CMD ["-jar", "tracker-server.jar", "conf/traccar.xml"]
