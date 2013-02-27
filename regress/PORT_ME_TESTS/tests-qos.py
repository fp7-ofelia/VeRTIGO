#!/usr/bin/python
from fvregress import *
import string 	# really?  you have to do this?

if len(sys.argv) > 1 :
	wantPause = True
	timeout=9999999
	valgrindArgs= []

else:
	wantPause = False
	timeout=5
        valgrindArgs= None


# start up a flowvisor with 1 switch (default) and two guests
# 	out of the flowvisor-conf.d-mobility config dir

#h= HyperTest(guests=[('localhost',54321),('localhost',54322), ('localhost',54323)],
#	hyperargs=['-v0',"-a", "flowvisor-conf.d-qos","ptcp:%d" % HyperTest.OFPORT ],valgrind=valgrindArgs)
h = FvRegress.parseConfig(configDir='flowvisor-conf.d-qos', valgrind=valgrindArgs)


if wantPause:
	doPause("start tests")
#################################### Start Tests
try:


	feature_request = 	 FvRegress.OFVERSION + '05 0008 2d47 c5eb'
	feature_request_after =  FvRegress.OFVERSION + '05 0008 0001 0000'
	h.runTest(name="feature_request",timeout=timeout,  events= [
			TestEvent( "send","guest","alice", feature_request),
			TestEvent( "recv","switch","switch1", feature_request_after),
			])
########################################
	udp =      FvRegress.OFVERSION + '''0d 0058 0000 abcd ffff ffff
		ffff 0008 0000 0008 0001 0080 0123 2000
		0001 0000 0000 0000 0800 4500 0032 0000
		4000 4011 2868 c0a8 c800 c0a8 c901 0001
		0000 001e d7c3 cdc0 251b e6dc ea0c 726d
		973f 2b71 c2e4 1b6f bc11 8250'''
	udp_and_vlan = FvRegress.OFVERSION + '''0d 00 60 01 01 00 00 ff ff ff ff ff ff 00 10
		00 01 00 08 00 0f 00 00 00 00 00 08 00 01 00 80
		01 23 20 ff 00 01 00 00 00 00 00 00 08 00 45 00
		00 32 00 00 40 00 40 11 28 68 c0 a8 c8 00 c0 a8
		c9 01 00 01 00 00 00 1e d7 c3 cd c0 25 1b e6 dc
		ea 0c 72 6d 97 3f 2b 71 c2 e4 1b 6f bc 11 82 50'''
	udp_and_pcp = FvRegress.OFVERSION + '''0d 00 60 02 01 00 00 ff ff ff ff ff ff 00 10
		00 02 00 08 03 00 00 00 00 00 00 08 00 01 00 80
		01 23 20 ff 00 02 00 00 00 00 00 00 08 00 45 00
		00 32 00 00 40 00 40 11 28 68 c0 a8 c8 00 c0 a8
		c9 01 00 01 00 00 00 1e d7 c3 cd c0 25 1b e6 dc
		ea 0c 72 6d 97 3f 2b 71 c2 e4 1b 6f bc 11 82 50'''
	udp_and_both = FvRegress.OFVERSION + '''0d 00 68 03 01 00 00 ff ff ff ff ff ff 00 18
		00 01 00 08 0f a0 00 00 00 02 00 08 05 00 00 00
		00 00 00 08 00 01 00 80 01 23 20 ff 00 03 00 00
		00 00 00 00 08 00 45 00 00 32 00 00 40 00 40 11
		28 68 c0 a8 c8 00 c0 a8 c9 01 00 01 00 00 00 1e
		d7 c3 cd c0 25 1b e6 dc ea 0c 72 6d 97 3f 2b 71
		c2 e4 1b 6f bc 11 82 50'''
	udp_and_slicing = FvRegress.OFVERSION + '''0d 00 60 09 01 00 00 ff ff ff ff ff ff 00 10
        00 0b 00 10 00 01 00 00 00 00 00 00 00 00 00 06
        01 23 20 ff 00 04 00 00 00 00 00 00 08 00 45 00
        00 32 00 00 40 00 40 11 28 68 c0 a8 c8 00 c0 a8
        c9 01 00 01 00 00 00 1e d7 c3 cd c0 25 1b e6 dc
        ea 0c 72 6d 97 3f 2b 71 c2 e4 1b 6f bc 11 82 50
        '''
	udp_and_both_and_slicing = FvRegress.OFVERSION + '''0d 00 70 0a 01 00 00 ff ff ff ff ff ff 00 20
        00 01 00 08 0f a0 00 00 00 02 00 08 05 00 00 00
        00 0b 00 10 00 01 00 00 00 00 00 00 00 00 00 07
        01 23 20 ff 00 05 00 00 00 00 00 00 08 00 45 00
        00 32 00 00 40 00 40 11 28 68 c0 a8 c8 00 c0 a8
        c9 01 00 01 00 00 00 1e d7 c3 cd c0 25 1b e6 dc
        ea 0c 72 6d 97 3f 2b 71 c2 e4 1b 6f bc 11 82 50
        '''
	h.runTest(name="packet_out qos re-write",timeout=timeout,  events= [
			TestEvent( "send","guest","alice", udp),
			TestEvent( "recv","switch","switch1", udp_and_vlan),
			TestEvent( "send","guest","bob", udp),
			TestEvent( "recv","switch","switch1", udp_and_pcp),
			TestEvent( "send","guest","cathy", udp),
			TestEvent( "recv","switch","switch1", udp_and_both),
			TestEvent( "send","guest","doug", udp),
			TestEvent( "recv","switch","switch1", udp_and_slicing),
			TestEvent( "send","guest","erik", udp),
			TestEvent( "recv","switch","switch1", udp_and_both_and_slicing),
			])
