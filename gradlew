#!/usr/bin/env sh

##############################################################################
##
##  Gradle wrapper script for UNIX
##
##############################################################################

# Resolve the real path of the script (not symlink)
PRG=$0

while [ -h "$PRG" ]; do
    LS=`ls -ld "$PRG"`
    LINK=`expr "$LS" : '.*-> \(.*\)$'`
    if expr "$LINK" : '/.*' > /dev/null; then
        PRG="$LINK"
    else
        PRG=`dirname "$PRG"`/"$LINK"
    fi
done

APP_HOME=`dirname "$PRG"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to Gradle.
DEFAULT_JVM_OPTS=""

# Use the maximum available memory for the JVM
JAVA_OPTS="-Xmx2g -Dorg.gradle.daemon=true"

# Determine the Java command to use to launch the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses different directories
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME environment variable in your shell to the correct location of your Java installation."
    fi
else
    JAVACMD="java"
fi

# If a JVM is selected that is older than Java 7, warn the user.
# if $JAVACMD -version >/dev/null 2>&1 | grep "java version "1.[0-6]\." >/dev/null ; then
#     echo "ERROR: Java 7 or greater is required to run Gradle."
#     exit 1
# fi

# For Gradle, you may need to use a specific Java version or set JAVA_HOME. Check the Gradle docs.
# We will use the system's default Java, but this can be overridden by JAVA_HOME.

# Collect all arguments for the Java command.
# If JAVA_OPTS is empty, do not include it in the arguments.
JAVA_ARGS="${JAVA_OPTS:+ $JAVA_OPTS} ${DEFAULT_JVM_OPTS}"

# Find the Gradle wrapper JAR and use it to launch Gradle.
GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Fallback to older wrapper path if the new path does not exist
if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
    GRADLE_WRAPPER_JAR="$APP_HOME/lib/gradle-wrapper.jar"
fi

exec "$JAVACMD" $JAVA_ARGS -jar "$GRADLE_WRAPPER_JAR" "$@"
