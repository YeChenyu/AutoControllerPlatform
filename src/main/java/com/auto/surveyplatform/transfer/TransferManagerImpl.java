package com.auto.surveyplatform.transfer;


import com.auto.surveyplatform.util.StringUtil;

import java.io.*;
import java.net.Socket;

/**
 */
public class TransferManagerImpl extends TransferManager {

    private final String TAG = "Transfer";

    private static final long OVERTIME = 1;
    private static final int SLEEP_TIME = 1;//修改读取是的延时时间为1ms
    private boolean isLogined = false;

    private Socket mSocket = null;
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private HandleProtocol handleProtocol = new HandleProtocol();
//    private Logger LOG = Logger.getLogger(TransferManager.class);

    public TransferManagerImpl() { }

    private static TransferManagerImpl self = null;

    public static TransferManagerImpl getInstance() {

        if (self == null) {
            synchronized (TransferManagerImpl.class) {
                if (self == null) {
                    self = new TransferManagerImpl();
                }
            }
        }
        return self;
    }
    /**
     * 连接K21安全模块
     *
     * @return
     */
    @Override
    public boolean initMasterDevice(Socket socket) {
//        LOG.trace("initMasterDevice executed");
        if(socket == null){
//            LOG.error("socket is null");
            return false;
        }
        try {
            mSocket = socket;
            if(!testCommunicate()){
//                LOG.error("the connection is unactive");
                return false;
            }
            mInputStream = socket.getInputStream();
            mOutputStream = socket.getOutputStream();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean destoryDevice() {
        return false;
    }

    /**
     * 与K21通信的通用接口
     *
     * @param cmd     指令号
     * @param params  参数
     * @param timeout 超时时间，单位:ms
     * @return
     * @throws SDKException
     */
    @Override
    public synchronized RespResult translate(byte[] cmd, byte[] params, int timeout) throws SDKException {
//        LOG.trace("translate executed");
        login();
        // 封装请求报文
        byte[] requestData = handleProtocol.packRequestProtocol(cmd, params, (byte)0x2f);
        try {
            write(requestData);
            RespResult result = readData(cmd, timeout);
            int ret = result.getExecuteId();
            if (ret == 0) {
                return result;
            } else if (ret == -1) {
                throw new SDKException(SDKException.COMMUNICATE_ERROR_BACK_CMD_ERROR);
            } else if (ret == -2) {
                throw new SDKException(SDKException.COMMUNICATE_ERROR_LRC_ERROR);
            } else {
                throw new SDKException(SDKException.COMMUNICATE_ERROR_OTHER);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new SDKException(SDKException.COMMUNICATE_ERROR_IO_ERROR);
        }
    }

    private void write(byte[] data) throws IOException {
//        if (mInputStream != null) {
//            while (mInputStream.available() > 0)
//                mInputStream.read();
//        }
        if (mOutputStream != null) {
            mOutputStream.flush();
            System.out.println( "write:"+ StringUtil.byte2HexStr(data));
            byte[] temp = new byte[data.length+1];
            System.arraycopy(data, 0, temp, 0, data.length);
            temp[data.length] = 0x0d;
            mOutputStream.write(temp);
            mOutputStream.flush();
        }
    }


    @Override
    public RespResult readClientData(int timeout) throws SDKException, IOException {
        // 取STX
        byte[] STXBuff = new byte[]{(byte) 0x02};
        int stxRet = read(STXBuff, STXBuff.length, timeout < OVERTIME ? OVERTIME : timeout);
        if (stxRet != 0) {
            if (stxRet == 2) {
                isLogined = false;//K21挂掉时，设置为需要重新登录
                throw new SDKException(SDKException.COMMUNICATE_ERROR_TIMEOUT);
            }
            if (stxRet == 4) {
                throw new SDKException(SDKException.COMMUNICATE_ERROR_IO_ERROR);
            }
            if (stxRet == 3) {
                throw new SDKException(SDKException.COMMUNICATE_ERROR_STOP);
            }
        }
        if (STXBuff[0] == 0) {
            System.out.println("stx 首字节读到0, 进行第二次读取...");
            stxRet =  read(STXBuff, STXBuff.length, OVERTIME);
            if (stxRet != 0) {
                if (stxRet == 2) {
                    isLogined = false;//K21挂掉时，设置为需要重新登录
                    throw new SDKException(SDKException.COMMUNICATE_ERROR_TIMEOUT);
                }
                if (stxRet == 4) {
                    throw new SDKException(SDKException.COMMUNICATE_ERROR_IO_ERROR);
                }
                if (stxRet == 3) {
                    throw new SDKException(SDKException.COMMUNICATE_ERROR_STOP);
                }
            }
        }
//        System.out.println("STXBuff[0]="+ STXBuff[0]);
        if (STXBuff[0] == 0x02) {
            byte[] dataLenBuff = new byte[2];
            int dataLenRet = read(dataLenBuff, dataLenBuff.length, 2 * 1000);
            if (dataLenRet != 0) {
                throw new SDKException(SDKException.COMMUNICATE_ERROR_TIMEOUT);
            }
            int dataLen = Integer.valueOf(StringUtil.byte2HexStr(dataLenBuff));
            byte[] dataBuff = new byte[dataLen + 2];
            int dataRet = read(dataBuff, dataBuff.length, 2 * 1000);
            if (dataRet != 0) {
                throw new SDKException(SDKException.COMMUNICATE_ERROR_TIMEOUT);
            }

//            System.out.println("dataBuff[dataBuff.length - 3]="+ dataBuff[dataBuff.length - 2]);
            if (dataBuff[dataBuff.length - 2] == 0x03) {
                RespResult result = handleProtocol.unPackageRequestProtocol(dataBuff);
                return result;
            } else {
                throw new SDKException(SDKException.COMMUNICATE_ERROR_ETX_ERROR);
            }
        } else {
            throw new SDKException(SDKException.COMMUNICATE_ERROR_STX_ERROR);
        }
    }

    @Override
    public void writeResponseData(byte[] cmd, String response, byte[] params) throws SDKException {
//        LOG.trace("translate executed");
        login();
        // 封装请求报文
        byte[] respCode = StringUtil.hexStr2Bytes(response);
        byte[] requestData = handleProtocol.packResponseProtocol(cmd, respCode, params);
        try {
            write(requestData);
        } catch (IOException e) {
            e.printStackTrace();
            throw new SDKException(SDKException.COMMUNICATE_ERROR_IO_ERROR);
        }
    }

    private RespResult readData(byte[] cmd, int timeout) throws SDKException, IOException {
        // 取STX
        byte[] STXBuff = new byte[]{(byte) 0x33};
        int stxRet = read(STXBuff, STXBuff.length, timeout < OVERTIME ? OVERTIME : timeout);
        if (stxRet != 0) {
            if (stxRet == 2) {
                isLogined = false;//K21挂掉时，设置为需要重新登录
                throw new SDKException(SDKException.COMMUNICATE_ERROR_TIMEOUT);
            }
            if (stxRet == 4) {
                throw new SDKException(SDKException.COMMUNICATE_ERROR_IO_ERROR);
            }
            if (stxRet == 3) {
                throw new SDKException(SDKException.COMMUNICATE_ERROR_STOP);
            }
        }
        if (STXBuff[0] == 0) {
            System.out.println("stx 首字节读到0");
            stxRet =  read(STXBuff, STXBuff.length, OVERTIME);
            if (stxRet != 0) {
                if (stxRet == 2) {
                    isLogined = false;//K21挂掉时，设置为需要重新登录
                    throw new SDKException(SDKException.COMMUNICATE_ERROR_TIMEOUT);
                }
                if (stxRet == 4) {
                    throw new SDKException(SDKException.COMMUNICATE_ERROR_IO_ERROR);
                }
                if (stxRet == 3) {
                    throw new SDKException(SDKException.COMMUNICATE_ERROR_STOP);
                }
            }
        }

        if (STXBuff[0] == 0x02) {
            byte[] dataLenBuff = new byte[2];
            int dataLenRet = read(dataLenBuff, dataLenBuff.length, 2 * 1000);
            if (dataLenRet != 0) {
                throw new SDKException(SDKException.COMMUNICATE_ERROR_TIMEOUT);
            }
            int dataLen = Integer.valueOf(StringUtil.byte2HexStr(dataLenBuff));
            byte[] dataBuff = new byte[dataLen + 2];
            int dataRet = read(dataBuff, dataBuff.length, 2 * 1000);
            if (dataRet != 0) {
                throw new SDKException(SDKException.COMMUNICATE_ERROR_TIMEOUT);
            }


            if (dataBuff[dataBuff.length - 2] == 0x03) {
                RespResult result = handleProtocol.unPackageProtocol(dataBuff, cmd);
                return result;
            } else {
                throw new SDKException(SDKException.COMMUNICATE_ERROR_ETX_ERROR);
            }
        } else {
            throw new SDKException(SDKException.COMMUNICATE_ERROR_STX_ERROR);
        }
    }


    public int read(byte recvBuf[], int recvLen, long waitTime) throws IOException {
        long lBeginTime = System.currentTimeMillis();// 更新当前秒计数
        long lCurrentTime = 0;
        int nRet = 0;
        int nReadedSize = 0;
        if (mInputStream == null) {
            return 4;
        }
        while (true) {

            if (mInputStream.available() > 0) {
                nRet = mInputStream.read(recvBuf, nReadedSize, (recvLen - nReadedSize));
                System.out.println("read: len="+ nRet+ ", data="+ StringUtil.byte2HexStr(recvBuf));
                if (nRet > 0) {
                    nReadedSize += nRet;
                    if (recvLen == nReadedSize) {
                        return 0;
                    }
                }
            }
            try {
                Thread.sleep(SLEEP_TIME);
                lCurrentTime = System.currentTimeMillis();
                if ((lCurrentTime - lBeginTime) > waitTime) {
                    return 2;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 登录设备 未抛出异常代表登录成功
     */
    private synchronized void login() throws SDKException {
        if (isLogined) {
            return;
        }
        //初始化配置log4j
//        Log4jUtil.configure();

        //先尝试默认波特率通信
        if (testCommunicate()) {
            isLogined = true;
            return;
        }
    }

    /**
     * 测试通信
     *
     * @return
     */
    public boolean testCommunicate() {
        if(mSocket != null) {
            try {
                mSocket.sendUrgentData(0xff);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }
}
