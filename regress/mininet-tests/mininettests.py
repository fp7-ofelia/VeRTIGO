#!/usr/bin/env python
import unittest
import sys
import subprocess
import logging
from topology import *

if sys.version < 2.7 :
    print "These unittests require python 2.7 for unittest.discover() to work"
    sys.exit(1)

class MininetTestsException(BaseException):
    pass

class MininetTests(unittest.TestCase):
    MN_bin = 'mn'
    MN_okay = False
    MN_stdout = 'mininet.stdout'
    MN_stderr = 'mininet.stderr'
    MN_switch_list = [ 'user', 'ovsk']
    #MN_switch_list = [ 'ovsk']


    def setUp(self):
        _format = "%(asctime)s  %(name)-15s: %(levelname)-8s: %(message)s"
        _datefmt = "%H:%M:%S"
        logging.basicConfig(filename="tests.log",
                level=logging.DEBUG,
                format=_format, datefmt=_datefmt)
        self.logger = logging.getLogger(self.__class__.__name__)
        self.mn_proc = None
        self.fv_proc = None
        self.log_stdout = open(MininetTests.MN_stdout, "a")
        self.log_stderr = open(MininetTests.MN_stderr, "a")
        if MininetTests.MN_okay:    # check cached result
            return      # FIXME : for some reason this doesn't cache
        MininetTests.MN_okay = self.hasWorkingMininet()
        if not MininetTests.MN_okay:
            self.noWorkingMininet()

    def noWorkingMininet(self):
        raise MininetTestsException(
            "\n\n\nMininet not working on this system: " + \
                "see %s and %s for details" % (
            MininetTests.MN_stdout, MininetTests.MN_stderr))

    def hasWorkingMininet(self):
        """ Probes different mn params and find a combo that works
            on this system """
        self.switch = None
        self.logger.info( "Testing to see if Mininet installation works")
        for switch in MininetTests.MN_switch_list :
            retcode = subprocess.call(["sudo", 
                            MininetTests.MN_bin, 
                            "--switch=%s"% switch,
                            "--test=pingall"],
                            stdout=self.log_stdout,
                            stderr=self.log_stderr,
                            )
            if retcode == 0 :
                self.switch = switch
                break
        return self.switch is not None

    def spawnMininet(self, switch=None, controller="remote", 
                            ip="127.0.0.1", port="16633", args=None):
        if switch is None:
            switch = self.switch
        mn_args = [ "sudo", 
                MininetTests.MN_bin,
                "--switch=%s" % switch,
                "--controller=%s" % controller,
                "--ip=%s" % ip,
                "--port=%s" % port]
        mn_args += args
        self.mn_proc = subprocess.Popen(mn_args, 
                        stdout=self.log_stdout,
                        stderr=self.log_stderr)


    def tearDown(self):
        if self.mn_proc:
            pass
        if self.fv_proc:
            pass
        self.log_stdout.close()
        self.log_stderr.close()

###########################################################

class MininetSelfTest(MininetTests):
    def runTest(self):
        pass


##################  main()  ###############################
        
if __name__ == '__main__':
    baseTest = MininetSelfTest()
    baseTest.run()
    if baseTest.MN_okay:
        unittest.main()        
    else:
        baseTest.noWorkingMininet()
