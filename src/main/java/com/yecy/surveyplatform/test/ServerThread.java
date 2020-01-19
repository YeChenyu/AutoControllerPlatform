package com.yecy.surveyplatform.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.tomcat.jni.Address;

/**
 * @author: yechenyu
 * @create: 2020/1/5 上午9:24
 * @email: Yecynull@163.com
 * @version:
 * @descripe:
 **/
public class ServerThread extends Thread {

    private static final String TAG = ServerThread.class.getSimpleName();

    public static final String SERVER_IP = "101.133.174.68";
    public static final int SERVER_PORT = 8080;
    public static final int SERVER_CONNECT_TIMEOUT = 15*1000;

    private ServerSocket mServer;

    public static final Map<String, ConnectionThread> mClientMap = new HashMap<String, ConnectionThread>();

    public ServerThread() {
    }

    @Override
    public void run() {
    	try {
	    	mServer = new ServerSocket(SERVER_PORT);
	        mServer.setSoTimeout(SERVER_CONNECT_TIMEOUT);
    	}catch(IOException e) {
    		e.printStackTrace();
    	}

    	String errMessage = null;
        while (true) {
        	Socket socket = null;
        	try {
        		System.out.println("ServerThread: start to wait client"+ (mClientMap.size()+1)+" connect...");
        		socket = mServer.accept();
        	}catch(IOException e) {
        		errMessage = e.getMessage();
        	}finally {
                if (socket == null) {
                    System.out.println("ServerThread: connected fail "+ errMessage);
                    Set<String> keySet = mClientMap.keySet();
					for(String key : keySet){
						ConnectionThread thread = mClientMap.get(key);
						if(!thread.isDataConnected()){
							System.out.println("ServerThread: remove foreach client "+ key);
							thread.destoryClient();
							mClientMap.remove(key, thread);
						}
					}
                    try {
						Thread.sleep(3*1000);
					} catch (InterruptedException e1) {
						// TODO 自動生成された catch ブロック
						e1.printStackTrace();
					}
                    continue;
                }
        	}

        	try {
	        	socket.setSoTimeout(SERVER_CONNECT_TIMEOUT);
                System.out.println( "ServerThread: connected success!");
                SocketAddress address = socket.getRemoteSocketAddress();
                System.out.println( "ServerThread: client hostname="+ address.toString());
                if(address != null) {
	                final ConnectionThread mConnection = new ConnectionThread(socket, new OnConnectionListener() {
						@Override
						public void onConnectFailed(String hostname, int port) {
							// TODO 自動生成されたメソッド・スタブ
							ConnectionThread thread = mClientMap.get(hostname);
							if(thread != null) {
								if(mClientMap.containsKey(hostname)) {
									System.out.println("ServerThread: remove client "+ hostname);
									mClientMap.remove(hostname, thread);
								}
								thread.interrupt();
								thread = null;
							}
						}
	                });
	                String key = address.toString();
	                mClientMap.put(key, mConnection);
	                mConnection.start();
                }
        	}catch(IOException e) {
        		e.printStackTrace();
        	}
        }
    }
}
