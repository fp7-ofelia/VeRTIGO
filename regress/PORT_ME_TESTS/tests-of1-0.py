#!/usr/bin/python
from fvregress import *
import string     # really?  you have to do this?
import sys

if len(sys.argv) > 1 :
    wantPause = True
    timeout=9999999
    valgrindArgs= []
else:
    wantPause = False
    timeout=5
    valgrindArgs= None

# start up a flowvisor with 1 switch (default) and two guests

h = FvRegress.parseConfig(configDir='flowvisor-conf.d-of1-0', valgrind=valgrindArgs)

if wantPause:
    doPause("start tests")
#################################### Start Tests
try:


    feature_request =     FvRegress.OFVERSION + '05 0008 2d47 c5eb'
    feature_request_after = FvRegress.OFVERSION + '05 0008 0101 0000'
    h.runTest(name="feature_request",timeout=timeout,  events= [
            TestEvent( "send","guest",'production', feature_request),
            TestEvent( "recv","switch",'switch1', feature_request_after, strict=True),
            ])

######################################################
    set_config_before = FvRegress.OFVERSION + '09 00 0c 00 00 00 00 00 00 00 80'
    set_config_after =  FvRegress.OFVERSION + '09 00 0c 00 00 00 00 00 00 ff ff'
    h.runTest(name="set_config", timeout=timeout, events= [
            TestEvent( "send","guest",'production', actorID2="switch1", packet=set_config_before),
            TestEvent( "recv","switch",'switch1', packet=set_config_after),
            ])


#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
    if wantPause:
        doPause("start cleanup")
    h.cleanup()

