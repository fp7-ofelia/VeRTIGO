#!/bin/sh

out=tests-config.xml
dump=tests-config.dump
good=tests-config.good

################
echo Generating a default config
sh ../scripts/fvconfig.sh generate $out foo-host foo-pass

if [ ! -s $out ] ; then
    echo "FAILED to generate config"
    exit 1
fi
echo SUCCESS : generated a default config

################
echo Dumping config to human readable format
sh ../scripts/fvconfig.sh dump $out | sort > $dump


if [ ! -s $dump ] ; then
    echo "FAILED to dump config"
    exit 1
fi
echo SUCCESS : dumped config

################
echo Comparing to known-good config
diff -c $dump $good

if [ $? -ne 0 ] ; then
    echo "FAILED : good config differs from generated config" 
    exit 1
fi
echo SUCCESS : no differences in config
