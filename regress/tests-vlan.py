#!/usr/bin/python
from fvregress import *
import string     # really?  you have to do this?
import sys


# start up a flowvisor with 1 switch (default) and two guests

#h= HyperTest(guests=[('localhost',54321),('localhost',54322)],
#    hyperargs=["-v0", "-a", "flowvisor-conf.d-base", "ptcp:%d"% HyperTest.OFPORT],valgrind=valgrindArgs)

wantPause = True

try:

    h= FvRegress()
    port=16633
    h.addController("alice",    54321)
    h.addController("bob",      54322)

    if len(sys.argv) > 1 :
        wantPause = False
        port=int(sys.argv[1])
        timeout=60
        h.useAlreadyRunningFlowVisor(port)
    else:
        wantPause = False
        timeout=5
        h.spawnFlowVisor(configFile="tests-vlan.xml")
    h.lamePause()
    h.addSwitch(name='switch1',port=port)
    h.addSwitch(name='switch2',port=port)


    if wantPause:
        doPause("start tests")
#################################### Start Tests
# all packets without vlan tags should go to bob
    packet_no_vlan1 =  FvRegress.OFVERSION + '''0a 0052 0000 0000 0000 0101
            0040 0003 0000 0000 0000 0001 0000 0000
            0002 0800 4500 0032 0000 0000 40ff f72c
            c0a8 0028 c0a8 0128 7a18 586b 1108 97f5
            19e2 657e 07cc 31c3 11c7 c40c 8b95 5151
            3354 51d5 0036'''
    packet_no_vlan2 =  FvRegress.OFVERSION + '''0a 0052 0000 0002 0000 0101
            0040 0003 0000 0000 0000 0002 0000 0000
            0001 0800 4500 0032 0000 0000 40ff f72c
            c0a8 0028 c0a8 0128 7a18 586b 1108 97f5
            19e2 657e 07cc 31c3 11c7 c40c 8b95 5151
            3354 51d5 0036'''
    packet_vlan1 =  FvRegress.OFVERSION + '''0a 0056 0000 0000 0000 0101
            0040 0003 0000 0000 0000 0001 0000 0000
            0002 8100       
            0001 0800            
            4500 0032 0000 0000 40ff f72c
            c0a8 0028 c0a8 0128 7a18 586b 1108 97f5
            19e2 657e 07cc 31c3 11c7 c40c 8b95 5151
            3354 51d5 0036'''
    packet_vlan812 =  FvRegress.OFVERSION + '''0a 0056 0000 0002 0000 0101
            0040 0003 0000 0000 0000 0002 0000 0000
            0001 8100
            032c 0800 
            4500 0032 0000 0000 40ff f72c
            c0a8 0028 c0a8 0128 7a18 586b 1108 97f5
            19e2 657e 07cc 31c3 11c7 c40c 8b95 5151
            3354 51d5 0036'''
    h.runTest(name="switch2controller packet_in routing - shared port", timeout=timeout, events= [
            TestEvent( "send","switch",'switch1', packet_no_vlan1),
            TestEvent( "recv","guest",'bob', packet_no_vlan1),
            TestEvent( "send","switch",'switch1', packet_no_vlan2),
            TestEvent( "recv","guest",'bob', packet_no_vlan2),
            TestEvent( "send","switch",'switch1', packet_vlan1),
            TestEvent( "recv","guest",'bob', packet_vlan1),
            TestEvent( "send","switch",'switch1', packet_vlan812),
            TestEvent( "recv","guest",'alice', packet_vlan812),
            ])

#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
    if wantPause:
        doPause("start cleanup")
    h.cleanup()

