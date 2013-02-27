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

#h= HyperTest(guests=[('localhost',54321),('localhost',54322)],
#	hyperargs=["-v0", "-a", "flowvisor-conf.d-expand", "ptcp:%d"% HyperTest.OFPORT],valgrind=valgrindArgs)
h = FvRegress.parseConfig(configDir='flowvisor-conf.d-expand', valgrind=valgrindArgs)


if wantPause:
	doPause("start tests")
#################################### Start Tests
try:


	feature_request = 	 FvRegress.OFVERSION + '05 0008 2d47 c5eb'
	feature_request_after =  FvRegress.OFVERSION + '05 0008 0201 0000'
	h.runTest(name="feature_request",timeout=timeout,  events= [
			TestEvent( "send","guest","alice", feature_request),
			TestEvent( "recv","switch","switch1", feature_request_after,strict=True),
			])

	############################################################
	feature_reply  =     FvRegress.OFVERSION + '''06 00e0 0201 0000 0000 76a9
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
	feature_reply_after =FvRegress.OFVERSION + '''06 00b0 2d47 c5eb 0000 76a9 d40d 2548
				0000 0100 0200 0000 0000 001f 0000 03ff
				0000 1ac1 51ff ef8a 7665 7468 3100 0000
				0000 0000 0000 0000 0000 0000 0000 0000
				0000 00c0 0000 0000 0000 0000 0000 0000
				0002 ca8a 1ef3 77ef 7665 7468 3500 0000
				0000 0000 0000 0000 0000 0000 0000 0000
				0000 00c0 0000 0000 0000 0000 0000 0000
				0003fabc778d7e0b766574683700000000000000000000000000000000000000000000c0000000000000000000000000'''



	h.runTest(name="feature_reply", timeout=timeout, events= [
			TestEvent( "send","switch","switch1", feature_reply),
			TestEvent( "recv","guest","alice", feature_reply_after),
			])
	################################################################
	flow_mod_del_all =  FvRegress.OFVERSION + '''0e0040a0bed70affffffff
				303031326532623866336430
				200000003800000000000000
				000000690002000000030000
				0000000000000000ffff0000
				00000000'''
	flow_mod_del_all_after=FvRegress.OFVERSION + '''0e0040a0bed70affffffff
				303031326532623866336430
				200000003800000000000000
				000000690002000000030000
				0000000000000000ffff0000
				00000000'''
	h.runTest(name="flow_mod del all", timeout=timeout, events= [
			TestEvent( "send","guest","bob", flow_mod_del_all),
			TestEvent( "recv","switch","switch1", flow_mod_del_all_after),
			])


#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
	if wantPause:
		doPause("start cleanup")
	h.cleanup()

