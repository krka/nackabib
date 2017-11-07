#!/bin/bash
set -e
DIR=$(dirname $0)
cd $DIR
rm -f last_run.out last_run.err
mvn package >> last_run.out 2>> last_run.err
java -jar target/nackabib-*-jar-with-dependencies.jar -d data -r index.html "$@" >> last_run.out 2>> last_run.err
if [ -f upload.sh ] ; then
  bash upload.sh >> last_run.out 2>> last_run.err
fi

