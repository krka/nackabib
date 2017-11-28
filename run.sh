#!/bin/bash
set -e
DIR=$(dirname $0)
cd $DIR
rm -f last_run.out last_run.err

# If the data has been updated within the last 4 hours, exit early
if [ "x" != "x$(find data/* -cmin -240)" ] ; then
  echo "Data updated recently, skipping run" >> last_run.out
else
  java -jar target/nackabib-*-jar-with-dependencies.jar -d data -r index.html "$@" >> last_run.out 2>> last_run.err
fi

if [ -f upload.sh ] ; then
  bash upload.sh >> last_run.out 2>> last_run.err
fi

