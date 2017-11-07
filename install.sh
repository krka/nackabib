#!/bin/bash
PWD=$(pwd)
RUNNABLE=$PWD/run.sh
crontab -l | grep $RUNNABLE > /dev/null 2> /dev/null
if [ $? == 0 ] ; then
  echo "Already installed!"
else
  echo "Installing..."
  (crontab -l 2> /dev/null; echo "15 8,12,16,20 * * * $RUNNABLE") | crontab -
fi

