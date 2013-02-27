
# requires gmake

.PHONY: debug clean all

TOP=../..
package=org.flowvisor.log.Syslog
classfile=$(subst .,/,$(package)).class
headerfile=$(subst .,_,$(package)).h
classpath=$(TOP)/build
srcpath=$(TOP)/src
distpath=$(TOP)/dist

install=install

MYCFLAGS=-Wall


# Sun's default location on linux, for convenience
MYCFLAGS+=-I/usr/lib/jvm/java-6-sun/include/ -I/usr/lib/jvm/java-6-sun/include/linux -I/usr/lib/jvm/java-6-openjdk/include/linux -I/usr/lib/jvm/java-6-openjdk/include

all: libsyslog_jni.so

libsyslog_jni.so: syslog_jni.o
	gcc $(LDFLAGS) -fPIC -shared -Wl,-soname,libsyslog_jni.so -o $@ syslog_jni.o -lc
	cp $@ $(distpath)


syslog_jni.o: syslog_jni.c $(headerfile)
	gcc $(CFLAGS) $(MYCFLAGS) -fPIC -c -o $@ syslog_jni.c


$(headerfile): $(classpath)/$(classfile)
	javah -jni -classpath $(classpath) $(package)


$(classpath)/$(classfile): 
	@echo Need to build source tree before building JNI libraries && exit 1

clean:
	rm -f *.h
	rm -f *.o
	rm -f *.so

# assumes DSTDIR is set by calling process; namely ./scripts/install-script.sh
install:
	$(install) libsyslog_jni.so $(DSTDIR)


debug:
	@echo classfile=$(classfile)
	@echo headerfile=$(headerfile)
