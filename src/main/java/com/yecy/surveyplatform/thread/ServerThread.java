package com.yecy.surveyplatform.thread;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author: yechenyu
 * @create: 2020/1/5 上午9:24
 * @email: Yecynull@163.com
 * @version:
 * @descripe:
 **/
public class ServerThread extends Thread {

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
        		System.out.println("start to wait client"+ (mClientMap.size()+1)+" connect...");
        		socket = mServer.accept();
        	}catch(IOException e) {
        		errMessage = e.getMessage();
        	}finally {
                if (socket == null) {
                    System.out.println("ServerThread: connected fail "+ errMessage);
                    Set<Entry<String, ConnectionThread>> entrySets = mClientMap.entrySet();

                    Iterator<Entry<String, ConnectionThread>> iterator = entrySets.iterator();
					while(iterator.hasNext()){
						Entry<String, ConnectionThread> entry = iterator.next();
						ConnectionThread thread = entry.getValue();
						if(!thread.isDataConnected()){
							System.out.println("ServerThread: remove foreach client "+ entry.getKey());
							thread.destoryClient();
							iterator.remove();
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
									thread.destoryClient();
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
