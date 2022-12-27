#!/bin/sh
./gradlew assemble
cd traccar-web/modern
npm install
npm run build
cd .. && cd ..
docker build . -t thecooldrop/software:traccar-$(git rev-parse HEAD)
docker push thecooldrop/software:traccar-$(git rev-parse HEAD)