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
#	hyperargs=["-v0", "-a", "flowvisor-conf.d-deny", "ptcp:%d"% HyperTest.OFPORT],valgrind=valgrindArgs)
h = FvRegress.parseConfig(configDir='flowvisor-conf.d-deny',valgrind=valgrindArgs)

if wantPause:
	doPause("start tests")
#################################### Start Tests
try:




	ping_in = FvRegress.OFVERSION + '''0a 00 74 00 00 00 00 01 20 a3 b0 00 62 00 03
				00 00 00 16 cb ac 1e 0e 00 d0 05 5d 24 00 08 00
				45 00 00 54 00 00 40 00 3f 01 39 5f ac 18 4a 60
				0a 4f 01 83 08 00 f5 25 22 11 00 01 aa e1 7c 4a
				c9 98 05 00 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13
				14 15 16 17 18 19 1a 1b 1c 1d 1e 1f 20 21 22 23
				24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33
				34 35 36 37
				'''

	h.runTest(name="ping_in ", timeout=timeout, events= [
			TestEvent( "send","switch","switch1", ping_in),
			TestEvent( "recv","guest","flowdragging", ping_in),
			#TestEvent( "recv","guest","production", ''),
			])
#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
	if wantPause:
		doPause("start cleanup")
	h.cleanup()

