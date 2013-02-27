#!/usr/bin/python
from fvregress import *
import string     # really?  you have to do this?
import sys
import xmlrpclib


# start up a flowvisor with 1 switch (default) and two guests

#h= HyperTest(guests=[('localhost',54321),('localhost',54322)],
#    hyperargs=["-v0", "-a", "flowvisor-conf.d-base", "ptcp:%d"% HyperTest.OFPORT],valgrind=valgrindArgs)

def test_failed(str):
    print "TEST FAILED!!!: " + str
    sys.exit(0)


wantPause = True

try:
    h= FvRegress()
    port=16633
    ### NOTE: intentionally do not start controllers here

    if len(sys.argv) > 1 :
        wantPause = False
        port=int(sys.argv[1])
        timeout=60
        h.useAlreadyRunningFlowVisor(port)
    else:
        wantPause = False
        timeout=5
        h.spawnFlowVisor(configFile="tests-deleteSlice.xml")
    h.lamePause()
    for i in range(1,100) : 
        h.addSwitch(name="switch"+str(i),port=port)


    if wantPause:
        doPause("start tests")
#################################### Start Tests
    user="fvadmin"
    passwd="0fw0rk"
    rpcport=18080
    t=1
    fv = xmlrpclib.ServerProxy("https://" + user + ":" + passwd + "@localhost:" + str(rpcport) + "/xmlrpc")
    print "Sleeping " + str(t) + " seconds"
    time.sleep(t)   
    print "     Deleting alice"
    if not fv.api.deleteSlice("alice") :
        test_failed("delete alice")
    print "         passed"
    print "     Deleting bob"
    if not fv.api.deleteSlice("bob") :
        test_failed("delete bob")
    print "         passed"
    print "     Counting remaining slices"
    slices = fv.api.listSlices()
    if len(slices) != 1 : 
        test_failed("too many slices: wanted 1, got " + str(count))
    print "         passed"

#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
    if wantPause:
        doPause("start cleanup")
    h.cleanup()
