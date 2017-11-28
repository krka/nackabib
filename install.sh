#!/bin/bash
mvn clean package
if [ $? != 0 ] ; then
  echo "Install failed"
  exit 1
fi

PWD=$(pwd)
RUNNABLE=$PWD/run.sh
crontab -l | grep $RUNNABLE > /dev/null 2> /dev/null
if [ $? == 0 ] ; then
  echo "Already installed!"
else
  echo "Installing..."
  (crontab -l 2> /dev/null; echo "0,15,30,45 8-20 * * * $RUNNABLE") | crontab -
fi

