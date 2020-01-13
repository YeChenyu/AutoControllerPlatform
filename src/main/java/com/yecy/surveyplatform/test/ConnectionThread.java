package com.yecy.surveyplatform.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;


/**
 * @author: yechenyu
 * @create: 2020/1/5 上午9:24
 * @email: Yecynull@163.com
 * @version:
 * @descripe:
 **/
public class ConnectionThread extends Thread {

    private static final String TAG = ConnectionThread.class.getSimpleName();

    private Socket socket;
    private InputStream is;
    private OutputStream os;
    private BufferedReader br;

    private static final String AUTH_STRING ="1234567890";
    private String hostname;
    private OnConnectionListener mListener;
    private boolean isRunning = false;

    public ConnectionThread(Socket socket, OnConnectionListener listener) {
    	this.socket = socket;
    	mListener = listener;
    	hostname = socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void run() {
    	isRunning = true;
    	if(socket != null) {
            System.out.println( "Client"+ hostname+ ": started...");
            if(!socket.isConnected()) {
            	System.out.println("Client"+ hostname+ ": connection status error");
            	mListener.onConnectFailed(hostname, 0);
            	return;
            }
            try {
	            is = socket.getInputStream();
	            os = socket.getOutputStream();
            }catch(IOException e) {
            	e.printStackTrace();
				try {
					socket.close();
					socket = null;
					mListener.onConnectFailed(hostname, 0);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
            	return;
            }
            br = new BufferedReader(new InputStreamReader(is));
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os));
            System.out.println("Client"+ hostname+ ": start to read auth...");
            try {
				String auth = br.readLine();
				System.out.println("Client"+ hostname+ ": auth="+ auth);
				if(auth == null || !auth.equals(AUTH_STRING)){
					System.out.println( "Client"+ hostname+ ":  auth failed!");
					br.close(); bw.close();
					br = null; bw = null;
					socket.close();
					socket = null;
					mListener.onConnectFailed(hostname, 0);
					return;
				}
				System.out.println( "Client"+ hostname+ ": auth success, back data to client");
				bw.write(AUTH_STRING+ "\n");
				bw.flush();
            }catch(IOException e) {
				System.out.println( "Client"+ hostname+ ":  read auth failed "+ e.getMessage());
            }
	        while (isRunning) {
	            try {
	                System.out.println( "Client"+ hostname+ ": start to read data...");
	                /*
	                 * start to read data...
	                 * */
	                long start = System.currentTimeMillis();
	                int timeout = 5;
	                while (true) {
	                	String preData = br.readLine();
	                    if(preData != null)
	                    	parseCommand(preData);
	                    long time = System.currentTimeMillis() - start;
	                    if(time/1000 > timeout)
	                    	break;
	                }
	            } catch (IOException e) {
					System.out.println( "Client"+ hostname+ ": error status "+ e.getMessage());
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
				}
	        }
    	}
    }

    public boolean isConnected(){
    	if(socket == null)
    		return false;
    	return socket.isConnected();
	}

    private void parseCommand(final String data){
        if(data != null){
        	System.out.println("parseCommand: length="+ data.getBytes().length+ ", data="+ data);
            try {
				JSONObject json = JSONObject.parseObject(data);
				json.getJSONObject(data);
				String cmd = json.getString(Constant.KEY_CMD);
				System.out.println("client cmd: "+ cmd);
				/**
				 * 获取远程设备列表
				 */
				if(cmd.equalsIgnoreCase(Constant.CMD_SEARCH_REMOTE_LIST)) {
					Set<String> keys = ServerThread.mClientMap.keySet();
					List<String> clients = new ArrayList<String>();
					if(keys!=null && keys.size() >1) {
						System.out.println("client count is: "+ keys.size());
						for(String key : keys) {
							clients.add(key);
						}
					}
					JSONArray job = new JSONArray();
					job.addAll(clients);
					json = new JSONObject();
					json.put(Constant.KEY_LIST, job.toString());
					byte[] result = json.toString().getBytes();
					writeData(result, result.length);
					job = null;
					json = null;
				/**
				 * 进行远程设备操作
				 */
				}else if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_DEVICE)){
					String hostname = json.getString(Constant.KEY_HOSTNAME);
					if(hostname != null){
						if(!isFileExist(hostname)){
							Set<String> keys = ServerThread.mClientMap.keySet();
							if(keys.contains(hostname)){
								ConnectionThread thread = ServerThread.mClientMap.get(hostname);
								if(thread != null){
									byte[] jsonData = data.getBytes();
									thread.writeData(jsonData, jsonData.length);
								}else{
									//未找到目标终端，错误处理
								}
							}else{
								//未找到目标终端，错误处理
							}
						}else{
							//本地文件已存在，直接返回
						}
					}else{
						//参数错误，错误处理
					}
				/**
				 * 转发远程信息
				 */
				}else if(cmd.equalsIgnoreCase(Constant.CMD_RETURN_REMOTE_DEVICE)){
					String fileName = json.getString(Constant.KEY_FILE);
					long length = json.getLong(Constant.KEY_LENGTH);
					String main = json.getString(Constant.KEY_HOSTNAME);
					System.out.println("Client"+ hostname+ " message to "+ main+ " filename="+ fileName+ ", length="+ length);
					String result = null;
					ConnectionThread thread = ServerThread.mClientMap.get(main);
					byte[] head = data.getBytes();
					thread.writeData(head, head.length);
					if(thread != null){
						try {
							while ((result = br.readLine()) != null) {
								byte[] ret = result.getBytes();
								thread.writeData(ret, ret.length);
							}
							System.out.println("Client"+ hostname+ " transfer success!");
						}catch (IOException e){
							e.printStackTrace();
						}
					}
				}
			} catch (JSONException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
        }
    }

    private boolean isFileExist(String hostname){
    	
    	return false;
	}


    public void writeData(byte[] data, int length){
        if(os != null){
            try {
                os.write(data, 0, length);
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void destoryClient(){
    	if(socket != null){
			try {
				socket.shutdownInput();
				socket.shutdownOutput();
				socket.close();
				socket = null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

    public void stopConnectionThread() {
    	isRunning = false;
    }

    public boolean isRunning() {
    	return isRunning;
    }
}
