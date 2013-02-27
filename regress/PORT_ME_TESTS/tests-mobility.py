#!/usr/bin/python
from fvregress import *
import string 	# really?  you have to do this?

if len(sys.argv) > 1 :
	wantPause = True
	timeout=9999999
	valgrindArgs=[]
else:
	wantPause = False
	timeout=5
	valgrindArgs=None

# start up a flowvisor with 1 switch (default) and two guests
# 	out of the flowvisor-conf.d-mobility config dir

#h= HyperTest(guests=[('localhost',54321),('localhost',54322)],
#	hyperargs=['-v0',"-a", "flowvisor-conf.d-mobility","ptcp:%d"% HyperTest.OFPORT] )
h = FvRegress.parseConfig(configDir='flowvisor-conf.d-mobility', valgrind=valgrindArgs)


if wantPause:
	doPause("start tests")
#################################### Start Tests
try:


	feature_request = 	 FvRegress.OFVERSION + '05 0008 2d47 c5eb'
	feature_request_after =  FvRegress.OFVERSION + '05 0008 0001 0000'
	h.runTest(name="feature_request",timeout=timeout,  events= [
			TestEvent( "send","guest","globalguy", feature_request),
			TestEvent( "recv","switch","switch1", feature_request_after),
			])
######################################
	legit_flow_mod = FvRegress.OFVERSION + '''0e 00 48 01 01 00 00 00 00 00 00 00 02 00 10
	18 07 67 87 00 0d b9 15 c0 44 ff ff 08 00 11 00
	c0 a8 02 fe c0 a8 02 02 00 43 00 44 00 00 00 05
	00 00 80 00 00 17 70 97 40 6f 98 02 00 00 00 00
	00 00 00 08 00 01 00 00'''
	h.runTest(name="legit flow_mod",timeout=timeout,  events= [
			TestEvent( "send","guest","globalguy", legit_flow_mod),
			TestEvent( "recv","switch","switch1", legit_flow_mod),
			])
######################################
	ping_in = FvRegress.OFVERSION + '''0a 00 74 00 00 00 00 00 00 35 db 00 62 00 01
	00 00 00 1c f0 ed 98 5a 00 22 41 fa 73 01 08 00
	45 00 00 54 f3 b9 00 00 40 01 00 97 c0 a8 02 7c
	c0 a8 02 8c 08 00 06 05 0d 07 00 37 d8 0c b1 49
	6b 63 05 00 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13
	14 15 16 17 18 19 1a 1b 1c 1d 1e 1f 20 21 22 23
	24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33
	34 35 36 37'''
	h.runTest(name="incoming ping",timeout=timeout,  events= [
			TestEvent( "send","switch","switch1", ping_in),
			TestEvent( "recv","guest","mvm-client2", ping_in),
			])
######################################
	legit_flow_mod2 = FvRegress.OFVERSION + '''0e 00 50 02 01 00 00 00 00 00 00 00 01 00 22
	41 fa 73 01 00 1c f0 ed 98 5a ff ff 08 00 01 00
	c0 a8 02 7c c0 a8 02 8c 00 08 00 00 00 00 00 05
	00 00 80 00 ff ff ff ff ff ff 00 00 00 00 00 00
	00 00 00 08 00 01 00 00 00 00 00 08 00 00 00 00'''
	err =  FvRegress.OFVERSION + ''' 01 00 0c 02 01 00 00 00 03 00 02'''
	h.runTest(name="legit flow_mod2",timeout=timeout,  events= [
			TestEvent( "send","guest","mvm-client2", legit_flow_mod2),
			TestEvent( "recv","switch","switch1", legit_flow_mod2),
			TestEvent( "send","guest","globalguy", legit_flow_mod2),
			TestEvent( "recv","guest","globalguy", err),
			])

#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
	if wantPause:
		doPause("start cleanup")
	h.cleanup()

