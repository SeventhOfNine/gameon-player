#!/bin/bash

#
# This script is only intended to run in the IBM DevOps Services Pipeline Environment.
#

#!/bin/bash
echo Informing Slack...
curl -X 'POST' --silent --data-binary '{"text":"A new build for the player service has started."}' $SLACK_WEBHOOK_PATH > /dev/null

echo Building projects using gradle...
./gradlew build 
rc=$?
if [ $rc != 0 ]
then
  echo "Gradle build failed, will NOT perform Docker steps."
  curl -X 'POST' --silent --data-binary '{"text":"Build for the player service has failed."}' $SLACK_WEBHOOK_PATH > /dev/null
  exit 1
fi

echo Setting up Docker...
mkdir dockercfg ; cd dockercfg
echo -e $KEY > key.pem
echo -e $CA_CERT > ca.pem
echo -e $CERT > cert.pem

echo $PWD
echo $WORKSPACE/dockercfg
cd ..

export DOCKER_CERT_PATH=$WORKSPACE/dockercfg
echo Building and Starting Docker Image using $DOCKER_CERT_PATH

cd player-wlpcfg

../gradlew --no-daemon buildDockerImage 
rc=$?
if [ $rc != 0 ]
then
  echo "Gradle build failed, will NOT perform Docker steps."
  curl -X 'POST' --silent --data-binary '{"text":"Build for the player service has failed."}' $SLACK_WEBHOOK_PATH > /dev/null
  exit 1
fi

../gradlew --no-daemon stopCurrentContainer 
../gradlew --no-daemon removeCurrentContainer
../gradlew --no-daemon startNewEtcdContainer

rm -rf dockercfg
