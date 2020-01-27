package com.yecy.surveyplatform.thread;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import org.springframework.boot.system.ApplicationHome;


/**
 * @author: yechenyu
 * @create: 2020/1/5 上午9:24
 * @email: Yecynull@163.com
 * @version:
 * @descripe:
 **/
public class ConnectionThread extends Thread {

    private Socket socket;
    private InputStream is;
    private OutputStream os;
    private BufferedReader br;

    private static final String AUTH_STRING ="1234567890";
    private String clientHostName;
    private OnConnectionListener mListener;
    private boolean isRunning = false;
    private String javaHome;

    private String fileTransferHost;

    public ConnectionThread(Socket socket, OnConnectionListener listener) {
    	this.socket = socket;
    	mListener = listener;
    	clientHostName = socket.getRemoteSocketAddress().toString();
		ApplicationHome home = new ApplicationHome(getClass());
    	javaHome = home.getSource().getParentFile().getAbsolutePath().toString();
		System.out.println("Server home path="+ javaHome);
    }

    @Override
    public void run() {
    	isRunning = true;
    	if(socket != null) {
            System.out.println( "Client"+ clientHostName+ ": started...");
            if(!socket.isConnected()) {
            	System.out.println("Client"+ clientHostName+ ": connection status error");
            	mListener.onConnectFailed(clientHostName, 0);
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
					mListener.onConnectFailed(clientHostName, 0);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
            	return;
            }
            br = new BufferedReader(new InputStreamReader(is));
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os));
            System.out.println("Client"+ clientHostName+ ": start to read auth...");
            try {
				String auth = br.readLine();
				System.out.println("Client"+ clientHostName+ ": auth="+ auth);
				if(auth == null || !auth.equals(AUTH_STRING)){
					System.out.println( "Client"+ clientHostName+ ":  auth failed!");
					br.close(); bw.close();
					br = null; bw = null;
					socket.close();
					socket = null;
					mListener.onConnectFailed(clientHostName, 0);
					return;
				}
				System.out.println( "Client"+ clientHostName+ ": auth success, back data to client");
				bw.write(AUTH_STRING+ "\n");
				bw.flush();
            }catch(IOException e) {
				System.out.println( "Client"+ clientHostName+ ":  read auth failed "+ e.getMessage());
            }
            System.out.println( "Client"+ clientHostName+ ": start to read data...");
            /*
             * start to read data...
             * */
	        while (isRunning) {
	           //JSON`指令接收
                while (isRunning) {
                	try {
	                	String preData = br.readLine();
	                    if(preData != null) {
	                    	if(!parseCommand(preData)) {
	                    		break;
	                    	}
	                    }
		            } catch (IOException e) {
						System.out.println( "Client"+ clientHostName+ ": error status "+ e.getMessage());
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
					}
                }

                //数据传输
				try {
					ConnectionThread thread = ServerThread.mClientMap.get(fileTransferHost);
					int ret = -1, length = 0;
					byte[] result = new byte[1024];
					while ((ret = is.read(result)) != -1) {
						thread.writeData(result, ret);
						fileBw.write(result);
						fileBw.flush();
						length += ret;
						if(length >= fileLength)
							break;
					}
				}catch (IOException e){
					e.printStackTrace();
				}
	        }
    	}
    }

    public boolean isConnected(){
    	if(socket == null)
    		return false;
    	return socket.isConnected();
	}

    /**
     * 是否是可识别的指令
     * @param data
     * @return
     */
    private boolean parseCommand(final String data){
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
							if(key.equals(clientHostName))
								continue;
							clients.add(key);
						}
					}
					JSONArray job = new JSONArray();
					job.addAll(clients);
					json = new JSONObject();
					json.put(Constant.KEY_LIST, job.toString());
					byte[] result = (json.toString()+"\n").getBytes();
					writeData(result, result.length);
					job = null;
					json = null;
					return true;
				/**
				 * 获取远程设备位置信息
				 */
				}else if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_LOCATION)) {
					String host = json.getString(Constant.KEY_HOSTNAME);
					System.out.println("Client"+ clientHostName+ " message to "+ host);
					json.put(Constant.KEY_HOSTNAME, clientHostName);
					if(ServerThread.mClientMap.containsKey(host)){
						byte[] result = (json.toString() + "\n").getBytes();
						ConnectionThread thread = ServerThread.mClientMap.get(host);
						thread.writeData(result, result.length);
					}else{
						System.out.println("Client"+ host+ " cat't find out");
						byte[] result = (json.toString()+ "\n").getBytes();
						writeData(result, result.length);
					}
					return true;
				/**
				 * 获取服务器备份信息
				 */
				}else if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_DEVICE)){
					String hostname = json.getString(Constant.KEY_HOSTNAME);
					System.out.println("hostname="+ hostname);
					if(hostname != null){
						if(isFileExist(hostname, cmd)){
							//本地文件已存在，直接返回
							returnFileToClient(clientHostName, cmd);
						}else{
							ConnectionThread thread = ServerThread.mClientMap.get(clientHostName);
							byte[] result = (json.toString()+ "\n").getBytes();
							thread.writeData(result, result.length);
						}
					}else{
						//参数错误，错误处理
						System.out.println("target host is not fond");
						ConnectionThread thread = ServerThread.mClientMap.get(clientHostName);
						byte[] result = json.toString().getBytes();
						thread.writeData(result, result.length);
					}
					return true;
				/**
				 * 进行远程设备操作
				 */
				}else if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_DEVICE)
						|| cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_PHONE)
						|| cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_SCREEN)){
					String hostname = json.getString(Constant.KEY_HOSTNAME);
					System.out.println("hostname="+ hostname);
					if(hostname != null){
						if(!isFileExist(hostname, cmd)){
							if(ServerThread.mClientMap.containsKey(hostname)){
								ConnectionThread thread = ServerThread.mClientMap.get(hostname);
								if(thread != null){
									json.put(Constant.KEY_HOSTNAME, clientHostName);
									byte[] jsonData = (json.toString()+"\n").getBytes();
									thread.writeData(jsonData, jsonData.length);
								}else{
									//未找到目标终端，错误处理
									System.out.println("Client"+ hostname+ " is no fond");
								}
							}else{
								//未找到目标终端，错误处理
								System.out.println("Client"+ hostname+ " is no fond");
								ConnectionThread thread = ServerThread.mClientMap.get(clientHostName);
								byte[] result = json.toString().getBytes();
								thread.writeData(result, result.length);
							}
						}else{
							//本地文件已存在，直接返回
							returnFileToClient(clientHostName, cmd);
						}
					}else{
						//参数错误，错误处理
						System.out.println("target host is not fond");
						ConnectionThread thread = ServerThread.mClientMap.get(clientHostName);
						byte[] result = json.toString().getBytes();
						thread.writeData(result, result.length);
					}
					return true;
				/**
				 * 转发远程文件
				 */
				}else if(cmd.equalsIgnoreCase(Constant.CMD_RETURN_REMOTE_DEVICE)){
					String fileName = json.getString(Constant.KEY_FILE);
					fileLength = json.getLong(Constant.KEY_LENGTH);
					fileTransferHost = json.getString(Constant.KEY_HOSTNAME);
					System.out.println("Client"+ clientHostName+ " message to "+ fileTransferHost+ " filename="+ fileName+ ", length="+ fileLength);
					ConnectionThread thread = ServerThread.mClientMap.get(fileTransferHost);
					byte[] head = (data+"\n").getBytes();
					thread.writeData(head, head.length);

					fileBw = createFile(fileName);
					return false;
				/**
				 * 转发远程JSON 数据
				 */
				}else if(cmd.equalsIgnoreCase(Constant.CMD_TRANSFER_REMOTE_DATA)){
					String host = json.getString(Constant.KEY_HOSTNAME);
					System.out.println("Client"+ clientHostName+ " message to "+ host);
					json.put(Constant.KEY_HOSTNAME, clientHostName);
					ConnectionThread thread = ServerThread.mClientMap.get(host);
					byte[] head = (data+"\n").getBytes();
					thread.writeData(head, head.length);

					return true;
				/**
				 * 停止远程操作
				 */
				}else if(cmd.equalsIgnoreCase(Constant.CMD_STOP_REMOTE_OPERA)
						|| cmd.equalsIgnoreCase(Constant.CMD_STOP_REMOTE_PHONE)
						|| cmd.equalsIgnoreCase(Constant.CMD_STOP_REMOTE_SCREEN)) {
					String hostname = json.getString(Constant.KEY_HOSTNAME);
					System.out.println("hostname="+ hostname);
					if(hostname != null){
						if(ServerThread.mClientMap.containsKey(hostname)){
							ConnectionThread thread = ServerThread.mClientMap.get(hostname);
							if(thread != null){
								byte[] jsonData = (data+"\n").getBytes();
								thread.writeData(jsonData, jsonData.length);
							}else{
								//未找到目标终端，错误处理
								System.out.println("Client"+ hostname+ " is no fond");
							}
						}else{
							//未找到目标终端，错误处理
							System.out.println("Client"+ hostname+ " is no fond");
						}
					}else{
						//参数错误，错误处理
						System.out.println("target host is not fond");
					}
					return true;
				}
			} catch (JSONException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
				return true;
			}
        }
        return true;
    }

    private boolean isFileExist(String hostname, String cmd){
    	File phone = new File(javaHome+ "/"+ Constant.FILE_PHONE);
    	File screen = new File(javaHome+ "/"+ Constant.FILE_SCREEN);
    	System.out.println("file phone exist:"+ phone.exists()+ " file screen exist:"+ screen.exists());
    	if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_DEVICE)
			&& (phone.exists() || screen.exists())){
				return true;
		}
		if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_PHONE) && phone.exists()){
			return true;
		}
		if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_SCREEN) && screen.exists()){
			return true;
		}
    	return false;
	}

	private long fileLength = 0;
	private FileOutputStream fileBw;
	private FileOutputStream createFile(String fileName){
		File file = new File(javaHome+ "/"+ fileName);
		try {
			if (!file.exists())
				file.createNewFile();
			return new FileOutputStream(file);
		}catch (FileNotFoundException e){
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private void returnFileToClient(String hostname, String cmd){
		System.out.println("Client"+ hostname+ " start to transfer file...");
		ConnectionThread thread = ServerThread.mClientMap.get(hostname);
		File phone = new File(javaHome+ "/"+ Constant.FILE_PHONE);
		if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_DEVICE)
				|| cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_PHONE)) {
			if (phone.exists()) {
				writeFileToClient(thread, phone, hostname);
			}
		}
		if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_DEVICE)
				|| cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_PHONE)) {
			File screen = new File(javaHome + "/" + Constant.FILE_SCREEN);
			if (screen.exists()) {
				writeFileToClient(thread, screen, hostname);
			}
		}
	}

	private void writeFileToClient(ConnectionThread thread, File root, String hostname){
    	FileInputStream fis = null;
		try {
			fis = new FileInputStream(root);
			JSONObject json = new JSONObject();
			json.put(Constant.KEY_CMD, Constant.CMD_RETURN_REMOTE_DEVICE);
			json.put(Constant.KEY_HOSTNAME, hostname);
			json.put(Constant.KEY_FILE, root.getName());
			json.put(Constant.KEY_LENGTH, root.length());

			String temp = json.toString()+ "\n";
			byte[] data = temp.getBytes();
			thread.writeData(data, data.length);
			Thread.sleep(100);

			int result = -1;
			byte[] recv = new byte[1024];
			while ((result = fis.read(recv)) != -1) {
				thread.writeData(recv, result);
			}
			System.out.println("Client"+ hostname+ " transfer success!");
//			root.delete();
//			root = null;
		}catch (IOException e){
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			if(fis != null){
				try {
					fis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
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

    public boolean isDataConnected(){
		try {
			socket.sendUrgentData(0xff);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

    public void destoryClient(){
    	isRunning = false;
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
		if(br != null){
			try {
				br.close();
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
