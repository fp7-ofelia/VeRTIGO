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

#h= HyperTest(guests=[('localhost',54321)],
#	hyperargs=["-v0", "-a", "flowvisor-conf.d-match_all", "ptcp:%d"% HyperTest.OFPORT],valgrind=valgrindArgs)
h = FvRegress.parseConfig(configDir='flowvisor-conf.d-match_all', valgrind=valgrindArgs)


if wantPause:
	doPause("start tests")
#################################### Start Tests
try:


	feature_request = 	 FvRegress.OFVERSION + '05 0008 2d47 c5eb'
	feature_request_after =  FvRegress.OFVERSION + '05 0008 0101 0000'
	h.runTest(name="feature_request",timeout=timeout,  events= [
			TestEvent( "send","guest","alice", feature_request),
			TestEvent( "recv","switch","switch1", feature_request_after,strict=True),
			])

	############################################################
	feature_reply  =     FvRegress.OFVERSION + '''06 00e0 0101 0000 0000 76a9
				d40d 2548 0000 0100 0200 0000 0000 001f
				0000 03ff 0000 1ac1 51ff ef8a 7665 7468
				3100 0000 0000 0000 0000 0000 0000 0000
				0000 0000 0000 00c0 0000 0000 0000 0000
				0000 0000 0001 ce2f a287 f670 7665 7468
				3300 0000 0000 0000 0000 0000 0000 0000
				0000 0000 0000 00c0 0000 0000 0000 0000
				0000 0000 0002 ca8a 1ef3 77ef 7665 7468
				3500 0000 0000 0000 0000 0000 0000 0000
				0000 0000 0000 00c0 0000 0000 0000 0000
				0000 0000 0003 fabc 778d 7e0b 7665 7468
				3700 0000 0000 0000 0000 0000 0000 0000
				0000 0000 0000 00c0 0000 0000 0000 0000
				0000 0000'''
	# this reply should strip the STP bit, and trim the ports down to the allowable set
	# this changed find out why
	feature_reply_after  =     FvRegress.OFVERSION + '''06 00e0 2d47 c5eb 0000 76a9
				d40d 2548 0000 0100 0200 0000 0000 001f
				0000 03ff 0000 1ac1 51ff ef8a 7665 7468
				3100 0000 0000 0000 0000 0000 0000 0000
				0000 0000 0000 00c0 0000 0000 0000 0000
				0000 0000 0001 ce2f a287 f670 7665 7468
				3300 0000 0000 0000 0000 0000 0000 0000
				0000 0000 0000 00c0 0000 0000 0000 0000
				0000 0000 0002 ca8a 1ef3 77ef 7665 7468
				3500 0000 0000 0000 0000 0000 0000 0000
				0000 0000 0000 00c0 0000 0000 0000 0000
				0000 0000 0003 fabc 778d 7e0b 7665 7468
				3700 0000 0000 0000 0000 0000 0000 0000
				0000 0000 0000 00c0 0000 0000 0000 0000
				0000 0000'''



	h.runTest(name="feature_reply", timeout=timeout, events= [
			TestEvent( "send","switch","switch1", feature_reply),
			TestEvent( "recv","guest","alice", feature_reply_after),
			])
	############################################################
	packet_to_g0_p0 =   FvRegress.OFVERSION + '''0a 0052 0000 0000 0000 0101
				0040 0000 0000 0000 0000 0001 0000 0000
				0002 0800 4500 0032 0000 0000 40ff f72c
				c0a8 0028 c0a8 0128 7a18 586b 1108 97f5
				19e2 657e 07cc 31c3 11c7 c40c 8b95 5151
				3354 51d5 0036'''
	h.runTest(name="switch2controller packet_in routing - by port", timeout=timeout, events= [
			TestEvent( "send","switch","switch1", packet_to_g0_p0),
			TestEvent( "recv","guest","alice", packet_to_g0_p0),
			])
#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
	if wantPause:
		doPause("start cleanup")
	h.cleanup()