######################################
	flow_mod = FvRegress.OFVERSION + '''0e 00 50 01 01 00 00 00 00 00 00 00 02 00 10
        18 07 67 87 00 0d b9 15 c0 44 ff ff 08 00 11 00
        c0 a8 02 fe c0 a8 02 02 00 43 00 44 00 00 00 05
        00 00 00 00 00 00 00 00 00 00 80 00 00 17 70 97 40 6f 98 02 00 00 00 00
        00 00 00 08 00 01 00 00'''
	flow_mod_and_vlan = FvRegress.OFVERSION + '''0e 00 58 01 01 00 00 00 00 00 00 00 02 00 10
		18 07 67 87 00 0d b9 15 c0 44 ff ff 08 00 11 00
		c0 a8 02 fe c0 a8 02 02 00 43 00 44 00 00 00 05
		00 00 00 00 00 00 00 00 00 00 80 00 00 17 70 97 40 6f 98 02 00 00 00 00
		00 01 00 08 00 0f 00 00 00 00 00 08 00 01 00 00'''
	flow_mod_and_pcp = FvRegress.OFVERSION + '''0e 00 58 05 01 00 00 00 00 00 00 00 02 00 10
        18 07 67 87 00 0d b9 15 c0 44 ff ff 08 00 11 00
        c0 a8 02 fe c0 a8 02 02 00 43 00 44 00 00 00 05
        00 00 00 00 00 00 00 00 00 00 80 00 00 17 70 97
        40 6f 98 02 00 00 00 00 00 02 00 08 03 00 00 00
        00 00 00 08 00 01 00 00
        '''
	flow_mod_and_both= FvRegress.OFVERSION + '''0e 00 60 06 01 00 00 00 00 00 00 00 02 00 10
        18 07 67 87 00 0d b9 15 c0 44 ff ff 08 00 11 00
        c0 a8 02 fe c0 a8 02 02 00 43 00 44 00 00 00 05
        00 00 00 00 00 00 00 00 00 00 80 00 00 17 70 97
        40 6f 98 02 00 00 00 00 00 01 00 08 0f a0 00 00
        00 02 00 08 05 00 00 00 00 00 00 08 00 01 00 00
        '''
	flow_mod_and_slicing = FvRegress.OFVERSION + '''0e 00 58 09 01 00 00 00 00 00 00 00 02 00 10
        18 07 67 87 00 0d b9 15 c0 44 ff ff 08 00 11 00
        c0 a8 02 fe c0 a8 02 02 00 43 00 44 00 00 00 05
        00 00 00 00 00 00 00 00 00 00 80 00 00 17 70 97
        40 6f 98 02 00 00 00 00 00 0b 00 10 00 01 00 00
        00 00 00 00 00 00 00 06
        '''
	flow_mod_and_both_and_slicing = FvRegress.OFVERSION + '''0e 00 68 0a 01 00 00 00 00 00 00 00 02 00 10
        18 07 67 87 00 0d b9 15 c0 44 ff ff 08 00 11 00
        c0 a8 02 fe c0 a8 02 02 00 43 00 44 00 00 00 05
        00 00 00 00 00 00 00 00 00 00 80 00 00 17 70 97
        40 6f 98 02 00 00 00 00 00 01 00 08 0f a0 00 00
        00 02 00 08 05 00 00 00 00 0b 00 10 00 01 00 00
        00 00 00 00 00 00 00 07
        '''
	h.runTest(name="flow_mod qos re-write",timeout=timeout,  events= [
			TestEvent( "send","guest","alice", flow_mod),
			TestEvent( "recv","switch","switch1", flow_mod_and_vlan),
			TestEvent( "send","guest","bob", flow_mod),
			TestEvent( "recv","switch","switch1", flow_mod_and_pcp),
			TestEvent( "send","guest","cathy", flow_mod),
			TestEvent( "recv","switch","switch1", flow_mod_and_both),
			TestEvent( "send","guest","doug", flow_mod),
			TestEvent( "recv","switch","switch1", flow_mod_and_slicing),
			TestEvent( "send","guest","erik", flow_mod),
			TestEvent( "recv","switch","switch1", flow_mod_and_both_and_slicing),
			])


#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
	if wantPause:
		doPause("start cleanup")
	h.cleanup()

