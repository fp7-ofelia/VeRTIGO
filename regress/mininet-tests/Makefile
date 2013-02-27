all:

doc:

install:

tests:
	@echo Mininet tests require root priviledges to run so they 
	@echo are not run by default.  To run the mininet tests, first
	@echo install mininet and then run:
	@echo
	@echo "      cd regress/mininet-tests && sudo make minitests"

minitests:
	@echo Minitests assumes you are root
	./mininettests.py --verbose

clean:
	@rm -f mininet.stderr mininet.stdout tests.log *.pyc
