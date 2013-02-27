
#include <sys/syslog.h>

#include "org_flowvisor_log_Syslog.h"


/***
 * JNI wrapper around openlog(3)
 */

JNIEXPORT void JNICALL Java_org_flowvisor_log_Syslog_open
  (JNIEnv * env, jobject jobj, jint facility, jstring jident) {
        jboolean iscopy;
        // get a non-java string
        const char *ident = (*env)->GetStringUTFChars(
                                env, jident, &iscopy);
        openlog(ident,0,facility);
        // don't free non-java string!! syslog still uses it!
        // (*env)->ReleaseStringUTFChars(env, jident, ident);
  }


JNIEXPORT void JNICALL Java_org_flowvisor_log_Syslog_log
  (JNIEnv * env, jobject jobj, jint priority, jstring jmsg) {
        // get a non-java string
        jboolean iscopy;
        const char *msg = (*env)->GetStringUTFChars(
                                env, jmsg, &iscopy);
        syslog(priority, "%s",msg);
        // free non-java string
        (*env)->ReleaseStringUTFChars(env, jmsg, msg);
  }
