#!/usr/bin/python
from fvregress import *
import sys
import xmlrpclib
import re



# start up a flowvisor with 1 switch (default) and two guests

#h= HyperTest(guests=[('localhost',54321),('localhost',54322)],
#    hyperargs=["-v0", "-a", "flowvisor-conf.d-base", "ptcp:%d"% HyperTest.OFPORT],valgrind=valgrindArgs)

def test_failed(str):
    s = "TEST FAILED!!!: " + str
    print s
    raise Exception(s)

wantPause = True
try:

    h= FvRegress()
    port=16633
    rpcport=18080
    h.addController("alice",    54321)
    h.addController("bob",      54322)
    wantPause = False

    if len(sys.argv) > 1 :
        port=int(sys.argv[1])
        timeout=60
        h.useAlreadyRunningFlowVisor(port)
    else:
        timeout=5
        h.spawnFlowVisor(configFile="tests-base.xml")
    h.lamePause()
    h.addSwitch(name='switch1',port=port)
    h.addSwitch(name='switch2',port=port)
    h.lamePause()


    if wantPause:
        doPause("start tests")
#################################### Start fvadmin Tests
    user="fvadmin"
    passwd="0fw0rk"
    s = xmlrpclib.ServerProxy("https://" + user + ":" + passwd + "@localhost:" + str(rpcport) + "/xmlrpc")

### ping (fvadmin)
    print "fvadmin ping test"
    x= s.api.ping("Joe mama")
    valid1 = "PONG\(fvadmin\): "
    valid2 = "Joe mama"
    if not re.search(valid2,x):
        print "Got '"+ x + "' but wanted '" + valid2 + "'"
        test_failed("ping test")
    if not re.search(valid1,x):
        print "Got '"+ x + "' but wanted '" + valid1 + "'"
        test_failed("ping test")
    print "     passed"

### listFlowSpace (fvadmin)
    print "fvadmin listFlowSpace test"
    x = s.api.listFlowSpace()
    #for x in  s.api.listFlowSpace():
    #        print x
    valid_len = 10
    if len(x) != valid_len: 
        print "Got " + str(len(x)) + " entries but wanted " + str(valid_len)
        test_failed("listFlowSpace fvadmin test")
    print "     passed"


### getDevices (fvadmin)
    print "GetDevices Test"
    x =  s.api.listDevices()
    valid_len = 2
    for device in x : 
        print "                 " + device
    if len(x) != valid_len :
        print "Got " + str(len(x)) + " entries but wanted " + str(valid_len)
        test_failed("listDevices fvadmin test1")
    valid = "00:00:00:00:00:00:00:01"
    if x[0] != valid:
        print "Got " + x[0] + " but wanted " + valid
        test_failed("listDevices fvadmin test2")
    valid = "00:00:00:00:00:00:00:02"
    if x[1] != valid:
        print "Got " + x[1] + " but wanted " + valid
        test_failed("listDevices fvadmin test3")
    x = s.api.getDeviceInfo("00:00:00:00:00:00:00:01")
    for key,val in  x.iteritems():
        print "                 "+ key + "="  + val
    portList= x["portList"]
    if not portList : 
        print "getDeviceInfo failed to return a portList"
        test_failed("listDevices fvadmin test4")
    right_portlist = "0,1,2,3"
    if portList != right_portlist : 
        print "getDeviceInfo return wrong port list: wanted " + right_portlist + " but got " + portList
        test_failed("listDevices fvadmin test5")
    print "     passed"

### getLinks (fvadmin)
    print "fvadmin getLinks test"
    x = s.api.getLinks()
    linkcount=0
    valid_len = 2
    if len(x) != valid_len: 
        print "Got " + str(len(x)) + " links but wanted " + str(valid_len)
        test_failed("getLinks fvadmin test")
    for link in x:
        print "             Link " + str(linkcount) + ":"
        linkcount+=1
        for key,val in link.iteritems():
            print "                 " + key  + "=" + val 
    print "     passed"

### changeFlowSpace(1234)
    print "fvadmin changeFlowSpace(REMOVE, 1234)"
    change = {"operation" : "REMOVE", "id" : "1234"}
    try:
        if not s.api.changeFlowSpace([change]):
            test_failed("remove illegal flow space")
    except xmlrpclib.Fault:
        print "     passed"


### Slice Creation: Cathy
    print "Slice creation: Cathy"
    lame_email = "cathy@foo.com"
    if not s.api.createSlice("cathy","cathyPass","tcp:localhost:54323",lame_email) :
        print "Got false from creating slice for cathy"
        test_failed("slice creation")
    x = s.api.getConfig("slices!cathy!contact_email")
    if (len(x) < 1) or x[0] != lame_email: 
        print "Failed to get correct email for cathy: wanted " + lame_email + " but got " + str(x)
        test_failed("slice creation")
    print "     passed"

### Slice Creation: Doug
    print "Slice creation: Doug (with FieldSeparator) -- should be blocked"
    cool_email = "laudi@daudi.com"
    try:
        s.api.createSlice("doug!e!fresh", "theOriginal", "tcp:localhost:54324", cool_email)
        print "Failed:  created a slice with an '!' when that should be disallowed"
        test_failed("slice creation with FieldSeparator")
    except (xmlrpclib.Fault):
        print "     passed"

### Slice Creation: Cathy again
    print "Slice creation: Cathy (again) -- should be blocked"
    some_email = "cathy@dos.com"
    try:
        s.api.createSlice("cathy", "otherPass", "tcp:localhost:54323", some_email)
        print "Failed: created a slice which already existed."
        test_failed("Slice creation with existing ID")
    except (xmlrpclib.Fault):
        print "    passed"
        

