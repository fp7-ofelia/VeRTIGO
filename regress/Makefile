all:
	make -C mininet-tests

tests:
	./tests.sh && make -C mininet-tests tests

clean:
	rm -f flowvisor.out fv_regress.log out valgrind* core* fvregress.pyc flowvisor.log flowvisor.stats \
		tests.results 
	make -C mininet-tests clean
