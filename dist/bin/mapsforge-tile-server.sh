#!/bin/sh

message()
{
  TITLE="Cannot start mapsforge-tile-server"
  if [ -t 1 ]; then
    echo "ERROR: $TITLE\n$1"
  elif [ -n `which zenity` ]; then
    zenity --error --title="$TITLE" --text="$1"
  elif [ -n `which kdialog` ]; then
    kdialog --error --title "$TITLE" "$1"
  elif [ -n `which xmessage` ]; then
    xmessage -center "ERROR: $TITLE: $1"
  elif [ -n `which notify-send` ]; then
    notify-send "ERROR: $TITLE: $1"
  else
    echo "ERROR: $TITLE\n$1"
  fi
}

UNAME=`which uname`
GREP=`which egrep`
GREP_OPTIONS=""
OS_TYPE=`"$UNAME" -s`

if [ -n "$JDK_HOME" -a -x "$JDK_HOME/bin/java" ]; then
  JDK="$JDK_HOME"
elif [ -n "$JAVA_HOME" -a -x "$JAVA_HOME/bin/java" ]; then
  JDK="$JAVA_HOME"
else
  JAVA_BIN_PATH=`which java`
  if [ -n "$JAVA_BIN_PATH" ]; then
    if [ "$OS_TYPE" = "FreeBSD" -o "$OS_TYPE" = "MidnightBSD" ]; then
      JAVA_LOCATION=`JAVAVM_DRYRUN=yes java | "$GREP" '^JAVA_HOME' | "$CUT" -c11-`
      if [ -x "$JAVA_LOCATION/bin/java" ]; then
        JDK="$JAVA_LOCATION"
      fi
    elif [ "$OS_TYPE" = "SunOS" ]; then
      JAVA_LOCATION="/usr/jdk/latest"
      if [ -x "$JAVA_LOCATION/bin/java" ]; then
        JDK="$JAVA_LOCATION"
      fi
    elif [ "$OS_TYPE" = "Darwin" ]; then
      JAVA_LOCATION=`/usr/libexec/java_home`
      if [ -x "$JAVA_LOCATION/bin/java" ]; then
        JDK="$JAVA_LOCATION"
      fi
    fi
  fi
fi

if [ -z "JAVA_BIN_PATH" ]; then
  JAVA_BIN_PATH="$JDK/bin/java"
fi

if [ -z "JAVA_BIN_PATH" ]; then
  message "No JDK found. Please validate either JDK_HOME or JAVA_HOME environment variable points to valid JDK installation."
  exit 1
fi

SCRIPT_LOCATION=$0
MT_HOME=`dirname "$SCRIPT_LOCATION"`/..

exec "$JAVA_BIN_PATH" -server -Xmx1G -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -Xbootclasspath/a:"$MT_HOME/lib/marlin-0.5.4-Unsafe.jar" -Dsun.java2d.renderer=org.marlin.pisces.PiscesRenderingEngine -Dsun.java2d.renderer.useRef=hard -Dfile.encoding=UTF-8 -Djava.library.path="$MT_HOME/lib" -Djava.awt.headless=true -Duser.language=en -Dorg.slf4j.simpleLogger.showLogName=false -Dorg.slf4j.simpleLogger.showDateTime=true -Dorg.slf4j.simpleLogger.dateTimeFormat="yyyy-MM-dd HH:mm:ss,SSS" -jar "$MT_HOME/mapsforge-tile-server.jar" --theme "$MT_HOME/renderThemes" "$@"
exit 1 