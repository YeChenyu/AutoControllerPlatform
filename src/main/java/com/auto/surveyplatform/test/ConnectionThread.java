package com.auto.surveyplatform.test;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.auto.surveyplatform.transfer.HandleProtocol;
import com.auto.surveyplatform.transfer.RespResult;
import com.auto.surveyplatform.util.StringUtil;
import org.springframework.boot.system.ApplicationHome;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * @author: xxx
 * @create: 2020/1/5 上午9:24
 * @email: xxx.xxx.xxx
 * @version:
 * @descripe:
 **/
public class ConnectionThread extends Thread {

    private Socket socket;
    private BufferedWriter bw;
    private BufferedReader br;

    private static final String AUTH_STRING ="1234567890";
    private String clientHostName;
    private OnConnectionListener mListener;
    private boolean isRunning = false;
    private String javaHome;
    private boolean transferFile = false;

    private String fileTransferHost;
    private HandleProtocol mHandleProtocol = new HandleProtocol();

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
            InputStream is = null;
            OutputStream os = null;
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
            bw = new BufferedWriter(new OutputStreamWriter(os));
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
				transferFile = false;
                while (!transferFile) {
                	try {
	                	String preData = br.readLine();
						if(transferFile) break;
	                	if(preData == null){
							try {
								Thread.sleep(300);
								continue;
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
	                	System.out.println("readline="+ StringUtil.hexStr2Str(preData));
	                	System.out.println("readline="+ preData);
	                	RespResult result = mHandleProtocol.unPackageProtocol(StringUtil.hexStr2Bytes(preData));
						if (result != null) {
							System.out.println("readClientData: " + result.toString());
							byte[] cmd = StringUtil.hexStr2Bytes(result.getCmdCode());
							byte[] data = result.getParams();
							//数据解析成功
							if (result.getExecuteId() == 0) {
								//响应包，直接转发
								if(result.getRespCode().equals("3030")) {
									if(data != null){
										JSONObject json = JSONObject.parseObject(StringUtil.byteToStr(data));
										if(json.containsKey(Constant.KEY_HOSTNAME)){
											String host = json.getString(Constant.KEY_HOSTNAME);
											byte[] temp = StringUtil.hexStr2Bytes(preData);
											ConnectionThread thread = ServerThread.mClientMap.get(host);
											thread.writeData(temp, temp.length);
										}
									}
								//其他，解析指令集
								}else{
									byte[] response = parseCommand(result.getCmdCode(), StringUtil.byteToStr(result.getParams()));
									//无需转发，马上回复响应
									if (response != null) {
										byte[] requestData = mHandleProtocol.packResponseProtocol(cmd, StringUtil.hexStr2Bytes("3030")
												, response);
										writeData(requestData, requestData.length);
										//转发消息
									} else {

									}
								}
							} else {
								if(cmd == null) {
									cmd = StringUtil.hexStr2Bytes(Constant.CMD_UNKNOWN);
								}
								byte[] requestData = mHandleProtocol.packResponseProtocol(cmd, StringUtil.hexStr2Bytes("3032"), null);
								writeData(requestData, requestData.length);
							}
						} else {
							System.out.println("readClientData: null");
						}
//	                    if(preData != null) {
//	                    	if(!parseCommand(preData)) {
//	                    		break;
//	                    	}
//	                    }
		            } catch (IOException e) {
						System.out.println( "Client"+ clientHostName+ ":"+ e.getMessage());
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
    private byte[] parseCommand(final String cmd, String data){
        if(data != null){
        	System.out.println("parseCommand: cmd="+ cmd+ ", data="+ data);
            try {
				JSONObject json = JSONObject.parseObject(data);
				json.getJSONObject(data);
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
					return json.toString().getBytes();
				/**
				 * 获取远程设备位置信息
				 */
				}
				else if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_LOCATION)) {
					String host = json.getString(Constant.KEY_HOSTNAME);
					System.out.println("Client"+ clientHostName+ " message to "+ host);
					json.put(Constant.KEY_HOSTNAME, clientHostName);
					if(ServerThread.mClientMap.containsKey(host)){
						byte[] arrData = json.toString().getBytes();
						byte[] arrCmd = StringUtil.hexStr2Bytes(cmd);
						byte[] temp = mHandleProtocol.packRequestProtocol(arrCmd, arrData, (byte)0x3f);
						ConnectionThread thread = ServerThread.mClientMap.get(host);
						thread.writeData(temp, temp.length);
						return null;
					}else{
						System.out.println("Client"+ host+ " cat't find out");
						return json.toString().getBytes();
					}
				/**
				 * 获取服务器备份信息
				 */
				}
				else if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_DEVICE)){
					String hostname = json.getString(Constant.KEY_HOSTNAME);
					System.out.println("hostname="+ hostname);
					if(hostname != null){
						if(isFileExist(hostname, cmd)){
							//本地文件已存在，直接返回
							returnNewFileToClient(hostname, clientHostName, cmd);
							return null;
						}else{
							return json.toString().getBytes();
						}
					}else{
						//参数错误，错误处理
						System.out.println("target host is not fond");
						return json.toString().getBytes();
					}
				/**
				 * 进行远程设备操作
				 */
				}
				else if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_DEVICE)
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
									byte[] arrData = json.toString().getBytes();
									byte[] arrCmd = StringUtil.hexStr2Bytes(cmd);
									byte[] temp = mHandleProtocol.packRequestProtocol(arrCmd, arrData, (byte)0x3f);
									thread.writeData(temp, temp.length);
								}else{
									//未找到目标终端，错误处理
									System.out.println("Client"+ hostname+ " is no fond");
									return json.toString().getBytes();
								}
							}else{
								//未找到目标终端，错误处理
								System.out.println("Client"+ hostname+ " is no fond");
								return json.toString().getBytes();
							}
						}else{
							//本地文件已存在，直接返回
							returnNewFileToClient(hostname, clientHostName, cmd);
							return null;
						}
					}else{
						//参数错误，错误处理
						System.out.println("target host is not fond");
						return json.toString().getBytes();
					}
				/**
				 * 转发远程文件
				 */
				}
				else if(cmd.equalsIgnoreCase(Constant.CMD_RETURN_REMOTE_DEVICE)){
					String fileName = json.getString(Constant.KEY_FILE);
					fileLength = json.getLong(Constant.KEY_LENGTH);
					fileTransferHost = json.getString(Constant.KEY_HOSTNAME);
					System.out.println("Client"+ clientHostName+ " message to "+ fileTransferHost+ " filename="+ fileName+ ", length="+ fileLength);
					ConnectionThread thread = ServerThread.mClientMap.get(fileTransferHost);
					byte[] arrData = json.toString().getBytes();
					byte[] arrCmd = StringUtil.hexStr2Bytes(cmd);
					byte[] temp = mHandleProtocol.packRequestProtocol(arrCmd, arrData, (byte)0x3f);
					thread.writeData(temp, temp.length);
					//被控端传送的文件
					fileBw = createFile(clientHostName, fileName);
					transferFile = true;
					return null;
				/**
				 * 转发远程JSON 数据
				 */
				}
				else if(cmd.equalsIgnoreCase(Constant.CMD_TRANSFER_REMOTE_DATA)){
					String host = json.getString(Constant.KEY_HOSTNAME);
					System.out.println("Client"+ clientHostName+ " message to "+ host);
					json.put(Constant.KEY_HOSTNAME, clientHostName);
					ConnectionThread thread = ServerThread.mClientMap.get(host);
					byte[] arrData = json.toString().getBytes();
					byte[] arrCmd = StringUtil.hexStr2Bytes(cmd);
					byte[] temp = mHandleProtocol.packRequestProtocol(arrCmd, arrData, (byte)0x3f);
					thread.writeData(temp, temp.length);
					return null;
				/**
				 * 停止远程操作
				 */
				}
				else if(cmd.equalsIgnoreCase(Constant.CMD_STOP_REMOTE_OPERA)
						|| cmd.equalsIgnoreCase(Constant.CMD_STOP_REMOTE_PHONE)
						|| cmd.equalsIgnoreCase(Constant.CMD_STOP_REMOTE_SCREEN)) {
					String hostname = json.getString(Constant.KEY_HOSTNAME);
					System.out.println("hostname="+ hostname);
					if(hostname != null){
						if(ServerThread.mClientMap.containsKey(hostname)){
							json.put(Constant.KEY_HOSTNAME, clientHostName);
							ConnectionThread thread = ServerThread.mClientMap.get(hostname);
							if(thread != null){
								byte[] arrData = json.toString().getBytes();
								byte[] arrCmd = StringUtil.hexStr2Bytes(cmd);
								byte[] temp = mHandleProtocol.packRequestProtocol(arrCmd, arrData, (byte)0x3f);
								thread.writeData(temp, temp.length);
							}else{
								//未找到目标终端，错误处理
								System.out.println("Client"+ hostname+ " is no fond");
								return json.toString().getBytes();
							}
						}else{
							//未找到目标终端，错误处理
							System.out.println("Client"+ hostname+ " is no fond");
							return json.toString().getBytes();
						}
					}else{
						//参数错误，错误处理
						System.out.println("target host is not fond");
						return json.toString().getBytes();
					}
					return null;
				}
			} catch (JSONException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
				return null;
			}
        }
        return null;
    }

    private boolean isFileExist(String hostname, String cmd){
    	if(hostname == null){
    		return false;
		}
    	String[] host = hostname.split(":");
    	File[] phone = new File(javaHome+ host[0]+ "/Phone").listFiles();
		File[] screen = new File(javaHome+ host[0]+ "/Screen").listFiles();

    	if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_DEVICE)
			&& ((phone!=null && phone.length>0) || (screen!=null && screen.length>0))){
			System.out.println("file phone exist:"+ true+ " file screen exist:"+ true);
				return true;
		}
		if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_PHONE) && (phone!=null && phone.length>0)){
			System.out.println("file phone exist: true");
			return true;
		}
		if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_SCREEN) && (screen!=null && screen.length>0)){
			System.out.println("file screen exist: true");
			return true;
		}
    	return false;
	}

	private long fileLength = 0;
	private FileOutputStream fileBw;
	private FileOutputStream createFile(String hostname, String fileName){
		File file = null;

		String[] host = hostname.split(":");
		if(fileName.contains("phone")){
			File root = new File(javaHome+ host[0]+ "/Phone");
			if(!root.exists()){
				root.mkdirs();
			}
			file = new File(root.getAbsolutePath()+ "/"+ fileName);
		}else{
			File root = new File(javaHome+ host[0]+ "/Screen");
			if(!root.exists()){
				root.mkdirs();
			}
			file = new File(root.getAbsolutePath()+ "/"+ fileName);
		}
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

	private void returnFileToClient(String tartHost, String homeHost, String cmd){
		System.out.println("Client"+ tartHost+ " start to transfer file...");
		ConnectionThread thread = ServerThread.mClientMap.get(homeHost);
		String[] host = tartHost.split(":");
		if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_DEVICE)
				|| cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_PHONE)) {
			File phone = new File(javaHome+ host[0]+ "/Phone/"+ Constant.FILE_PHONE);
			if (phone.exists()) {
				writeFileToClient(thread, phone, tartHost);
			}
		}
		if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_DEVICE)
				|| cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_PHONE)) {
			File screen = new File(javaHome+ host[0]+ "/Screen/"+ Constant.FILE_SCREEN);
			if (screen.exists()) {
				writeFileToClient(thread, screen, tartHost);
			}
		}
	}

	private void returnNewFileToClient(String tartHost, String homeHost, String cmd){
		System.out.println("Client"+ tartHost+ " start to transfer file...");
		ConnectionThread thread = ServerThread.mClientMap.get(homeHost);
		String[] host = tartHost.split(":");
		if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_DEVICE)
				|| cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_PHONE)) {
			File phone = fetchNewFile(javaHome+ host[0]+ "/Phone");
			if (phone != null) {
				writeFileToClient(thread, phone, tartHost);
			}
		}
		if(cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_DEVICE)
				|| cmd.equalsIgnoreCase(Constant.CMD_FETCH_REMOTE_PHONE)) {
			File screen = fetchNewFile(javaHome+ host[0]+ "/Screen");
			if (screen != null) {
				writeFileToClient(thread, screen, tartHost);
			}
		}
	}

	private File fetchNewFile(String path){
		File root = new File(path);
		File[] files = root.listFiles();
		if(files!=null && files.length>0){
			return files[0];
		}
		return null;
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

			byte[] arrData = json.toString().getBytes();
			byte[] arrCmd = StringUtil.hexStr2Bytes(Constant.CMD_RETURN_REMOTE_DEVICE);
			byte[] temp = mHandleProtocol.packRequestProtocol(arrCmd, arrData, (byte)0x3f);
			thread.writeData(temp, temp.length);
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
        if(bw != null){
            try {
				bw.flush();
				System.out.println( "write:"+ StringUtil.byte2HexStr(data));
				bw.write(StringUtil.byte2HexStr(data)+ "\n");
				bw.flush();
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
