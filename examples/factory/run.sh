#!/bin/bash

BASE=`pwd`/../../;
FLAGS="--debug --verbose"
FLAGS="--verbose"
FLAGS=""
TIMEFORMAT="--format=TIME %U user %S system %E elapsed %Mk max"

function doit() {
  INFILES=$*
  export CLASSPATH=$BASE/plugin/target/owlapiplugin-1.0-SNAPSHOT.jar:$BASE/hexlite/java-api/target/hexlite-java-plugin-api-1.0-SNAPSHOT.jar ;
  export LOG4J_CONFIGURATION_FILE=`pwd`/log4j2.xml
  /usr/bin/time "$TIMEFORMAT" \
  hexlite $FLAGS --flpcheck=none \
    --pluginpath $BASE/hexlite/plugins/ --plugin javaapiplugin at.ac.tuwien.kr.hexlite.OWLAPIPlugin \
    --plugin jsonoutputplugin \
    --number 2 -- $INFILES |`pwd`/format_answersets.py
}


for f in query_allpainted query_deactivatable query_skippable; do
  {
    echo "=== $f";
    doit domain.hex $f.hex;
  } 2>&1 |tee $f.log
done

grep -H ^TIME query*.log >time-summary.log
cat time-summary.log