### getSliceInfo (alice)
    print "Test: getSliceInfo(alice)"
    x = s.api.getSliceInfo("alice")
    for key,val in  x.iteritems():
        print "                 "+ key + "="  + val
    ################################################################
    # send some more traffic just so we have some stats to report
    lldp_out = FvRegress.OFVERSION + '''0d 00 3a 00 00 00 00 ff ff ff ff ff fd 00 08
            00 00 00 08 00 01 00 00 01 23 20 00 00 01 00 12
            e2 b8 dc 4c 88 cc 02 07 04 e2 b8 dc 3b 17 95 04
            03 02 00 01 06 02 00 78 00 00'''
    lldp_out_after = FvRegress.OFVERSION + '''0d005e00000000fffffffffffd0008
            00000008000100000123200000010012
            e2b8dc4c88cc020704e2b8dc3b179504
            03020001060200780000022407616c69
            636500202020206d6167696320666c6f
            777669736f7231000615deadcafe'''
    h.runTest(name="lldp hack", timeout=timeout, events= [
            TestEvent( "send","guest",'alice', lldp_out),
            TestEvent( "recv","switch",'switch1', lldp_out_after),
            ])
    ################################################################


### getSliceStats (alice)
    print "Test: getSliceStats(alice)"
    x = s.api.getSliceStats("alice")
    if x :
        print "Alice's STATS:"
        print x
    else:
        test_failed("getSliceStats returned None")

    print "Test: getSwitchStats(00:00:00:00:00:00:00:01)"
    x = s.api.getSwitchStats("00:00:00:00:00:00:00:01")
    if x :
        print "Switch 00:00:00:00:00:00:00:01's STATS:"
        print x
    else:
        test_failed("getSliceStats returned None")


#################################### Start Alice Tests
    user="alice"
    passwd="alicePass"
    s = xmlrpclib.ServerProxy("https://" + user + ":" + passwd + "@localhost:" + str(rpcport) + "/xmlrpc")

### ping (alice)
    print "Alice ping test"
    x= s.api.ping("Joe mama")
    valid = "PONG\(alice\):"
    if(not re.search(valid,x)):
        print "Got '"+ x + "' but wanted '" + valid + "'"
        test_failed("ping test")
    print "     passed"

### listFlowSpace (alice)
    print "Alice listFlowSpace test"
    x = s.api.listFlowSpace()
    valid_len = 6 
    if len(x) != valid_len: 
        print "Got " + str(len(x)) + " entries but wanted " + str(valid_len)
        test_failed("listFlowSpace alice test")
    print "     passed"

    ## FIXME!
    #print s.api.change_passwd("alice","foo")
#################################### Start Bob Tests
    user="bob"
    passwd="bobPass"
    s = xmlrpclib.ServerProxy("https://" + user + ":" + passwd + "@localhost:" + str(rpcport) + "/xmlrpc")

### ping (bob)
    print "Bob ping test"
    x= s.api.ping("Joe mama")
    valid = "PONG\(bob\):"
    if(not re.search(valid,x)):
        print "Got '"+ x + "' but wanted '" + valid + "'"
        test_failed("ping test")
    print "     passed"

### listFlowSpace (bob)
    print "Bob listFlowSpace test"
    x = s.api.listFlowSpace()
    valid_len = 4 
    if len(x) != valid_len: 
        print "Got " + str(len(x)) + " entries but wanted " + str(valid_len)
        test_failed("listFlowSpace bob test")
    print "     passed"

### changeSLice (bob)
    print "Bob changeSlice (email)"
    x = s.api.changeSlice("bob","contact_email", "bob@elsewhere.com")
    if not x:
        print "Got %s ; was expecting true" % (str(x))
        test_failed("changeSlice bob email test")
    print "     passed"


### changeSLice (bob)
    print "Bob changeSlice (port)"
    x = s.api.changeSlice("bob","controller_port", "4444")
    if not x:
        print "Got %s ; was expecting true" % (str(x))
        test_failed("changeSlice bob port test")
    print "     passed"

### getSliceInfo (bob)
    print "Bob getSliceInfo"
    x = s.api.getSliceInfo("bob")
    if not x['contact_email'] or x['contact_email'] != "bob@elsewhere.com":
        print "Got %s ; was expecting 'bob@elsewhere.com'" % str(x['contact_email'])
        test_failed("getSliceInfo bob email test")
    if not x['controller_port'] or x['controller_port'] != "4444":
        print "Got %s ; was expecting 4444" % (str(x['4444']))
        test_failed("getSliceInfo bob port test")
    print "     passed"
#################################### Start fvadmin Tests
    user="fvadmin"
    passwd="0fw0rk"
    s = xmlrpclib.ServerProxy("https://" + user + ":" + passwd + "@localhost:" + str(rpcport) + "/xmlrpc")

### ping (2) (fvadmin)
    print "fvadmin ping test(2)"
    x= s.api.ping("Joe mama")
    valid = "PONG\(fvadmin\):"
    if(not re.search(valid,x)):
        print "Got '"+ x + "' but wanted '" + valid + "'"
        test_failed("ping test")
    print "     passed"

### deleteSlice(alice) (fvadmin)
    print "fvadmin deleteSlice(alice)"
    if not s.api.deleteSlice('alice') :
        print "Got false!"
        test_failed("remove slice test")
    x = s.api.listFlowSpace()
    #for x in  s.api.listFlowSpace():
    #        print x
    valid_len = 4
    if len(x) != valid_len: 
        print "Got " + str(len(x)) + " entries but wanted " + str(valid_len) + " :: FlowSpace delete failed"
        test_failed("listFlowSpace fvadmin test")
    print "     passed"


#################################### Start Tests
# more tests for this setup HERE
#################################### End Tests
finally:
    h.cleanup()

