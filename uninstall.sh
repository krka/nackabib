#!/bin/bash
PWD=$(pwd)
RUNNABLE=$PWD/run.sh
crontab -l | grep $RUNNABLE > /dev/null 2> /dev/null
if [ $? == 0 ] ; then
  echo "Uninstalling..."
  (crontab -l 2> /dev/null | grep -v "$RUNNABLE") | crontab -
else
  echo "Not installed!"
fi

