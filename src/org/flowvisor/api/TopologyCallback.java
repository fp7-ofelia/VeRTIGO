package org.flowvisor.api;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.ofswitch.TopologyController;

public class TopologyCallback implements Runnable {

	String URL;
	String cookie;
	String methodName;
	
	String httpBasicUserName;
	String httpBasicPassword;	
	
	XmlRpcClientConfigImpl config;
	XmlRpcClient client;

	public TopologyCallback(String uRL, String methodName,String cookie) {
		super();
		URL = uRL;
		this.methodName=methodName;
		this.cookie = cookie;
		
		int indexAt;

		indexAt=uRL.indexOf("@");
		if (indexAt>3){//means there is a username/password encoded in the URL
			String newString;
			newString=uRL.substring(uRL.indexOf("://")+3,indexAt-uRL.indexOf("://")+5);
			this.httpBasicUserName=newString.substring(0,newString.indexOf(":"));
			this.httpBasicPassword=newString.substring(newString.indexOf(":")+1);
		}
		else{
			this.httpBasicUserName="";
			this.httpBasicPassword="";
		}
		
	}

	public void spawn() {
		new Thread(this).start();
	}

	public String getURL() {		
		return this.URL;
	}

	public String getMethodName(){
		return this.methodName;	
	}
	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		this.installDumbTrust();
		config = new XmlRpcClientConfigImpl();
		URL urlType;
		try {
			urlType = new URL(this.URL);
			config.setServerURL(urlType);
		} catch (MalformedURLException e) {
			// should never happen; we test this on input
			throw new RuntimeException(e);
		}
		config.setEnabledForExtensions(true);
		
		if (httpBasicUserName!=null && httpBasicUserName!="" && httpBasicPassword!="" && httpBasicPassword!=null)
		{
			config.setBasicUserName(httpBasicUserName);
			config.setBasicPassword(httpBasicPassword);
		}
		
		client = new XmlRpcClient();
		// client.setTransportFactory(new
		// XmlRpcCommonsTransportFactory(client));
		// client.setTransportFactory(new )
		client.setConfig(config);
		try {
			String call = urlType.getPath();
			if (call.startsWith("/"))
				call = call.substring(1);
			//this.client.execute(this.methodName, new Object[] { cookie });
this.client.execute(this.methodName,new Object[]{ null});		
	} catch (XmlRpcException e) {
			FVLog.log(LogLevel.WARN, TopologyController.getRunningInstance(),
					"topoCallback to URL=" + URL + " failed: " + e);
		}

	}

	public void installDumbTrust() {

		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs,
					String authType) {
				// Trust always
			}

			public void checkServerTrusted(X509Certificate[] certs,
					String authType) {
				// Trust always
			}
		} };
		try {
			// Install the all-trusting trust manager
			SSLContext sc = SSLContext.getInstance("SSL");
			// Create empty HostnameVerifier
			HostnameVerifier hv = new HostnameVerifier() {
				public boolean verify(String arg0, SSLSession arg1) {
					return true;
				}
			};

			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection
					.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(hv);
		} catch (KeyManagementException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

}
